package com.giorgio.voicebox.ui.connect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.giorgio.voicebox.data.ApiClient
import com.giorgio.voicebox.data.HealthResponse
import com.giorgio.voicebox.data.ServerPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ConnectState {
    data object Idle : ConnectState
    data object Testing : ConnectState
    data class Connected(val health: HealthResponse) : ConnectState
    data class Failed(val message: String) : ConnectState
}

class ConnectViewModel(app: Application) : AndroidViewModel(app) {

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url

    private val _state = MutableStateFlow<ConnectState>(ConnectState.Idle)
    val state: StateFlow<ConnectState> = _state

    init {
        viewModelScope.launch {
            _url.value = ServerPrefs.baseUrl(getApplication())
        }
    }

    fun onUrlChange(value: String) {
        _url.value = value
        _state.value = ConnectState.Idle
    }

    fun testAndSave() {
        val target = _url.value.trim()
        if (target.isEmpty()) {
            _state.value = ConnectState.Failed("请输入服务器地址，例如 192.168.1.10:17493")
            return
        }
        _state.value = ConnectState.Testing
        viewModelScope.launch {
            try {
                val health = ApiClient.api(target).health()
                ServerPrefs.saveBaseUrl(getApplication(), target)
                _state.value = ConnectState.Connected(health)
            } catch (e: Exception) {
                _state.value = ConnectState.Failed(e.message ?: "连接失败")
            }
        }
    }
}
