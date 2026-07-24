package com.giorgio.voicebox.ui.read

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadScreen(viewModel: ReadViewModel = viewModel()) {
    val profiles by viewModel.profiles.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val text by viewModel.text.collectAsState()
    val state by viewModel.state.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("用我的声音朗读", style = MaterialTheme.typography.headlineSmall)

        ExposedDropdownMenuBox(
            expanded = menuExpanded,
            onExpandedChange = { menuExpanded = it },
        ) {
            OutlinedTextField(
                value = selected?.name ?: "选择音色",
                onValueChange = {},
                readOnly = true,
                label = { Text("音色") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text("${profile.name}（${profile.language}）") },
                        onClick = {
                            viewModel.onSelect(profile)
                            menuExpanded = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = text,
            onValueChange = viewModel::onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
            label = { Text("要朗读的文本") },
            placeholder = { Text("粘贴一段故事…") },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = viewModel::generateAndPlay,
                enabled = state !is ReadState.Generating,
            ) {
                Text(if (state is ReadState.Generating) "生成中…" else "生成并播放")
            }
            OutlinedButton(onClick = viewModel::stopPlayback) {
                Text("停止")
            }
        }

        when (val s = state) {
            is ReadState.Failed -> Text(
                s.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            is ReadState.Playing -> Text(
                "正在播放…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            is ReadState.Generating -> Text(
                "服务器合成中，长文本可能需要一会儿…",
                style = MaterialTheme.typography.bodyMedium,
            )
            ReadState.Idle -> {}
        }
    }
}
