package io.github.xfl2342.voiceassistant.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 闹钟到点时被系统唤起；通知上的「稍后」「完成」两个按钮也走这里。
 *
 * 用广播而不是 Activity 接这三件事，是因为闹钟响的时候应用多半在后台：
 * 弹通知不需要界面，而要不要亮屏交给系统按全屏提醒的授权去决定。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: DEFAULT_TITLE
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()

        when (intent.action) {
            ACTION_REMINDER -> NotificationHelper.show(context, eventId, title)

            ACTION_SNOOZE -> {
                // 先把这一条撤掉，十分钟之后再响一次。
                NotificationHelper.cancel(context, title)
                ReminderScheduler(context).snooze(
                    eventId = eventId,
                    title = title,
                    minutes = NotificationHelper.SNOOZE_MINUTES,
                )
            }

            ACTION_DISMISS -> NotificationHelper.cancel(context, title)
        }
    }

    companion object {
        const val ACTION_REMINDER = "io.github.xfl2342.voiceassistant.action.REMINDER"
        const val ACTION_SNOOZE = "io.github.xfl2342.voiceassistant.action.SNOOZE"
        const val ACTION_DISMISS = "io.github.xfl2342.voiceassistant.action.DISMISS"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_EVENT_ID = "extra_event_id"

        private const val DEFAULT_TITLE = "行程提醒"

        /** 拼一个发给自己的广播：闹钟、通知上的按钮、稍后提醒都用它。 */
        fun intent(context: Context, actionName: String, eventId: String, title: String): Intent =
            Intent(context, ReminderReceiver::class.java).apply {
                action = actionName
                putExtra(EXTRA_EVENT_ID, eventId)
                putExtra(EXTRA_TITLE, title)
            }
    }
}
