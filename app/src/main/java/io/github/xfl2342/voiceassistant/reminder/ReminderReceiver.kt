package io.github.xfl2342.voiceassistant.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 闹钟到点时被系统唤起，负责弹出通知。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return

        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "行程提醒"
        NotificationHelper.show(context, title)
    }

    companion object {
        const val ACTION_REMINDER = "io.github.xfl2342.voiceassistant.action.REMINDER"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_EVENT_ID = "extra_event_id"
    }
}
