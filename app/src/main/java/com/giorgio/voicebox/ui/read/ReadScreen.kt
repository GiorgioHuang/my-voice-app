package com.giorgio.voicebox.ui.read

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.giorgio.voicebox.data.SUPPORTED_LANGUAGES
import com.giorgio.voicebox.data.TtsModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadScreen(viewModel: ReadViewModel = viewModel()) {
    val profiles by viewModel.profiles.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val models by viewModel.models.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val language by viewModel.language.collectAsState()
    val text by viewModel.text.collectAsState()
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("用我的声音朗读", style = MaterialTheme.typography.headlineSmall)

        DropdownField(
            label = "音色",
            value = selected?.name ?: "选择音色",
            options = profiles.map { "${it.name}（${it.language}）" },
            onSelect = { index -> viewModel.onSelect(profiles[index]) },
        )

        DropdownField(
            label = "模型",
            value = selectedModel?.let { modelLabel(it) } ?: "跟随音色默认引擎",
            options = models.map { modelLabel(it) },
            onSelect = { index -> viewModel.onSelectModel(models[index]) },
        )

        if (selectedModel?.downloaded == false) {
            Text(
                "⚠️ 该模型在服务器上尚未下载，生成会先触发下载（可能有几个 GB）",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        DropdownField(
            label = "语言",
            value = SUPPORTED_LANGUAGES.firstOrNull { it.first == language }
                ?.let { "${it.second}（${it.first}）" } ?: language,
            options = SUPPORTED_LANGUAGES.map { "${it.second}（${it.first}）" },
            onSelect = { index -> viewModel.onLanguageChange(SUPPORTED_LANGUAGES[index].first) },
        )

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

private fun modelLabel(model: TtsModel): String {
    val status = when {
        model.loaded -> "已加载"
        model.downloaded -> "已下载"
        else -> "未下载"
    }
    return "${model.displayName} · $status"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}
