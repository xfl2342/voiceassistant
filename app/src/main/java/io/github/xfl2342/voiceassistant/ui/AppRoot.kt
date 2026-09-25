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
import io.github.xfl2342.voiceassistant.ui.record.RecordScreen
import io.github.xfl2342.voiceassistant.ui.theme.VoiceAssistantTheme

/**
 * 应用根节点。
 *
 * 目前只有两个页面，用状态切换就够了，不引入导航库；页面多起来再换成 Navigation Compose。
 */
@Composable
fun AppRoot(graph: AppGraph) {
    var showRecordScreen by remember { mutableStateOf(false) }

    VoiceAssistantTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            if (showRecordScreen) {
                RecordScreen(
                    repository = graph.eventRepository,
                    settingsStore = graph.settingsStore,
                    onSaved = { showRecordScreen = false },
                    onBack = { showRecordScreen = false },
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                CalendarScreen(
                    repository = graph.eventRepository,
                    onRecordClick = { showRecordScreen = true },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}
