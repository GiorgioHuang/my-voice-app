package com.giorgio.voicebox.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ConnectScreen(viewModel: ConnectViewModel = viewModel()) {
    val url by viewModel.url.collectAsState()
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("连接 Voicebox 服务器", style = MaterialTheme.typography.headlineSmall)
        Text(
            "在 Mac 上以 --host 0.0.0.0 启动 Voicebox 后端（或使用 Tailscale），" +
                "然后输入它的地址。",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = url,
            onValueChange = viewModel::onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("服务器地址") },
            placeholder = { Text("192.168.1.10:17493") },
            singleLine = true,
        )

        Button(
            onClick = viewModel::testAndSave,
            enabled = state !is ConnectState.Testing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state is ConnectState.Testing) "连接中…" else "测试并保存")
        }

        when (val s = state) {
            is ConnectState.Testing -> CircularProgressIndicator(Modifier.size(28.dp))
            is ConnectState.Connected -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("✓ 已连接", style = MaterialTheme.typography.titleMedium)
                    Text("推理后端：${s.health.backendType ?: "未知"}")
                    Text("GPU：${s.health.gpuType ?: "无"}")
                    Text("模型已加载：${if (s.health.modelLoaded) "是（${s.health.modelSize ?: ""}）" else "否（首次生成时加载）"}")
                }
            }
            is ConnectState.Failed -> Text(
                s.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            ConnectState.Idle -> {}
        }
    }
}
