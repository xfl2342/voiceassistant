package io.github.xfl2342.voiceassistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.xfl2342.voiceassistant.ui.AppRoot
import io.github.xfl2342.voiceassistant.ui.AppStart
import io.github.xfl2342.voiceassistant.ui.AppStartRequest
import io.github.xfl2342.voiceassistant.widget.TodayWidget
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * 这次要落到哪一页。
     *
     * 从桌面小组件点进来时，Intent 里带着「要看哪条行程」或「直接开始说话」；
     * 应用已经在后台时不会再走一遍 onCreate，所以 onNewIntent 也要接上。
     */
    private var startRequest by mutableStateOf(AppStartRequest(AppStart.Calendar, 0L))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = AppGraph(applicationContext)

        // 只有第一次进来才按 Intent 跳转：转屏重建时会再走一次 onCreate，
        // 那时候不该把用户又拽回小组件当初指定的那一页。
        if (savedInstanceState == null) {
            startRequest = AppStartRequest(AppStart.from(intent), 0L)
        }

        // 启动时把提醒顺延一遍：重复行程的提醒只预注册未来 30 天，
        // 每次打开应用都往后推，才不会出现「用着用着提醒就没了」。
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { graph.eventService.refreshAllReminders() }
            // 顺手把桌面小组件重画一遍：在应用里改完行程，回到桌面看到的也是新的。
            // 放在后台线程做，别让桌面那一块拖慢应用启动。
            TodayWidget.refresh(applicationContext)
        }

        setContent {
            AppRoot(graph, startRequest)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 每次外部入口都算一次新请求：连点两下同一个入口也要能再跳一次。
        startRequest = AppStartRequest(AppStart.from(intent), startRequest.id + 1)
    }
}
