package io.github.xfl2342.voiceassistant.ui.record

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.xfl2342.voiceassistant.ai.DeepSeekClient
import io.github.xfl2342.voiceassistant.ai.EventDraft
import io.github.xfl2342.voiceassistant.data.SettingsStore
import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.domain.ConflictReport
import io.github.xfl2342.voiceassistant.domain.EventDraftMapper
import io.github.xfl2342.voiceassistant.domain.EventSaveBundle
import io.github.xfl2342.voiceassistant.domain.EventService
import io.github.xfl2342.voiceassistant.domain.ReminderPreset
import io.github.xfl2342.voiceassistant.speech.SpeechEvent
import io.github.xfl2342.voiceassistant.speech.offline.OfflineSpeechEngine
import io.github.xfl2342.voiceassistant.ui.components.ConflictDialog
import io.github.xfl2342.voiceassistant.ui.components.EventColors
import io.github.xfl2342.voiceassistant.ui.components.InfoRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 录音页：说一句话 → 识别 → 解析 → 确认 → 存入日历。
 *
 * 这是整个 App 的主入口，也就是「最短闭环」所在。验证阶段那些诊断信息已经去掉，
 * 只保留用户真正会用到的部分；出错时仍然给出可读的原因。
 */
@Composable
fun RecordScreen(
    service: EventService,
    settingsStore: SettingsStore,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { OfflineSpeechEngine(context, scope) }

    DisposableEffect(engine) {
        // 进入页面就把模型加载好，第一次说话时不必再等模型初始化。
        engine.warmUp()
        onDispose { engine.release() }
    }

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var listening by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var speechStatus by remember { mutableStateOf("按住下面的按钮说话，说多久都行，松开自动识别") }
    var recognizedText by remember { mutableStateOf("") }

    var sentence by remember { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    /** 等待通知权限结果之后再继续保存。 */
    var waitingPermission by remember { mutableStateOf<EventSaveBundle?>(null) }
    /** 撞上已有行程时先弹窗问一句，用户确认后再从这里继续保存。 */
    var conflictReport by remember { mutableStateOf<ConflictReport?>(null) }
    var waitingConflict by remember { mutableStateOf<EventSaveBundle?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<EventDraft?>(null) }

    fun startListening() {
        errorText = null
        recognizedText = ""
        listening = true
        speechStatus = "正在聆听，松开结束"

        engine.start { event ->
            when (event) {
                SpeechEvent.Ready -> speechStatus = "正在聆听，松开结束"
                SpeechEvent.EndOfSpeech -> speechStatus = "识别中…"
                is SpeechEvent.Partial -> {
                    recognizedText = event.text
                    speechStatus = "识别中…"
                }
                is SpeechEvent.Final -> {
                    listening = false
                    recognizedText = event.alternatives.firstOrNull().orEmpty()
                    speechStatus = if (recognizedText.isEmpty()) "没有识别到内容" else "识别完成"
                    if (recognizedText.isNotEmpty()) sentence = recognizedText
                }
                is SpeechEvent.Failed -> {
                    listening = false
                    speechStatus = "识别失败"
                    // 失败之前引擎可能已经吐出一部分文字，先留下来，别让用户白说一遍。
                    errorText = if (recognizedText.isNotBlank()) {
                        sentence = recognizedText
                        "${event.errorName}：${event.hint}\n" +
                            "已经听到的内容已填入下面的输入框，可以直接用，也可以手动补齐。"
                    } else {
                        "${event.errorName}：${event.hint}"
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) startListening() else errorText = "没有录音权限，无法识别"
    }

    fun stopListening() {
        if (!listening) return
        engine.stop()
        listening = false
        speechStatus = "识别中…"
    }

    fun parse() {
        val text = sentence.trim()
        if (text.isEmpty()) {
            errorText = "请先说一句或输入一句话"
            return
        }

        // Key 统一在设置里配置，这里只负责用。
        val apiKey = settingsStore.deepSeekApiKey
        if (apiKey.isNullOrBlank()) {
            errorText = "还没有配置 DeepSeek API Key，请到右上角「设置」里填写后再试"
            return
        }

        parsing = true
        errorText = null
        draft = null

        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                DeepSeekClient(apiKey, settingsStore.deepSeekModel).parseEvent(text)
            }
            parsing = false
            when (outcome) {
                is DeepSeekClient.Outcome.Success ->
                    EventDraft.parse(outcome.content)
                        .onSuccess { draft = it }
                        .onFailure { errorText = "返回内容不是合法 JSON：" + (it.message ?: "") }
                is DeepSeekClient.Outcome.Failure -> errorText = outcome.message
            }
        }
    }

    fun performSave(bundle: EventSaveBundle) {
        saving = true
        errorText = null

        scope.launch {
            // 入库 + 注册系统闹钟，两件事在这里一起完成。
            service.save(bundle)

            saving = false
            onSaved()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // 即使用户拒绝通知权限，也照样把行程存下来，只是收不到提醒。
        val bundle = waitingPermission
        waitingPermission = null
        if (bundle != null) performSave(bundle)
    }

    fun continueSave(bundle: EventSaveBundle) {
        // 只有真的要排提醒时才去要通知权限：待办、或用户选了「不提醒」的行程，
        // 没必要为此弹一次授权框。
        val needsNotificationPermission = bundle.reminders.isNotEmpty() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

        if (needsNotificationPermission) {
            // 真正要用到提醒的时候才申请权限，而不是一打开应用就弹窗。
            waitingPermission = bundle
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            performSave(bundle)
        }
    }

    /**
     * 点「保存到日历」之后：先转成实体，再看有没有撞上已有行程。
     *
     * 顺序上先查冲突、再申请通知权限，是为了不让两个弹窗叠在一起——
     * 用户先回答「要不要存」，再回答「要不要允许提醒」。
     */
    fun save() {
        val current = draft ?: return
        saving = true
        errorText = null

        scope.launch {
            val mapped = EventDraftMapper.toBundle(current, rawText = sentence.trim())
            val bundle = mapped.getOrNull()
            if (bundle == null) {
                saving = false
                errorText = "保存失败：" + (mapped.exceptionOrNull()?.message ?: "数据不完整")
                return@launch
            }

            val report = service.findConflicts(bundle.event, bundle.rule)
            saving = false
            if (report.hasConflict) {
                waitingConflict = bundle
                conflictReport = report
            } else {
                continueSave(bundle)
            }
        }
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text(
                text = "记录行程",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "第一步 · 说一句话",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = speechStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (recognizedText.isNotEmpty()) {
                    Text(text = recognizedText, style = MaterialTheme.typography.bodyLarge)
                }
                // 长按说话：按下开始录音，松开结束。
                // 这样做的好处是「什么时候开始说」由你决定——点一下再想半天，
                // 识别引擎会以为你已经说完了。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            if (pressed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        )
                        .pointerInput(permissionGranted) {
                            detectTapGestures(
                                onPress = {
                                    if (!permissionGranted) {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        pressed = true
                                        startListening()
                                        // 一直等到手指抬起（或手势被取消）。
                                        tryAwaitRelease()
                                        pressed = false
                                        stopListening()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            listening -> "正在聆听…松开结束"
                            else -> "按住说话"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (pressed) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "第二步 · 解析成行程",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = sentence,
                    onValueChange = { sentence = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("要解析的一句话") },
                    minLines = 2,
                    placeholder = { Text("例如：明天下午三点开项目评审会，提前十五分钟提醒我") },
                )
                Button(onClick = { parse() }, enabled = !parsing && !saving) {
                    if (parsing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("解析")
                    }
                }
            }
        }

        errorText?.let { message ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        draft?.let { parsed ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "第三步 · 确认并保存",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    InfoRow("标题", parsed.title.ifBlank { "（空）" })
                    if (parsed.isTodo) {
                        InfoRow("类型", "待办（不设时间）")
                    } else {
                        InfoRow("开始", parsed.start.ifBlank { "（空）" })
                        InfoRow("结束", parsed.end.ifBlank { "（空）" })
                        if (parsed.allDay) InfoRow("类型", "全天")
                    }
                    if (parsed.location.isNotBlank()) InfoRow("地点", parsed.location)
                    InfoRow(
                        label = "急迫程度",
                        value = EventColors.label(EventEntity.normalizeUrgency(parsed.urgency)),
                    )
                    parsed.recurrenceText?.let { InfoRow("重复", it) }
                    InfoRow(
                        label = "提醒",
                        value = describeReminders(parsed),
                    )
                    if (parsed.warnings.isNotEmpty()) {
                        Text(
                            text = parsed.warnings.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        onClick = { save() },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                saving -> "保存中…"
                                parsed.isTodo -> "保存待办"
                                else -> "保存到日历"
                            },
                        )
                    }
                }
            }
        }

    }

    conflictReport?.let { report ->
        ConflictDialog(
            report = report,
            onSaveAnyway = {
                val bundle = waitingConflict
                conflictReport = null
                waitingConflict = null
                if (bundle != null) continueSave(bundle)
            },
            onBack = {
                conflictReport = null
                waitingConflict = null
            },
        )
    }
}

/** 把提醒说清楚：用户明确说了就照实显示，没说就说明是按急迫程度取的默认值。 */
private fun describeReminders(draft: EventDraft): String {
    // 待办没有时间，谈不上提前多久提醒。
    if (draft.isTodo) return "不提醒"
    if (draft.reminderMinutes.isNotEmpty()) {
        return draft.reminderMinutes.joinToString("、") { ReminderPreset.describe(it) }
    }
    val preset = ReminderPreset.fromUrgency(draft.urgency)
    return "${ReminderPreset.describe(preset.minutesBefore)}（按「${preset.label}」默认）"
}
