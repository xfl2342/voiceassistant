package io.github.xfl2342.voiceassistant.ui

import android.content.Context
import android.content.Intent
import io.github.xfl2342.voiceassistant.MainActivity

/**
 * 从桌面小组件点进来时，应用该直接落到哪一页。
 *
 * 小组件上的每一行行程、那个小话筒按钮，都是往这里塞一个「起点」再打开应用：
 * 行程要能点到详情页，话筒要能直接开始说话，而不是每次都从日历首页翻过去。
 */
sealed interface AppStart {

    /** 日历首页（默认）。 */
    data object Calendar : AppStart

    /** 直接进录音页。 */
    data object Record : AppStart

    /** 直接看某一条行程的详情。 */
    data class EventDetail(val eventId: String) : AppStart

    companion object {

        private const val EXTRA_OPEN = "io.github.xfl2342.voiceassistant.extra.OPEN"
        private const val EXTRA_EVENT_ID = "io.github.xfl2342.voiceassistant.extra.EVENT_ID"

        private const val OPEN_RECORD = "record"
        private const val OPEN_EVENT = "event"

        /**
         * 拼一个能落到指定页面的启动 Intent，小组件的点击跳转用它。
         *
         * `NEW_TASK` 是给小组件这种「从别的进程发起」的场景备的：应用已经在后台时，
         * 它会把原有任务调回前台，而不是再开一个。
         */
        fun intent(context: Context, start: AppStart): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                when (start) {
                    AppStart.Calendar -> Unit
                    AppStart.Record -> putExtra(EXTRA_OPEN, OPEN_RECORD)
                    is AppStart.EventDetail -> {
                        putExtra(EXTRA_OPEN, OPEN_EVENT)
                        putExtra(EXTRA_EVENT_ID, start.eventId)
                    }
                }
            }

        /** 从启动 Intent 里读出要落到哪一页；认不出来就当日历首页。 */
        fun from(intent: Intent?): AppStart = when (intent?.getStringExtra(EXTRA_OPEN)) {
            OPEN_RECORD -> Record
            OPEN_EVENT -> intent.getStringExtra(EXTRA_EVENT_ID)
                ?.takeIf { it.isNotBlank() }
                ?.let { EventDetail(it) }
                ?: Calendar
            else -> Calendar
        }
    }
}

/**
 * 一次跳转请求。
 *
 * [id] 每次外部进来都会加一：连点两下同一个入口（比如两次都点「语音录入」），
 * 内容一样但请求不同，界面才知道该再跳一次。
 */
data class AppStartRequest(val start: AppStart, val id: Long)
