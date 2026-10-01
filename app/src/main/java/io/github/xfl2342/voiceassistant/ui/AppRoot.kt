package io.github.xfl2342.voiceassistant.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.xfl2342.voiceassistant.AppGraph
import io.github.xfl2342.voiceassistant.data.ThemeMode
import io.github.xfl2342.voiceassistant.ui.calendar.CalendarScreen
import io.github.xfl2342.voiceassistant.ui.detail.EventDetailScreen
import io.github.xfl2342.voiceassistant.ui.edit.EventEditScreen
import io.github.xfl2342.voiceassistant.ui.list.EventListScreen
import io.github.xfl2342.voiceassistant.ui.record.RecordScreen
import io.github.xfl2342.voiceassistant.ui.settings.BackupScreen
import io.github.xfl2342.voiceassistant.ui.settings.DeepSeekScreen
import io.github.xfl2342.voiceassistant.ui.settings.FeedbackScreen
import io.github.xfl2342.voiceassistant.ui.settings.ReminderSettingsScreen
import io.github.xfl2342.voiceassistant.ui.settings.SettingsScreen
import io.github.xfl2342.voiceassistant.ui.theme.VoiceAssistantTheme
import java.time.LocalDate

/** 当前显示的页面。页面不多，用状态切换就够了。 */
private sealed interface AppScreen {
    data object Calendar : AppScreen
    data object Record : AppScreen
    /**
     * 行程详情。
     *
     * [from] 记住是从哪一页点进来的：从「全部日程」进来的，返回时就该回列表。
     * 详情页自己算不出这件事，只能由跳转的那一方带进来。
     *
     * [occurrenceDate] 是「从日历上的某一天点进来的」——重复行程靠它才知道用户指的是哪一次，
     * 也才能提供「只删这一次」。从列表或桌面小组件点进来时没有这一天。
     */
    data class Detail(
        val eventId: String,
        val from: AppScreen = Calendar,
        val occurrenceDate: LocalDate? = null,
    ) : AppScreen
    /** 编辑行程。[backTo] 是编辑完之后要回去的那一页（进来时的那张详情页）。 */
    data class Edit(val eventId: String, val backTo: AppScreen = Calendar) : AppScreen
    data class Create(val date: LocalDate) : AppScreen
    data object EventList : AppScreen
    data object Settings : AppScreen
    /** DeepSeek 配置：Key 的输入、修改、删除都收在这一页，设置页只留一行状态。 */
    data object DeepSeek : AppScreen
    /** 数据备份：把行程、提醒与改进意见导成一份文件。 */
    data object Backup : AppScreen
    /** 改进意见：手机上随手记，攒着连电脑时读走。 */
    data object Feedback : AppScreen
    /** 提醒设置：可能从日历的提示条进来，也可能从设置进来，返回时回到来的地方。 */
    data class ReminderSettings(val fromSettings: Boolean = false) : AppScreen
}

/**
 * 应用根节点。
 *
 * 页面还不多，用状态切换就够了，不引入导航库；再复杂就换成 Navigation Compose。
 *
 * [startRequest] 说的是「从桌面小组件这类外部入口进来时要落到哪一页」：第一次进来
 * 直接落到那一页；应用已经在运行时（比如又点了小组件上的另一条行程）也照样跳过去。
 */
@Composable
fun AppRoot(
    graph: AppGraph,
    startRequest: AppStartRequest = AppStartRequest(AppStart.Calendar, 0L),
) {
    var screen by remember { mutableStateOf(startRequest.start.toScreen()) }
    // 主题与每周起始日由设置页修改，这里持有状态以便立刻生效。
    var themeMode by remember { mutableStateOf(graph.settingsStore.themeMode) }
    var weekStartDay by remember { mutableStateOf(graph.settingsStore.weekStartDay) }

    // id 每次外部进来都会加一：同一个入口连点两下也能再跳一次。
    LaunchedEffect(startRequest.id) {
        if (startRequest.id > 0L) screen = startRequest.start.toScreen()
    }

    VoiceAssistantTheme(
        darkTheme = when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        },
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            val padded = Modifier.padding(innerPadding)
            when (val current = screen) {
                AppScreen.Calendar -> CalendarScreen(
                    repository = graph.eventRepository,
                    onRecordClick = { screen = AppScreen.Record },
                    onEventClick = { eventId, date ->
                        screen = AppScreen.Detail(
                            eventId = eventId,
                            from = AppScreen.Calendar,
                            occurrenceDate = date,
                        )
                    },
                    onOpenReminderSettings = { screen = AppScreen.ReminderSettings() },
                    onOpenSettings = { screen = AppScreen.Settings },
                    onCreateClick = { screen = AppScreen.Create(it) },
                    onOpenList = { screen = AppScreen.EventList },
                    weekStartDay = weekStartDay,
                    modifier = padded,
                )

                AppScreen.Record -> RecordScreen(
                    service = graph.eventService,
                    settingsStore = graph.settingsStore,
                    onSaved = { screen = AppScreen.Calendar },
                    onBack = { screen = AppScreen.Calendar },
                    modifier = padded,
                )

                is AppScreen.Detail -> EventDetailScreen(
                    service = graph.eventService,
                    eventId = current.eventId,
                    occurrenceDate = current.occurrenceDate,
                    // 从哪进来的就回哪去：日历点开的回日历，全部日程点开的回列表。
                    onBack = { screen = current.from },
                    onEdit = { screen = AppScreen.Edit(current.eventId, backTo = current) },
                    onDeleted = { screen = current.from },
                    modifier = padded,
                )

                is AppScreen.Edit -> EventEditScreen(
                    service = graph.eventService,
                    settingsStore = graph.settingsStore,
                    eventId = current.eventId,
                    initialDate = null,
                    onBack = { screen = current.backTo },
                    onSaved = { screen = current.backTo },
                    modifier = padded,
                )

                is AppScreen.Create -> EventEditScreen(
                    service = graph.eventService,
                    settingsStore = graph.settingsStore,
                    eventId = null,
                    initialDate = current.date,
                    onBack = { screen = AppScreen.Calendar },
                    onSaved = { screen = AppScreen.Calendar },
                    modifier = padded,
                )

                AppScreen.EventList -> EventListScreen(
                    repository = graph.eventRepository,
                    onBack = { screen = AppScreen.Calendar },
                    onEventClick = { screen = AppScreen.Detail(it, from = AppScreen.EventList) },
                    modifier = padded,
                )

                AppScreen.Settings -> SettingsScreen(
                    settingsStore = graph.settingsStore,
                    onBack = { screen = AppScreen.Calendar },
                    onOpenDeepSeek = { screen = AppScreen.DeepSeek },
                    onOpenFeedback = { screen = AppScreen.Feedback },
                    onOpenBackup = { screen = AppScreen.Backup },
                    onOpenReminderSettings = { screen = AppScreen.ReminderSettings(fromSettings = true) },
                    onThemeModeChange = { themeMode = it },
                    onWeekStartChange = { weekStartDay = it },
                    modifier = padded,
                )

                AppScreen.DeepSeek -> DeepSeekScreen(
                    settingsStore = graph.settingsStore,
                    onBack = { screen = AppScreen.Settings },
                    modifier = padded,
                )

                AppScreen.Backup -> BackupScreen(
                    service = graph.backupService,
                    onBack = { screen = AppScreen.Settings },
                    modifier = padded,
                )

                AppScreen.Feedback -> FeedbackScreen(
                    repository = graph.feedbackRepository,
                    onBack = { screen = AppScreen.Settings },
                    modifier = padded,
                )

                is AppScreen.ReminderSettings -> ReminderSettingsScreen(
                    onBack = {
                        screen = if (current.fromSettings) AppScreen.Settings else AppScreen.Calendar
                    },
                    modifier = padded,
                )
            }
        }
    }
}

/** 外部入口要落到哪一页。 */
private fun AppStart.toScreen(): AppScreen = when (this) {
    AppStart.Calendar -> AppScreen.Calendar
    AppStart.Record -> AppScreen.Record
    is AppStart.EventDetail -> AppScreen.Detail(eventId)
}
