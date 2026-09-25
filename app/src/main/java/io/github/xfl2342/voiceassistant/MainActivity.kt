package io.github.xfl2342.voiceassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.xfl2342.voiceassistant.ui.AppRoot
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = AppGraph(applicationContext)
        // 启动时把提醒顺延一遍：重复行程的提醒只预注册未来 30 天，
        // 每次打开应用都往后推，才不会出现「用着用着提醒就没了」。
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { graph.eventService.refreshAllReminders() }
        }
        setContent {
            AppRoot(graph)
        }
    }
}
