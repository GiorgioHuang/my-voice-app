# 安卓端"我的声音"应用 — 实现方案探讨

> 目标：做一个安卓手机端应用，用**用户自己的声音**生成内容（读故事、读文章等）。
> 桌面版（macOS, v0.5.0）的 **Voicebox** 页已跑通完整闭环：创建/导入 voice profile → 选模型（Qwen3-TTS 1.7B 等）→ 生成语音。本文探讨如何把这套体验落到安卓端。

---

## 2026-07 更新：两条端侧路线 A/B

最新调研（2026-07）带来两个关键变化：

1. **Qwen3-TTS 有了 GGUF/llama.cpp 推理路径**（HaujetZhao/Qwen3-TTS-GGUF、qwen3-tts.cpp、Serveurperso 现成 GGUF 权重）：LLM 部分走 llama.cpp（CPU/Vulkan/Metal），tokenizer/vocoder 走 ONNX Runtime，支持流式合成（延迟可至 ~300ms，12.5Hz tokenizer）。llama.cpp 在安卓上成熟（CPU + Adreno Vulkan），**Qwen3-TTS 0.6B 量化版上手机值得实测**——但尚无公开安卓 RTF 数据。
2. **sherpa-onnx 克隆模型扩充**：除 ZipVoice 外，PocketTTS（flow-matching，更轻）也支持参考音频克隆；TTS 家族达 7 个（VITS/Matcha/Kokoro/Kitten/ZipVoice/PocketTTS/Supertonic）。

**架构含义：Voicebox 音色档案可以两端通用。** voice profile 本质是"参考音频 + 参考文本 + 元数据"。若安卓端也跑 Qwen3-TTS（0.6B 量化），同一 profile 在 Mac 和手机上还原出**同一个声音**，导出/导入或云同步即可打通两端；用 ZipVoice 则音色会有模型间差异。

| M0 实测对比 | 路线 1：sherpa-onnx + ZipVoice/PocketTTS | 路线 2：llama.cpp + Qwen3-TTS 0.6B GGUF |
|---|---|---|
| 安卓集成成熟度 | ✅ 官方 AAR，端到端已验证 | ⚠️ 自行组装（llama.cpp JNI + ONNX vocoder） |
| 与桌面版音色一致 | ❌ 不同模型 | ✅ 同家族，profile 两端通用 |
| 速度 | ✅ 已知 RTF≈1（旗舰 CPU） | ❓ 需实测（q4 量化 + Vulkan） |
| 流式 | 批式为主 | ✅ 原生流式，~300ms 延迟 |

**决策规则**：路线 2 在目标机型上 RTF ≤ 2（配合整章后台预生成）→ 首选路线 2，两端一个生态；否则退回路线 1，profile 内保留参考音频，接受音色差异。

---

## 1. 模型选型说明

> 注：桌面版的 "Voicebox" 是应用内的音色管理功能，与 Meta 的 Voicebox 模型无关。后者只发了论文、权重从未开源，不在选型范围内。

可用的"用自己声音"（零样本克隆）开源方案，与桌面版生态一致：

| 模型 | 克隆能力 | 体积 | 端侧可行性 |
|---|---|---|---|
| **ZipVoice (distill, int8)** | ✅ 零样本克隆 | ~几百 MB | ✅ **sherpa-onnx 已官方支持安卓端到端**，Pixel 10 Pro CPU 实测 RTF≈1.0 |
| **Qwen3-TTS 0.6B Base** | ✅ 3 秒音频克隆 | 2.5 GB（GGUF q4 后约 1 GB 内） | ⚠️ llama.cpp GGUF 路径已出现（见顶部 2026-07 更新），旗舰机值得实测 |
| **Qwen3-TTS 1.7B / CustomVoice** | ✅（1.7B）/ 预置音色（CustomVoice） | 4.5 GB / 较小 | ❌ 服务端为主 |
| **Chatterbox TTS** | ✅ 参考音频克隆 | ~1 GB | ⚠️ 无成熟安卓移植，服务端为主 |
| **Kokoro 82M** | ❌ 只有预置音色 | ~300 MB | ✅ 端侧很快，可做兜底/预览 |

**结论**：忘掉 VoiceBox；端侧克隆用 **ZipVoice（sherpa-onnx）**，高质量克隆放服务端用 **Qwen3-TTS / Chatterbox**。

---

## 2. 三种架构方案对比

### 方案 A：纯端侧（All on-device）
手机本地跑克隆 TTS（sherpa-onnx + ZipVoice int8）。

- ✅ 完全离线、隐私最好、零服务器成本
- ✅ 2026 年已真实可行：sherpa-onnx 的安卓 TTS Engine 应用已跑通 ZipVoice 克隆
- ⚠️ RTF≈1.0 意味着"生成 1 分钟音频要 1 分钟"（旗舰机 CPU），中端机更慢
- ⚠️ 音质/韵律不如 0.6B+ 的大模型；长故事要靠**后台预生成**掩盖延迟

### 方案 B：瘦客户端 + 自建服务端
安卓只做 UI，推理放 GPU 服务器（FastAPI + Qwen3-TTS 1.7B / Chatterbox），本质上是把桌面版的推理栈搬上服务器。

- ✅ 音质上限最高、手机不发热不占存储、迭代最快（复用桌面版模型管线）
- ❌ 需要 GPU 服务器（成本）、要联网、用户声音样本上传涉及隐私

### 方案 C：混合（推荐）
- **端侧**：sherpa-onnx 集成 ZipVoice（克隆）+ Kokoro（快速预览/兜底），离线可用
- **服务端（可选开关）**：接桌面版同款 Qwen3-TTS，追求音质时用；也可以让 **Mac 桌面版开一个局域网 HTTP 服务**，手机在同一 WiFi 下直接调 Mac 的算力——零服务器成本，隐私不出家门
- 读故事场景天然适合**离线批量预生成**：睡前点"生成整章"，后台合成完缓存成音频文件，播放时零延迟

---

## 3. 推荐技术栈（安卓端）

| 层 | 选型 |
|---|---|
| 语言/UI | Kotlin + Jetpack Compose |
| 推理引擎 | **sherpa-onnx**（官方 Android AAR，支持 ZipVoice/Kokoro/Matcha/VITS，附带 Whisper ASR 和 VAD） |
| 播放 | Media3 (ExoPlayer) + MediaSession（锁屏/耳机控制，读故事必备） |
| 长任务 | Foreground Service + WorkManager（整章后台合成） |
| 数据 | Room（故事库、章节、生成缓存索引） |
| 录音注册 | AudioRecord 16kHz/单声道 + sherpa-onnx VAD 做静音裁剪 |
| 模型分发 | 首启从 HuggingFace/自有 CDN 下载到应用私有目录（对齐桌面版的 Models 页设计） |

---

## 4. 核心流程设计

### 4.1 声音注册（一次性 Enrollment）
1. 引导用户朗读 1~3 句固定文本（10~30 秒干净录音）
2. VAD 裁剪静音、响度归一化；用 Whisper（sherpa-onnx 同库）转写得到参考文本
3. 保存 `(参考音频, 参考文本)` 作为"我的音色"——ZipVoice 零样本克隆只需要这一对
4. 支持多音色档案（爸爸的声音、妈妈的声音…讲睡前故事的真实需求）

### 4.2 读故事管线（长文本）
```
导入文本(粘贴/txt/epub) → 分章 → 按句切分（中文按标点，控制每段 <200 字）
→ 逐句合成 → 句间拼接（交叉淡入淡出 30ms）→ 写入章节缓存 WAV/M4A
→ ExoPlayer 播放列表播放，边生成边播（生成领先播放即可，RTF≈1 刚好够）
```
- 后台整章预生成 + 缓存后，二次播放零延迟
- 可导出 M4A/M4B 当有声书

### 4.3 内容来源（"产生内容"）
- MVP：粘贴文本、导入 txt
- 进阶：epub 解析、网页正文抽取、接 LLM 生成故事（"给 5 岁孩子编一个恐龙故事"→ 用自己的声音读出来）

---

## 5. 里程碑建议

| 阶段 | 内容 | 验证目标 |
|---|---|---|
| M0 PoC（1~2 周） | 两条路线各搭 demo：① sherpa-onnx + ZipVoice/PocketTTS；② llama.cpp + Qwen3-TTS 0.6B GGUF。用同一段 20 秒录音 A/B 对比 | 克隆相似度、中文效果、RTF、发热；按"决策规则"定路线 |
| M1 MVP | 注册流程 + 粘贴文本朗读 + 后台整章生成 + 基本播放器 | 完整"用我的声音读故事"闭环 |
| M2 | 故事库/epub、多音色档案、导出音频、Kokoro 快速预览 | 日常可用 |
| M3 | 可选服务端（Mac 局域网模式或云端 Qwen3-TTS）提升音质 | 音质对齐桌面版 |

---

## 6. 风险与注意事项

- **性能分层**：中端机 RTF 可能到 2~3，必须依赖预生成而非实时流式；UI 上要把"生成中"做成后台任务而不是转圈等待
- **合规**：克隆仅限用户本人/经同意的声音；注册流程加明确同意声明；生成音频可考虑加水印（Chatterbox 自带 Perth 水印可参考）
- **中文效果**：ZipVoice 中文克隆效果需要 M0 阶段实测；若不达标，退到"端侧 Kokoro 中文预置音色 + 服务端克隆"的组合
- **模型下载体验**：几百 MB~GB 级下载需要断点续传、仅 WiFi 下载选项（桌面版 Models 页的交互可以直接复刻）

---

## 7. 下一步

1. 确认路线：建议 **方案 C（端侧 ZipVoice 为主，服务端可选）**
2. M0 PoC：拉 sherpa-onnx 官方 Android 示例，换上 ZipVoice distill int8 模型实测中文克隆效果
3. 定安卓工程骨架（Compose 单模块起步），在本仓库初始化项目

### 参考链接
- sherpa-onnx TTS 文档：https://k2-fsa.github.io/sherpa/onnx/index.html
- sherpa-onnx TTS 模型家族总览：https://deepwiki.com/k2-fsa/sherpa-onnx/3.2-text-to-speech-(tts)
- ZipVoice 安卓克隆支持（issue #3439）：https://github.com/k2-fsa/sherpa-onnx/issues/3439
- Qwen3-TTS 开源仓库：https://github.com/QwenLM/Qwen3-TTS
- Qwen3-TTS GGUF/llama.cpp 推理：https://github.com/HaujetZhao/Qwen3-TTS-GGUF
- qwen3-tts.cpp（组件级移植 + GGUF 转换脚本）：https://github.com/predict-woo/qwen3-tts.cpp
- 现成 GGUF 权重：https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF
