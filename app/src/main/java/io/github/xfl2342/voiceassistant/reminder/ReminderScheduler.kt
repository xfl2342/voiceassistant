package io.github.xfl2342.voiceassistant.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    private fun pendingIntent(reminderId: String, eventId: String, title: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            // extras 不参与 PendingIntent 的相等判断，因此取消时用同一套 action 和 requestCode 即可。
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
