package io.github.xfl2342.voiceassistant.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import io.github.xfl2342.voiceassistant.data.db.ReminderEntity

/**
 * 把提醒注册到系统的闹钟服务。
 *
 * 为什么不直接用定时任务（WorkManager）：那种方式只保证「最终会执行」，
 * 到点差几分钟很正常；提醒要的是准时，所以用闹钟。
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * 是否具备精确闹钟能力。
     *
     * Android 12 起，精确闹钟需要用户在系统设置里单独允许，默认可能是关的。
     */
    val canScheduleExact: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    fun schedule(reminder: ReminderEntity, title: String) {
        schedule(reminder.id, reminder.eventId, title, reminder.triggerAt)
    }

    fun schedule(reminderId: String, eventId: String, title: String, triggerAt: Long) {
        // 已经过去的时间点不再注册，否则会立刻弹一条通知。
        if (triggerAt <= System.currentTimeMillis()) return

        val pendingIntent = pendingIntent(reminderId, eventId, title)
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        } else {
            // 没有精确闹钟权限时退化成不精确闹钟：可能晚几分钟，但不会完全不响。
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        }
    }

    fun cancel(reminderId: String) {
        alarmManager.cancel(pendingIntent(reminderId, eventId = "", title = ""))
    }

    /**
     * 稍后再响一次。
     *
     * 这是一次性的临时闹钟，**不写进提醒表**：稍后十分钟是「刚刚这个动作」，
     * 不是行程本身的提醒规则，不该因为按了一次就多出一条记录。
     *
     * requestCode 只跟行程绑定：连着按两次「稍后」只会重排同一个闹钟，
     * 而不是十分钟后响两声。
     */
    fun snooze(eventId: String, title: String, minutes: Int) {
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val pendingIntent = pendingIntent(
            reminderId = snoozeId(eventId),
            eventId = eventId,
            title = title,
        )
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    /** 撤掉某条行程还没响的「稍后提醒」（测试与清理时用得上）。 */
    fun cancelSnooze(eventId: String) {
        alarmManager.cancel(pendingIntent(snoozeId(eventId), eventId = "", title = ""))
    }

    private fun snoozeId(eventId: String): String = "snooze:$eventId"

    private fun pendingIntent(reminderId: String, eventId: String, title: String): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            ReminderReceiver.intent(context, ReminderReceiver.ACTION_REMINDER, eventId, title),
            // extras 不参与 PendingIntent 的相等判断，因此取消时用同一套 action 和 requestCode 即可。
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
