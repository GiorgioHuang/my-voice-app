package com.giorgio.voicebox.ui.profiles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.giorgio.voicebox.data.ApiClient
import com.giorgio.voicebox.data.ServerPrefs
import com.giorgio.voicebox.data.VoiceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ProfilesState {
    data object Loading : ProfilesState
    data class Loaded(val profiles: List<VoiceProfile>) : ProfilesState
    data class Failed(val message: String) : ProfilesState
}

class ProfilesViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<ProfilesState>(ProfilesState.Loading)
    val state: StateFlow<ProfilesState> = _state

    init {
        refresh()
    }

    fun refresh() {
        _state.value = ProfilesState.Loading
        viewModelScope.launch {
            val baseUrl = ServerPrefs.baseUrl(getApplication())
            if (baseUrl.isEmpty()) {
                _state.value = ProfilesState.Failed("尚未配置服务器，请先到「连接」页设置")
                return@launch
            }
            try {
                _state.value = ProfilesState.Loaded(ApiClient.api(baseUrl).profiles())
            } catch (e: Exception) {
                _state.value = ProfilesState.Failed(e.message ?: "加载失败")
            }
        }
    }
}
