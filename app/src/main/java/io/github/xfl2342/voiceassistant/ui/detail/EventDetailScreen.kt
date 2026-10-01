package io.github.xfl2342.voiceassistant.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.xfl2342.voiceassistant.data.EventDetail
import io.github.xfl2342.voiceassistant.domain.EventService
import io.github.xfl2342.voiceassistant.ui.components.EventColors
import io.github.xfl2342.voiceassistant.ui.components.EventFormat
import io.github.xfl2342.voiceassistant.ui.components.InfoRow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 行程详情页。
 *
 * 从日历上点进来，能看完整信息，也能改或删。重复行程点开的是整条规则，
 * 界面上会明确说明「修改会影响所有重复的日程」。
 */
@Composable
fun EventDetailScreen(
    service: EventService,
    eventId: String,
    /** 从日历上某一天点进来时带上那一天；重复行程靠它提供「只删这一次」。 */
    occurrenceDate: LocalDate? = null,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<EventDetail?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        detail = service.load(eventId)
    }

    fun deleteWhole() {
        confirmDelete = false
        deleting = true
        scope.launch {
            service.delete(eventId)
            deleting = false
            onDeleted()
        }
    }

    fun skipThisOne(date: LocalDate) {
        confirmDelete = false
        deleting = true
        scope.launch {
            service.skipOccurrence(eventId, date)
            deleting = false
            onDeleted()
        }
    }

    BackHandler(onBack = onBack)

    val current = detail
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
                text = "行程详情",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (current == null) {
            Text(
                text = "这条行程已经不在了（可能刚被删除）。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = current.event.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    // 待办做完之后划掉标题：和列表里保持一致的说法。
                    textDecoration = if (current.event.done) TextDecoration.LineThrough else null,
                )
                if (current.event.isTodo) {
                    InfoRow(
                        label = "类型",
                        value = if (current.event.done) "待办（已完成）" else "待办（不设时间）",
                    )
                } else {
                    InfoRow("时间", EventFormat.timeDescription(current.event))
                }
                InfoRow("急迫程度", EventColors.label(current.event.urgency))
                EventFormat.recurrenceDescription(current.rule)?.let {
                    InfoRow("重复", it)
                }
                current.event.location?.takeIf { it.isNotBlank() }?.let {
                    InfoRow("地点", it)
                }
                if (current.reminders.isEmpty()) {
                    InfoRow("提醒", "不提醒")
                } else {
                    val times = current.reminders.mapNotNull { it.minutesBefore }.joinToString("、") {
                        EventFormat.reminderDescription(it)
                    }
                    InfoRow(
                        label = "提醒",
                        value = if (current.rule != null) {
                            // 重复行程会预注册未来多次发生，这里把次数说清楚，
                            // 免得看到「提醒 15 分钟」却以为是只提醒一次。
                            "$times（已为未来 ${current.reminders.size} 次发生注册）"
                        } else {
                            times
                        },
                    )
                }
            }
        }

        current.event.rawText?.takeIf { it.isNotBlank() }?.let { raw ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "当时说的话",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(text = raw, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (current.rule != null) {
            Text(
                text = if (occurrenceDate != null) {
                    "这是重复行程，修改会影响到所有重复的日程；" +
                        "删除时可以只跳过 ${EventFormat.fullDate(occurrenceDate)} 这一次。"
                } else {
                    "这是重复行程，修改会影响到所有重复的日程。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (current.event.isTodo) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            service.setTodoDone(eventId, !current.event.done)
                            // 重新读一次，让标记与标题立刻对上。
                            detail = service.load(eventId)
                        }
                    },
                ) {
                    Text(if (current.event.done) "撤销完成" else "标记完成")
                }
            }
            Button(onClick = onEdit) { Text("编辑") }
            Button(
                onClick = { confirmDelete = true },
                enabled = !deleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(if (deleting) "删除中…" else "删除")
            }
        }
    }

    if (confirmDelete) {
        // 从日历上某一天点进来的重复行程，才能问「只删这一次还是整条」。
        val skipDate = occurrenceDate?.takeIf { current?.rule != null }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (skipDate == null) "删除这条行程？" else "删哪一次？") },
            text = {
                if (skipDate == null) {
                    Text("删除后这条行程的提醒也会一并取消，无法从应用里恢复。")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "「只删这一次」把 ${EventFormat.fullDate(skipDate)} 这次跳过，" +
                                "往后各次照常提醒；「删除整条」则把这条重复行程整个去掉。",
                        )
                        Button(
                            onClick = { skipThisOne(skipDate) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !deleting,
                        ) {
                            Text("只删这一次（${EventFormat.shortDate(skipDate)}）")
                        }
                        OutlinedButton(
                            onClick = { deleteWhole() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !deleting,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("删除整条重复行程")
                        }
                    }
                }
            },
            confirmButton = {
                if (skipDate == null) {
                    TextButton(onClick = { deleteWhole() }, enabled = !deleting) {
                        Text("删除")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}
