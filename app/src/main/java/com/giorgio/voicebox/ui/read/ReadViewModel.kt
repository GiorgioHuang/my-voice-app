package com.giorgio.voicebox.ui.read

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.giorgio.voicebox.data.ApiClient
import com.giorgio.voicebox.data.GenerateRequest
import com.giorgio.voicebox.data.ServerPrefs
import com.giorgio.voicebox.data.TtsModel
import com.giorgio.voicebox.data.VoiceProfile
import com.giorgio.voicebox.data.toTtsModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ReadState {
    data object Idle : ReadState
    data object Generating : ReadState
    data class Playing(val generationId: String) : ReadState
    data class Failed(val message: String) : ReadState
}

class ReadViewModel(app: Application) : AndroidViewModel(app) {

    private val _profiles = MutableStateFlow<List<VoiceProfile>>(emptyList())
    val profiles: StateFlow<List<VoiceProfile>> = _profiles

    private val _selected = MutableStateFlow<VoiceProfile?>(null)
    val selected: StateFlow<VoiceProfile?> = _selected

    private val _models = MutableStateFlow<List<TtsModel>>(emptyList())
    val models: StateFlow<List<TtsModel>> = _models

    private val _selectedModel = MutableStateFlow<TtsModel?>(null)
    val selectedModel: StateFlow<TtsModel?> = _selectedModel

    private val _language = MutableStateFlow("zh")
    val language: StateFlow<String> = _language

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text

    private val _state = MutableStateFlow<ReadState>(ReadState.Idle)
    val state: StateFlow<ReadState> = _state

    private var player: ExoPlayer? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val baseUrl = ServerPrefs.baseUrl(getApplication())
            if (baseUrl.isEmpty()) return@launch
            val api = ApiClient.api(baseUrl)

            try {
                val list = api.profiles()
                _profiles.value = list
                if (_selected.value == null) {
                    list.firstOrNull()?.let { onSelect(it) }
                }
            } catch (_: Exception) {
                // Profiles tab surfaces connection errors; keep this tab quiet.
            }

            try {
                val ttsModels = api.modelStatus().models.mapNotNull { it.toTtsModel() }
                _models.value = ttsModels
                if (_selectedModel.value == null) {
                    _selectedModel.value = pickDefaultModel(ttsModels)
                }
            } catch (_: Exception) {
                // Without model info we fall back to the profile's engine.
            }
        }
    }

    /**
     * Prefer the user's last choice, then a downloaded model matching the
     * profile's engine, then any downloaded model — never a model that
     * would trigger a surprise multi-GB download on the server.
     */
    private suspend fun pickDefaultModel(models: List<TtsModel>): TtsModel? {
        if (models.isEmpty()) return null
        val lastName = ServerPrefs.lastModel(getApplication())
        models.firstOrNull { it.modelName == lastName }?.let { return it }

        val profileEngine = _selected.value?.effectiveEngine
        models.firstOrNull { it.downloaded && profileEngine != null && it.engine == profileEngine }
            ?.let { return it }
        models.firstOrNull { it.loaded }?.let { return it }
        return models.firstOrNull { it.downloaded }
    }

    fun onSelect(profile: VoiceProfile) {
        _selected.value = profile
        _language.value = profile.language
        // Keep model aligned with the profile's engine when possible.
        val current = _selectedModel.value
        val engine = profile.effectiveEngine
        if (engine != null && current != null && current.engine != engine) {
            _models.value
                .firstOrNull { it.engine == engine && it.downloaded }
                ?.let { _selectedModel.value = it }
        }
    }

    fun onSelectModel(model: TtsModel) {
        _selectedModel.value = model
        viewModelScope.launch {
            ServerPrefs.saveLastModel(getApplication(), model.modelName)
        }
    }

    fun onLanguageChange(code: String) {
        _language.value = code
    }

    fun onTextChange(value: String) {
        _text.value = value
    }

    fun generateAndPlay() {
        val profile = _selected.value
        val content = _text.value.trim()
        if (profile == null) {
            _state.value = ReadState.Failed("请先选择一个音色")
            return
        }
        if (content.isEmpty()) {
            _state.value = ReadState.Failed("请输入要朗读的文本")
            return
        }

        _state.value = ReadState.Generating
        viewModelScope.launch {
            val baseUrl = ServerPrefs.baseUrl(getApplication())
            if (baseUrl.isEmpty()) {
                _state.value = ReadState.Failed("尚未配置服务器，请先到「连接」页设置")
                return@launch
            }
            try {
                val api = ApiClient.api(baseUrl)
                val model = _selectedModel.value
                val request = GenerateRequest(
                    profileId = profile.id,
                    text = content,
                    language = _language.value,
                    engine = model?.engine ?: profile.effectiveEngine,
                    modelSize = model?.modelSize,
                )
                var generation = api.generate(request)

                // /generate may return before synthesis finishes; poll until done.
                val deadline = System.currentTimeMillis() + 10 * 60 * 1000
                while (generation.status !in listOf("completed", "failed")) {
                    if (System.currentTimeMillis() > deadline) {
                        _state.value = ReadState.Failed("生成超时")
                        return@launch
                    }
                    delay(1500)
                    generation = api.generation(generation.id)
                }

                if (generation.status == "failed") {
                    _state.value = ReadState.Failed(generation.error ?: "生成失败")
                    return@launch
                }

                play(ApiClient.audioUrl(baseUrl, generation.id))
                _state.value = ReadState.Playing(generation.id)
            } catch (e: Exception) {
                _state.value = ReadState.Failed(e.message ?: "生成失败")
            }
        }
    }

    fun stopPlayback() {
        player?.stop()
        if (_state.value is ReadState.Playing) _state.value = ReadState.Idle
    }

    private fun play(url: String) {
        val p = player ?: ExoPlayer.Builder(getApplication()).build().also { player = it }
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.play()
    }

    override fun onCleared() {
        player?.release()
        player = null
    }
}
