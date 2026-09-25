package io.github.xfl2342.voiceassistant.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.github.xfl2342.voiceassistant.R

/** 通知渠道与通知的组装。 */
object NotificationHelper {

    private const val CHANNEL_ID = "event_reminders"

    /** 创建通知渠道；Android 8 之后不建渠道通知不会显示。重复调用是安全的。 */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "行程提醒",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "在行程开始前提醒你"
                }
            )
        }
    }

    fun show(context: Context, title: String) {
        ensureChannel(context)

        val manager = context.getSystemService(NotificationManager::class.java)
        // 用户关掉了通知权限（Android 13 起需要主动授权），这里直接安静返回，不要报错。
        if (!manager.areNotificationsEnabled()) return

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("行程即将开始")
            .setAutoCancel(true)
            .build()

        // 用标题做通知 id：同一条行程的重复提醒会覆盖上一条，不会堆满通知栏。
        manager.notify(title.hashCode(), notification)
    }
}
