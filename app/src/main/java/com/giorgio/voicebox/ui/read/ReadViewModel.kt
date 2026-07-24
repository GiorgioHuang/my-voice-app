package com.giorgio.voicebox.ui.read

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.giorgio.voicebox.data.ApiClient
import com.giorgio.voicebox.data.GenerateRequest
import com.giorgio.voicebox.data.ServerPrefs
import com.giorgio.voicebox.data.VoiceProfile
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

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text

    private val _state = MutableStateFlow<ReadState>(ReadState.Idle)
    val state: StateFlow<ReadState> = _state

    private var player: ExoPlayer? = null

    init {
        loadProfiles()
    }

    fun loadProfiles() {
        viewModelScope.launch {
            val baseUrl = ServerPrefs.baseUrl(getApplication())
            if (baseUrl.isEmpty()) return@launch
            try {
                val list = ApiClient.api(baseUrl).profiles()
                _profiles.value = list
                if (_selected.value == null) _selected.value = list.firstOrNull()
            } catch (_: Exception) {
                // Profiles tab surfaces connection errors; keep this tab quiet.
            }
        }
    }

    fun onSelect(profile: VoiceProfile) {
        _selected.value = profile
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
                val request = GenerateRequest(
                    profileId = profile.id,
                    text = content,
                    language = profile.language,
                    engine = profile.effectiveEngine,
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
