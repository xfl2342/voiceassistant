package io.github.xfl2342.voiceassistant.ui.settings

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.xfl2342.voiceassistant.data.FeedbackExporter
import io.github.xfl2342.voiceassistant.data.FeedbackRepository
import io.github.xfl2342.voiceassistant.data.db.FeedbackEntity
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 改进意见。
 *
 * 定位是「随口一句的收集箱」：说不上是需求，也够不上记进日程，就先扔在这里，
 * 等下次连电脑时一起读走、一起改。所以这一页只有两件事——写，和处理。
 */
@Composable
fun FeedbackScreen(
    repository: FeedbackRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val items by repository.observeAll().collectAsState(initial = emptyList())
    var draft by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<FeedbackEntity?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

    val pending = items.filterNot { it.done }
    val done = items.filter { it.done }

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
                text = "改进意见",
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
                    text = "记一条",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("想到什么写一句就行，比如「日历上想看到下周的天气」")
                    },
                    minLines = 3,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                if (repository.add(draft)) {
                                    draft = ""
                                    hint = "已记下，并写了一份到手机的下载目录。"
                                }
                            }
                        },
                        enabled = draft.isNotBlank(),
                    ) {
                        Text("记录")
                    }
                    if (draft.isNotBlank()) {
                        TextButton(onClick = { draft = "" }) { Text("清空") }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "怎么传到电脑",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "每记一条（包括改动和删除），应用都会把全部意见重新写一份到" +
                        "手机的「下载」目录，文件名是「${FeedbackExporter.FILE_NAME}」。下次用数据线连上电脑时，" +
                        "在「此电脑 → 手机 → 内部存储 → Download」里就能看到它，也可以让" +
                        "电脑上的 Codex 直接读过去开工。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        exporting = true
                        scope.launch {
                            val name = repository.exportToDownloads()
                            exporting = false
                            hint = if (name != null) {
                                "已写出：下载/$name"
                            } else {
                                "写入失败。意见不会丢，都还在应用里，稍后再试一次看看。"
                            }
                        }
                    },
                    enabled = !exporting && items.isNotEmpty(),
                ) {
                    Text(if (exporting) "正在写…" else "立刻重写一份到下载目录")
                }
                hint?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (items.isEmpty()) {
            Text(
                text = "还没记过什么。用着用着觉得哪里别扭，回到这里写一句就行。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (pending.isNotEmpty()) {
            SectionTitle("待处理（${pending.size}）")
            pending.forEach { item ->
                FeedbackItem(
                    item = item,
                    editing = editingId == item.id,
                    editingText = editingText,
                    onEditingTextChange = { editingText = it },
                    onStartEdit = {
                        editingId = item.id
                        editingText = item.content
                    },
                    onCancelEdit = { editingId = null },
                    onSaveEdit = {
                        scope.launch { repository.update(item.id, editingText) }
                        editingId = null
                    },
                    onToggleDone = { scope.launch { repository.setDone(item.id, true) } },
                    onDelete = { pendingDelete = item },
                )
            }
        }

        if (done.isNotEmpty()) {
            SectionTitle("已完成（${done.size}）")
            done.forEach { item ->
                FeedbackItem(
                    item = item,
                    editing = editingId == item.id,
                    editingText = editingText,
                    onEditingTextChange = { editingText = it },
                    onStartEdit = {
                        editingId = item.id
                        editingText = item.content
                    },
                    onCancelEdit = { editingId = null },
                    onSaveEdit = {
                        scope.launch { repository.update(item.id, editingText) }
                        editingId = null
                    },
                    onToggleDone = { scope.launch { repository.setDone(item.id, false) } },
                    onDelete = { pendingDelete = item },
                )
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删掉这一条？") },
            text = { Text(target.content) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.delete(target.id) }
                        pendingDelete = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun FeedbackItem(
    item: FeedbackEntity,
    editing: Boolean,
    editingText: String,
    onEditingTextChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (editing) {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = onEditingTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSaveEdit, enabled = editingText.isNotBlank()) {
                        Text("保存")
                    }
                    TextButton(onClick = onCancelEdit) { Text("取消") }
                }
                return@Column
            }

            Text(
                text = item.content,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTime(item.createdAt),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onToggleDone) {
                    Text(if (item.done) "撤销完成" else "完成")
                }
                TextButton(onClick = onStartEdit) { Text("编辑") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

/** 手机上的意见一般当天就传走，时间带上「哪一天」比带年份更有用。 */
private fun formatTime(epochMillis: Long): String =
    TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private val TIME_FORMAT = DateTimeFormatter.ofPattern("M月d日 HH:mm")
