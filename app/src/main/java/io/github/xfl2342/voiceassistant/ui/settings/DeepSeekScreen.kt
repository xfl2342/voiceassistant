package io.github.xfl2342.voiceassistant.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.xfl2342.voiceassistant.ai.DeepSeekClient
import io.github.xfl2342.voiceassistant.data.SettingsStore

/**
 * DeepSeek 配置页。
 *
 * Key 的输入、修改、删除都收在这一页，设置页上只留一行状态。
 * 这么做一是免得在设置里随手一翻就看见半截 Key，二是「清除」这种不可逆的操作
 * 放在单独一页里，点错的概率小得多。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeepSeekScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var apiKey by remember { mutableStateOf(settingsStore.deepSeekApiKey.orEmpty()) }
    var deepSeekModel by remember { mutableStateOf(settingsStore.deepSeekModel) }
    var showKey by remember { mutableStateOf(false) }
    /** 「清除 Key」的二次确认：删掉容易，重新填一次要翻半天，所以再问一句。 */
    var confirmClearKey by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text(
                text = "DeepSeek",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            text = if (settingsStore.hasApiKey) {
                "已配置 Key，当前用 ${settingsStore.deepSeekModel} 解析。"
            } else {
                "还没有配置 Key。填上之后才能用语音记行程。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "API Key",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        // 边输边存，免得改完忘了保存。
                        // 但清空输入框不算「清除」——误删一段文字就把 Key 弄丢太容易了，
                        // 真要清除请用下面的「清除 Key」，那里会再问一次。
                        if (it.isNotBlank()) settingsStore.deepSeekApiKey = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (showKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "隐藏" else "显示")
                        }
                    },
                )
                if (apiKey.isBlank() && settingsStore.hasApiKey) {
                    Text(
                        text = "输入框空着不影响已保存的 Key；要真的清除，请点下面的「清除 Key」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "用哪个模型",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(DeepSeekClient.MODEL_CHAT, DeepSeekClient.MODEL_REASONER).forEach { model ->
                        FilterChip(
                            selected = deepSeekModel == model,
                            onClick = {
                                deepSeekModel = model
                                settingsStore.deepSeekModel = model
                            },
                            label = { Text(model) },
                        )
                    }
                }
                Text(
                    text = "chat 快一点，reasoner 想得更细但慢一些。解析日常说法用 chat 就够。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "Key 用系统密钥库加密后存在本机，不会上传到别处；" +
                        "应用直接向 DeepSeek 官方接口发起请求。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "清除",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "清除后 AI 解析就用不了了，需要重新填一次 Key。已经记下的行程不受影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { confirmClearKey = true },
                    enabled = settingsStore.hasApiKey,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("清除 Key")
                }
            }
        }
    }

    if (confirmClearKey) {
        AlertDialog(
            onDismissRequest = { confirmClearKey = false },
            title = { Text("清除 API Key？") },
            text = {
                Text(
                    "清除后 AI 解析就用不了了，需要重新填一次 Key 才能继续用语音记行程。" +
                        "已经记下的行程不受影响。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearKey = false
                        apiKey = ""
                        settingsStore.deepSeekApiKey = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearKey = false }) { Text("取消") }
            },
        )
    }
}
