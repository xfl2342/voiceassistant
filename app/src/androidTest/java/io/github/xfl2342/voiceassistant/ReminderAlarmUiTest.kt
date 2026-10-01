package io.github.xfl2342.voiceassistant

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xfl2342.voiceassistant.reminder.ReminderAlarmActivity
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 到点弹出来的那一屏：有「稍后 10 分钟」和「完成」，点完成就关掉。
 *
 * 只能在真机上跑，而且**要先把「后台弹出界面」放开**（否则 Activity 起不来，测试会卡住）：
 *
 *     adb shell appops set io.github.xfl2342.voiceassistant 10021 allow
 *     gradlew :app:connectedDebugAndroidTest \
 *         -Pandroid.testInstrumentationRunnerArguments.class=io.github.xfl2342.voiceassistant.ReminderAlarmUiTest
 *
 * 跑的时候手机会真的响一两秒 —— 这一屏本来就是干这个的。
 */
@Ignore("需要先 adb shell appops set 放开「后台弹出界面」，否则会卡住整个测试集")
@RunWith(AndroidJUnit4::class)
class ReminderAlarmUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ReminderAlarmActivity>()

    @Test
    fun 闹钟屏上有稍后与完成两个按钮() {
        compose.onNodeWithText("稍后 10 分钟").assertExists()
        compose.onNodeWithText("完成").assertExists()

        // 顺手留一张图，看看排版。
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "alarm-screen.png",
        )
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        compose.onNodeWithText("完成").performClick()
        compose.waitForIdle()
    }
}
