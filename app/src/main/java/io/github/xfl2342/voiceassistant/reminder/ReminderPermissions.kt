package io.github.xfl2342.voiceassistant.reminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationManager

/** 提醒能否准时弹出、能不能像闹钟一样亮屏，取决于这几项系统设置。 */
data class ReminderPermissionStatus(
    /** 精确闹钟：不开的话提醒可能晚几分钟。 */
    val exactAlarmAllowed: Boolean,
    /** 通知权限：关了就完全收不到提醒。 */
    val notificationsEnabled: Boolean,
    /** 是否已加入电池优化白名单：没加的话系统可能在后台把提醒掐掉。 */
    val batteryOptimizationIgnored: Boolean,
    /**
     * 全屏提醒：Android 14 起要单独授权。
     *
     * 关着的时候提醒照样响、通知照样有，只是不会在锁屏上自动亮起来。
     */
    val fullScreenAlarmAllowed: Boolean,
) {
    /** 影响「提醒能不能准时响」的三项。全屏提醒只影响亮不亮屏，不在这里面。 */
    val allGood: Boolean
        get() = exactAlarmAllowed && notificationsEnabled && batteryOptimizationIgnored

    /** 四项都到位：既准时响，也会自动亮屏。 */
    val allRingingGood: Boolean get() = allGood && fullScreenAlarmAllowed
}

/**
 * 读取与跳转提醒相关的系统设置。
 *
 * 这些设置分散在系统里好几个地方，用户很难自己找到，所以应用负责把他们带过去。
 */
object ReminderPermissions {

    fun read(context: Context): ReminderPermissionStatus {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return ReminderPermissionStatus(
            exactAlarmAllowed = ReminderScheduler(context).canScheduleExact,
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            batteryOptimizationIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false,
            fullScreenAlarmAllowed = fullScreenAlarmAllowed(context),
        )
    }

    /** Android 14 之前没有这项限制，一律算允许。 */
    private fun fullScreenAlarmAllowed(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else {
            true
        }

    fun openExactAlarmSettings(context: Context) {
        start(
            context,
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", context.packageName, null)),
        )
    }

    fun openNotificationSettings(context: Context) {
        start(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }

    fun openBatteryOptimizationSettings(context: Context) {
        start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        start(
            context,
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(Uri.fromParts("package", context.packageName, null)),
        )
    }

    /** 应用信息页：小米等系统的「自启动」「省电策略」都藏在这一页里。 */
    fun openAppDetailSettings(context: Context) {
        start(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null)),
        )
    }

    private fun start(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // 个别机型没有对应的设置页，退回到应用信息页，总比点了没反应好。
        runCatching { context.startActivity(intent) }
            .onFailure { openAppDetailSettings(context) }
    }
}
