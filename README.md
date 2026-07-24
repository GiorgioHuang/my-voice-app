# My Voice App — Voicebox 安卓客户端

用自己的声音（[Voicebox](https://github.com/jamiepine/voicebox) 克隆的音色）在手机上朗读故事和文本。

手机端是纯客户端：录音、文本、播放在手机上，声音合成由局域网里的 Mac（或云端 Docker）上运行的
Voicebox 后端完成。方案调研见 [`docs/android-voice-app-exploration.md`](docs/android-voice-app-exploration.md)。

## 使用前提

1. 一台运行 Voicebox 的 Mac（或 Docker 部署），后端需要监听局域网：
   - 方式 A：手动启动后端并绑定所有网卡
     `voicebox-server --host 0.0.0.0`（默认端口 17493）
   - 方式 B（推荐，无需改动）：Mac 上装 [Tailscale](https://tailscale.com)，
     `tailscale serve 17493` 把本机端口安全地暴露给自己的设备网络，手机装 Tailscale 后即可访问
2. 在桌面版 Voicebox 里创建好音色档案（Voicebox 页 → Create Voice）
3. 手机与 Mac 同一 WiFi（或同一 Tailscale 网络）

## 应用结构

| Tab | 功能 | 对接 API |
|---|---|---|
| 朗读 | 选音色、粘贴文本、生成并播放 | `POST /generate` → 轮询 `GET /history/{id}` → 播放 `GET /audio/{id}` |
| 音色 | 浏览服务器上的音色档案 | `GET /profiles` |
| 连接 | 配置服务器地址、连通性检测 | `GET /health` |

## 构建

```bash
./gradlew :app:assembleDebug
# APK 输出：app/build/outputs/apk/debug/app-debug.apk
```

需要 JDK 17 与 Android SDK（compileSdk 35）。首次构建会自动下载依赖。

## 技术栈

Kotlin · Jetpack Compose (Material 3) · Retrofit + kotlinx.serialization · Media3 ExoPlayer · DataStore

## 路线图

- [x] M1 第一片：连接设置 / 音色列表 / 文本朗读
- [ ] 录音注册音色（手机录 30 秒上传 `POST /profiles` + samples）
- [ ] 故事库（对接 `/stories`）与整章后台生成、进度展示
- [ ] 生成结果本地缓存，离线播放
- [ ] epub/txt 导入、导出 M4B
