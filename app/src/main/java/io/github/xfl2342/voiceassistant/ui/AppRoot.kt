package io.github.xfl2342.voiceassistant.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.xfl2342.voiceassistant.AppGraph
import io.github.xfl2342.voiceassistant.ui.calendar.CalendarScreen
import io.github.xfl2342.voiceassistant.ui.detail.EventDetailScreen
import io.github.xfl2342.voiceassistant.ui.edit.EventEditScreen
import io.github.xfl2342.voiceassistant.ui.record.RecordScreen
import io.github.xfl2342.voiceassistant.ui.settings.ReminderSettingsScreen
import io.github.xfl2342.voiceassistant.ui.theme.VoiceAssistantTheme

/** 当前显示的页面。页面不多，用状态切换就够了。 */
private sealed interface AppScreen {
    data object Calendar : AppScreen
    data object Record : AppScreen
    data class Detail(val eventId: String) : AppScreen
    data class Edit(val eventId: String) : AppScreen
    data object ReminderSettings : AppScreen
}

/**
 * 应用根节点。
 *
 * 页面还不多，用状态切换就够了，不引入导航库；再复杂就换成 Navigation Compose。
 */
@Composable
fun AppRoot(graph: AppGraph) {
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Calendar) }

    VoiceAssistantTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            val padded = Modifier.padding(innerPadding)
            when (val current = screen) {
                AppScreen.Calendar -> CalendarScreen(
                    repository = graph.eventRepository,
                    onRecordClick = { screen = AppScreen.Record },
                    onEventClick = { screen = AppScreen.Detail(it) },
                    onOpenReminderSettings = { screen = AppScreen.ReminderSettings },
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
                    onBack = { screen = AppScreen.Calendar },
                    onEdit = { screen = AppScreen.Edit(current.eventId) },
                    onDeleted = { screen = AppScreen.Calendar },
                    modifier = padded,
                )

                is AppScreen.Edit -> EventEditScreen(
                    service = graph.eventService,
                    eventId = current.eventId,
                    onBack = { screen = AppScreen.Detail(current.eventId) },
                    onSaved = { screen = AppScreen.Detail(current.eventId) },
                    modifier = padded,
                )

                AppScreen.ReminderSettings -> ReminderSettingsScreen(
                    onBack = { screen = AppScreen.Calendar },
                    modifier = padded,
                )
            }
        }
    }
}
