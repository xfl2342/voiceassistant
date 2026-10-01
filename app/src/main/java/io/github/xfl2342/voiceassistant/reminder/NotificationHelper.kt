package io.github.xfl2342.voiceassistant.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.xfl2342.voiceassistant.R
import io.github.xfl2342.voiceassistant.ui.AppStart

/**
 * 提醒通知的组装。
 *
 * 这一版的目标是「像闹钟」：走闹钟音量通道、带震动、带全屏提醒，
 * 通知上直接给「稍后 10 分钟」和「完成」两个动作，点正文直接进这条行程。
 *
 * 全屏提醒（锁屏上直接亮起来的那一屏）在 Android 14 起要用户单独授权，
 * 拿不到就退化成一条高优先级通知：声音照响，只是不自动亮屏。
 */
object NotificationHelper {

    /**
     * 渠道 id 换了新的。
     *
     * 通知渠道建好之后，声音与震动**改不了**：老版本那条渠道是「普通通知」的音，
     * 想让它变成闹钟音只能换一个 id 重建，再把旧的删掉。
     */
    const val CHANNEL_ID = "event_alarm"
    private const val LEGACY_CHANNEL_ID = "event_reminders"

    /** 稍后提醒的时长：一个不长不短、够起身接杯水的数。 */
    const val SNOOZE_MINUTES = 10

    /**
     * 通知 id 用标题算：同一条行程的重复提醒会覆盖上一条，不堆满通知栏；
     * 「稍后 10 分钟」之后重新响的那次也是同一个 id，同样只占一条。
     */
    fun notificationId(title: String): Int = title.hashCode()

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
                    setSound(AlarmSound.URI, AlarmSound.ATTRIBUTES)
                    enableVibration(true)
                    vibrationPattern = AlarmSound.VIBRATION_PATTERN
                },
            )
        }
        // 升级上来的机器会留着那条旧渠道，删掉，免得系统设置里挂一个用不上的。
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
    }

    fun show(context: Context, eventId: String, title: String) {
        ensureChannel(context)

        val manager = context.getSystemService(NotificationManager::class.java)
        // 用户关掉了通知权限（Android 13 起需要主动授权），这里直接安静返回，不要报错。
        if (!manager.areNotificationsEnabled()) return

        manager.notify(notificationId(title), build(context, eventId, title))
    }

    /**
     * 组装这一条通知。
     *
     * 单独抽出来是为了能直接检查「有没有全屏提醒、有没有两个按钮」——
     * 系统在没授权时会把自己那份里的全屏提醒摘掉，看系统里那份就查不出我们到底发了什么。
     */
    fun build(context: Context, eventId: String, title: String): Notification {
        val id = notificationId(title)
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("行程即将开始")
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            // 点正文：直接落到这条行程的详情页。
            .setContentIntent(
                activityPendingIntent(
                    context,
                    id + 1,
                    AppStart.intent(context, AppStart.EventDetail(eventId)),
                ),
            )
            // 到点自动弹一屏；系统不给这个权限时，它就只是一条高优先级通知。
            .setFullScreenIntent(
                activityPendingIntent(
                    context,
                    id + 2,
                    ReminderAlarmActivity.intent(context, eventId, title),
                ),
                true,
            )
            .addAction(
                action(
                    "稍后 $SNOOZE_MINUTES 分钟",
                    actionPendingIntent(
                        context,
                        id + 3,
                        ReminderReceiver.ACTION_SNOOZE,
                        eventId,
                        title,
                    ),
                ),
            )
            .addAction(
                action(
                    "完成",
                    actionPendingIntent(
                        context,
                        id + 4,
                        ReminderReceiver.ACTION_DISMISS,
                        eventId,
                        title,
                    ),
                ),
            )
            .build()
    }

    /** 关掉某条提醒的通知（点了「完成」或「稍后」之后调）。 */
    fun cancel(context: Context, title: String) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(title))
    }

    /** 通知上的按钮：没有图标，只有文字。 */
    private fun action(label: String, pendingIntent: PendingIntent): Notification.Action =
        Notification.Action.Builder(null, label, pendingIntent).build()

    private fun actionPendingIntent(
        context: Context,
        requestCode: Int,
        actionName: String,
        eventId: String,
        title: String,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        ReminderReceiver.intent(context, actionName, eventId, title),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun activityPendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
