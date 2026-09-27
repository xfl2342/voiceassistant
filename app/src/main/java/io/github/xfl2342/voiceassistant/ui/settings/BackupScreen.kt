package io.github.xfl2342.voiceassistant.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.xfl2342.voiceassistant.data.BackupCounts
import io.github.xfl2342.voiceassistant.data.ImportPlan
import io.github.xfl2342.voiceassistant.data.ImportSummary
import io.github.xfl2342.voiceassistant.domain.BackupService
import kotlinx.coroutines.launch

/**
 * 数据备份。
 *
 * 这一页做两件事：把库里的东西导成一份文件，和把那样一份文件读回来。留给「换手机」
 * 与「手滑删了」两种时候用。
 *
 * 顺序上刻意先说清楚要动什么、存到哪，再给按钮：反过来的话，用户按下按钮时心里是没底的。
 * 恢复更是如此——它是这个应用里唯一会成批写数据的操作，所以要先摆在明面上问一句。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupScreen(
    service: BackupService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    /** null 表示还在数；条数不多，数一下也就一瞬间。 */
    var counts by remember { mutableStateOf<BackupCounts?>(null) }
    var busy by remember { mutableStateOf(false) }
    var exportHint by remember { mutableStateOf<String?>(null) }
    var restoreHint by remember { mutableStateOf<String?>(null) }
    /** 已经读好、等着用户点头的那份备份。 */
    var pending by remember { mutableStateOf<ImportPlan?>(null) }

    // 「另存为」与「选备份文件」都交给系统的文件选择框：位置和文件由用户自己定，
    // 应用不去猜路径，也就不用申请存储权限。
    val saveAs = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = service.exportTo(uri)
            busy = false
            exportHint = if (ok) {
                "已写出你选的那份文件。"
            } else {
                "写入失败，换个位置再试一次看看。"
            }
        }
    }

    val pickBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        restoreHint = null
        scope.launch {
            // 读文件与解析都可能在坏文件上失败，失败的那句话由 BackupFormatException 带着，
            // 直接摆出来给用户看。
            val plan = runCatching { service.prepareImport(uri) }
                .getOrElse { error ->
                    restoreHint = error.message ?: "这个文件读不动，先看看是不是选错了。"
                    null
                }
            busy = false
            pending = plan
        }
    }

    LaunchedEffect(Unit) { counts = service.counts() }

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text(
                text = "数据备份",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "要备份什么",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = counts?.let { describeCounts(it) } ?: "正在数…",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "备份的是一份 JSON 文本，换手机或者误删之后照着它能找回来。" +
                        "里面不含 API Key，也不含深色模式、时间方式这些设置；" +
                        "已经删掉的行程不会进去。",
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
                    text = "导出",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "「导出到下载目录」最省事：手机连上电脑，在" +
                        "「此电脑 → 手机 → 内部存储 → Download」里就能看到。" +
                        "想直接放进网盘或 U 盘，用「另存为」自己挑位置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                val result = service.exportToDownloads()
                                busy = false
                                exportHint = if (result != null) {
                                    "已写出：下载/${result.fileName}（行程 ${result.counts.events} 条）"
                                } else {
                                    "写入失败。行程都还在应用里，什么都没丢，稍后再试一次看看。"
                                }
                            }
                        },
                        enabled = !busy && counts?.isEmpty == false,
                    ) {
                        Text(if (busy) "正在处理…" else "导出到下载目录")
                    }
                    OutlinedButton(
                        onClick = { saveAs.launch(service.suggestedFileName()) },
                        enabled = !busy && counts?.isEmpty == false,
                    ) {
                        Text("另存为…")
                    }
                }
                exportHint?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
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
                    text = "恢复",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "挑一份以前导出的备份（那个 .json 文件），把里面的东西读回来。" +
                        "恢复只增不减：手机上没有的加进来，之前删掉的找回来；" +
                        "手机上已有的同一条原地不动，不会被备份里的旧版本盖掉。" +
                        "恢复完会把提醒重新排一遍。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { pickBackup.launch(arrayOf("*/*")) },
                    enabled = !busy,
                ) {
                    Text(if (busy) "正在处理…" else "选择备份文件…")
                }
                restoreHint?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Text(
            text = if (counts?.isEmpty == true) {
                "还没有可以备份的数据。先记几条行程，或者到「改进意见」里写一句，再回来导出。"
            } else {
                "每次导出都是新的一份文件，文件名带时间，不会覆盖上一次的备份。" +
                    "备份文件留在手机上，应用不会自动传走，也不会自动清理。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    pending?.let { plan ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (plan.summary.changedAny) "从这份备份恢复？" else "这份备份不用恢复") },
            text = { Text(describePlan(plan)) },
            confirmButton = {
                if (plan.summary.changedAny) {
                    TextButton(
                        onClick = {
                            pending = null
                            busy = true
                            scope.launch {
                                val result = runCatching { service.restore(plan) }.getOrNull()
                                busy = false
                                restoreHint = if (result != null) {
                                    counts = service.counts()
                                    describeResult(result)
                                } else {
                                    "恢复失败。手机上什么都没变，等会儿再试一次看看。"
                                }
                            }
                        },
                    ) {
                        Text("恢复")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text(if (plan.summary.changedAny) "取消" else "知道了")
                }
            },
        )
    }
}

/**
 * 条数说明。
 *
 * 重复规则与提醒是挂在行程上的，但条数并不相等（一条行程可以有好几条提醒），
 * 所以用括号跟在行程后面，别写成「其中」——那会让人以为它们包含在行程条数里。
 */
private fun describeCounts(counts: BackupCounts): String =
    "行程 ${counts.events} 条（带重复规则 ${counts.rules} 条、提醒 ${counts.reminders} 条）；" +
        "改进意见 ${counts.feedback} 条。"

/** 动手之前把账目摆清楚：这份备份是哪时候的、会动到什么、不会动什么。 */
private fun describePlan(plan: ImportPlan): String {
    val summary = plan.summary
    return buildString {
        plan.exportedAtText?.let { append("这份备份是 $it 导出的。\n\n") }

        if (!summary.changedAny) {
            append("里面的东西手机上都有了，没有需要恢复的。")
            return@buildString
        }

        append("恢复会这样处理：\n")
        append("· 行程：新增 ${summary.newEvents} 条")
        if (summary.restoredEvents > 0) {
            append("，找回之前删掉的 ${summary.restoredEvents} 条")
        }
        if (summary.skippedEvents > 0) {
            append("，跳过 ${summary.skippedEvents} 条（手机上已有）")
        }
        append('\n')
        append("· 改进意见：新增 ${summary.newFeedback} 条")
        if (summary.skippedFeedback > 0) {
            append("，跳过 ${summary.skippedFeedback} 条")
        }
        append("\n\n")
        append("手机上已有的行程不会被覆盖，也不会删掉任何东西。")
    }
}

/** 恢复完的结果。跳过的条数也要说，不然用户会以为「怎么没全进来」。 */
private fun describeResult(summary: ImportSummary): String {
    if (!summary.changedAny) return "这份备份里的东西手机上都有了，没有需要恢复的。"

    return buildString {
        append("恢复完成：行程新增 ${summary.newEvents} 条")
        if (summary.restoredEvents > 0) append("，找回 ${summary.restoredEvents} 条")
        if (summary.skippedEvents > 0) append("，跳过 ${summary.skippedEvents} 条")
        if (summary.newFeedback > 0) append("；改进意见新增 ${summary.newFeedback} 条")
        append("。提醒已重新排好。")
    }
}
