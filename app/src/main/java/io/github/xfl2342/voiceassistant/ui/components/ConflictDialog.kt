package io.github.xfl2342.voiceassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.xfl2342.voiceassistant.domain.ConflictReport

/**
 * 保存前的冲突提示：把撞上的行程列出来，让用户自己决定。
 *
 * 用「仍然保存 / 返回修改」两个按钮，而不是默认禁止保存：
 * 同一天两个会同时存在是常事，应用不该替用户做决定。
 */
@Composable
fun ConflictDialog(
    report: ConflictReport,
    onSaveAnyway: () -> Unit,
    onBack: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("这段时间已经有别的行程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (report.totalCount > report.conflicts.size) {
                        "共找到 ${report.totalCount} 处时间重叠，先列出最早的 ${report.conflicts.size} 处："
                    } else {
                        "和下面 ${report.conflicts.size} 条行程的时间重叠了："
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                report.conflicts.forEach { conflict ->
                    Text(
                        text = EventFormat.conflictDescription(conflict),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "仍然保存的话，两条行程都会留在日历里，提醒也会照常响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSaveAnyway) { Text("仍然保存") }
        },
        dismissButton = {
            TextButton(onClick = onBack) { Text("返回修改") }
        },
    )
}
