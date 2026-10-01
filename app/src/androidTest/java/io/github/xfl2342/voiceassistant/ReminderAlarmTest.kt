package io.github.xfl2342.voiceassistant

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xfl2342.voiceassistant.reminder.NotificationHelper
import io.github.xfl2342.voiceassistant.reminder.ReminderReceiver
import io.github.xfl2342.voiceassistant.reminder.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 提醒要像闹钟：通知得是闹钟类别、带声音与震动、带全屏提醒、带「稍后 / 完成」两个动作。
 *
 * 只能在真机上跑（通知与闹钟都是系统服务）。跑法：
 *
 *     gradlew :app:connectedDebugAndroidTest
 *
 * 用例自己会把这些提醒撤干净，不留给用户。
 */
@RunWith(AndroidJUnit4::class)
class ReminderAlarmTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun notificationManager() = context.getSystemService(NotificationManager::class.java)

    /** 通知是异步发出去的，等它出现。 */
    private fun awaitPosted(id: Int, expect: Boolean) {
        repeat(30) {
            val found = notificationManager().activeNotifications.any { it.id == id }
            if (found == expect) return
            Thread.sleep(100)
        }
        throw AssertionError("等了 3 秒，通知还是没有按预期出现 / 消失（expect=$expect）")
    }

    @Test
    fun 提醒通知是闹钟样式并带两个动作() {
        assertTrue(
            "通知权限没开，这个用例跑不了",
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )

        // 直接看我们组装出来的那一条：系统那份在没授权时会自己把全屏提醒摘掉，
        // 从系统里读就看不出我们到底发了什么。
        val built = NotificationHelper.build(context, eventId = "self-check", title = "自检提醒")

        assertEquals(Notification.CATEGORY_ALARM, built.category)
        assertNotNull("没有全屏提醒的入口，锁屏就不会亮", built.fullScreenIntent)
        assertEquals("应该是「稍后」与「完成」两个动作", 2, built.actions.size)
        assertTrue(
            "「稍后」的按钮不见了",
            built.actions.any { it.title.toString().contains("稍后") },
        )
        assertTrue(
            "「完成」的按钮不见了",
            built.actions.any { it.title.toString() == "完成" },
        )
        assertNotNull("点正文应该能进这条行程", built.contentIntent)
    }

    @Test
    fun 提醒走的渠道带声音与震动() {
        NotificationHelper.ensureChannel(context)

        val channel = notificationManager().getNotificationChannel(NotificationHelper.CHANNEL_ID)

        assertNotNull("通知渠道没建起来", channel)
        assertNotNull("渠道上没配声音", channel.sound)
        assertTrue("渠道没开震动", channel.shouldVibrate())
        assertTrue("震动节奏是空的", channel.vibrationPattern?.isNotEmpty() == true)
        assertTrue("渠道重要性不够，锁屏上不会跳出来", channel.importance >= NotificationManager.IMPORTANCE_HIGH)
    }

    @Test
    fun 点稍后会把通知撤掉并重新排一个闹钟() {
        val title = "自检稍后-${System.currentTimeMillis()}"
        val id = NotificationHelper.notificationId(title)
        try {
            NotificationHelper.show(context, eventId = "self-check", title = title)
            awaitPosted(id, expect = true)

            // 相当于用户点了通知上的「稍后 10 分钟」。
            ReminderReceiver().onReceive(
                context,
                ReminderReceiver.intent(
                    context = context,
                    actionName = ReminderReceiver.ACTION_SNOOZE,
                    eventId = "self-check",
                    title = title,
                ),
            )

            awaitPosted(id, expect = false)
        } finally {
            NotificationHelper.cancel(context, title)
            // 把刚才排出来的那个稍后闹钟撤掉，别过十分钟真的响起来。
            ReminderScheduler(context).cancelSnooze("self-check")
        }
    }
}
