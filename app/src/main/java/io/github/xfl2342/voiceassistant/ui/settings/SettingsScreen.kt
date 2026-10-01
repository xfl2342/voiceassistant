package io.github.xfl2342.voiceassistant.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.xfl2342.voiceassistant.data.SettingsStore
import io.github.xfl2342.voiceassistant.data.ThemeMode
import io.github.xfl2342.voiceassistant.data.TimeInputMode
import io.github.xfl2342.voiceassistant.speech.offline.ModelInstaller
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/**
 * 设置中心。
 *
 * 所有可以调的东西都集中到这里，主界面只保留「用」相关的操作。
 * 目前有 DeepSeek、语音模型、时间选择方式、每周起始日、深色模式、改进意见、提醒设置
 * 与数据备份，后续的设置项也加在这里。
 *
 * DeepSeek 这一项只显示「配没配、用哪个模型」，点了才进 [DeepSeekScreen]：
 * Key 的输入与清除都收在那一页，不会在这一页上露出来。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settingsStore: SettingsStore,
    onBack: () -> Unit,
    onOpenDeepSeek: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onWeekStartChange: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    var timeInputMode by remember { mutableStateOf(settingsStore.timeInputMode) }
    var themeMode by remember { mutableStateOf(settingsStore.themeMode) }
    var weekStartDay by remember { mutableStateOf(settingsStore.weekStartDay) }
    // 这一页不读 Key 明文，只读「配没配」和用哪个模型，用来在入口上显示一行状态。
    val hasApiKey = settingsStore.hasApiKey
    val deepSeekModelName = settingsStore.deepSeekModel
    val context = LocalContext.current
    val installer = remember { ModelInstaller(context) }

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
                text = "设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenDeepSeek() },
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "DeepSeek（AI 解析）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (hasApiKey) {
                        "已配置 · $deepSeekModelName"
                    } else {
                        "未配置。填上 Key 才能把说的话解析成行程"
                    },
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
                    text = "选择时间的方式",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "编辑行程时，点时间按钮弹出的界面。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimeInputMode.entries.forEach { mode ->
                        FilterChip(
                            selected = timeInputMode == mode,
                            onClick = {
                                timeInputMode = mode
                                settingsStore.timeInputMode = mode
                            },
                            label = { Text(mode.label) },
                        )
                    }
                }
                Text(
                    text = timeInputMode.description,
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
                    text = "每周起始日",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).forEach { day ->
                        FilterChip(
                            selected = weekStartDay == day,
                            onClick = {
                                weekStartDay = day
                                settingsStore.weekStartDay = day
                                onWeekStartChange(day)
                            },
                            label = { Text(if (day == DayOfWeek.MONDAY) "周一" else "周日") },
                        )
                    }
                }
                Text(
                    text = "决定日历每一行从哪天开始。国内习惯是周一。",
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
                    text = "深色模式",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = {
                                themeMode = mode
                                settingsStore.themeMode = mode
                                onThemeModeChange(mode)
                            },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
        }

        ModelSection(installer)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenFeedback() },
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "改进意见",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "用着哪里别扭就随口记一句；攒够了连上电脑，一次改掉",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenReminderSettings() },
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "提醒设置",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "精确闹钟、通知权限、后台运行 —— 决定提醒能不能准时弹出",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenBackup() },
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "数据备份",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "把行程、提醒与改进意见导成一份文件，存到下载目录或别处；不含 API Key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

}

/**
 * 语音模型的状态与下载。
 *
 * 模型不进安装包：一是体积（70 多 MB），二是它和代码的更新节奏不同。
 * 下载只需要一次，装好之后识别完全在本地进行。
 */
@Composable
private fun ModelSection(installer: ModelInstaller) {
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(installer.isInstalled()) }
    var installing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var progressText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    // 从手机里选一个模型压缩包安装，不依赖网络。
    val pickModelFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        installing = true
        errorText = null
        scope.launch {
            val result = installer.installFromFile(uri)
            installing = false
            installed = result.isSuccess
            result.exceptionOrNull()?.let {
                errorText = "安装失败：" + (it.message ?: "未知原因")
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "语音模型",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (installed) {
                    "已安装。识别完全在本机进行，不需要联网。"
                } else {
                    "未安装。需要下载一次，约 74 MB；装好之后识别不再联网。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (installing) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = progressText.ifBlank { "正在连接…" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            errorText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (!installed && !installing) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                        installing = true
                        errorText = null
                        progress = 0f
                        var lastUpdateAt = 0L
                        scope.launch {
                            val result = installer.install { downloaded, total ->
                                // 进度回调很密集，限制一下刷新频率，避免界面被刷爆。
                                val now = System.currentTimeMillis()
                                if (now - lastUpdateAt < 200) return@install
                                lastUpdateAt = now
                                progress = if (total > 0) downloaded.toFloat() / total else 0f
                                progressText = buildString {
                                    append("已下载 ")
                                    append(downloaded / 1024 / 1024)
                                    append(" MB")
                                    if (total > 0) {
                                        append(" / ")
                                        append(total / 1024 / 1024)
                                        append(" MB")
                                    }
                                }
                            }
                            installing = false
                            installed = result.isSuccess
                            result.exceptionOrNull()?.let {
                                errorText = "下载失败：" + (it.message ?: "未知原因")
                            }
                        }
                        },
                    ) {
                        Text("下载模型")
                    }
                    OutlinedButton(
                        onClick = { pickModelFile.launch(arrayOf("*/*")) },
                    ) {
                        Text("从文件安装")
                    }
                }
                Text(
                    text = "下载走的是 GitHub，国内网络可能连不上。也可以自己在电脑上" +
                        "下载模型压缩包传到手机，然后点「从文件安装」选择它。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
