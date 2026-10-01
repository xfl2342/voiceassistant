package io.github.xfl2342.voiceassistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xfl2342.voiceassistant.data.EventRepository
import io.github.xfl2342.voiceassistant.data.db.AppDatabase
import io.github.xfl2342.voiceassistant.ui.calendar.CalendarScreen
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 日历上的滑动手势：左右翻月，收起成一周之后左右翻周。
 *
 * 只能在真机上跑，而且**要先把「后台弹出界面」放开**，否则测试用的空 Activity 起不来，
 * 测试会一直卡着等而不是报错（同一个坑记在 `FeedbackExportTest` 里）。装一次应用就会
 * 重置这个权限，所以每次都得先：
 *
 *     adb shell appops set io.github.xfl2342.voiceassistant 10021 allow
 *     gradlew :app:connectedDebugAndroidTest \
 *         -Pandroid.testInstrumentationRunnerArguments.class=io.github.xfl2342.voiceassistant.CalendarSwipeTest
 *
 * 数据用内存数据库，不碰手机上真实的那份行程。
 *
 * 默认标了 @Ignore：没放开那个权限时它会一直卡着等，挂在 `connectedAndroidTest` 里
 * 会把整个测试集拖死。要跑就把下面这行注解去掉。
 */
@Ignore("需要先 adb shell appops set 放开「后台弹出界面」，否则会卡住整个测试集")
@RunWith(AndroidJUnit4::class)
class CalendarSwipeTest {

    @get:Rule
    val compose = createComposeRule()

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val monthTitleFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月")
    private val dayTitleFormatter = DateTimeFormatter.ofPattern("M 月 d 日")

    private val today: LocalDate = LocalDate.now(zone)

    private fun showCalendar() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        compose.setContent {
            CalendarScreen(
                repository = EventRepository(database),
                onRecordClick = {},
                onEventClick = { _, _ -> },
                onOpenReminderSettings = {},
                onOpenSettings = {},
                onCreateClick = {},
                onOpenList = {},
                weekStartDay = DayOfWeek.MONDAY,
            )
        }
    }

    /** 日程区那条「M 月 d 日 周X」，用它判断当前选中的是哪天。 */
    private fun agendaTitleOf(date: LocalDate): String =
        dayTitleFormatter.format(date) + " " + WEEKDAY_LABELS[date.dayOfWeek.value - 1]

    /** 月历格子上随便找一格：这样滑动手势的落点在月历里，而不是标题栏或空白处。 */
    private fun aDayCell() = compose.onAllNodesWithText(today.dayOfMonth.toString())[0]

    @Test
    fun 左右滑动切换月份() {
        showCalendar()
        val thisMonth = monthTitleFormatter.format(YearMonth.from(today))
        val nextMonth = monthTitleFormatter.format(YearMonth.from(today).plusMonths(1))

        compose.onNodeWithText(thisMonth).assertIsDisplayed()

        // 在下面的日程列表上往左滑：日历主体里最好按的地方。
        compose.onNodeWithText("全部日程").performTouchInput {
            swipeLeft(startX = centerX + 200f, endX = centerX - 200f)
        }
        compose.waitForIdle()
        compose.onNodeWithText(nextMonth).assertIsDisplayed()

        // 再在月历格子上往右滑，翻回这个月。
        aDayCell().performTouchInput {
            swipeRight(startX = centerX - 200f, endX = centerX + 200f)
        }
        compose.waitForIdle()
        compose.onNodeWithText(thisMonth).assertIsDisplayed()
    }

    @Test
    fun 收起成一周后左右滑动切换周() {
        showCalendar()

        // 在月历格子上往上滑，收起成一周。
        aDayCell().performTouchInput { swipeUp(startY = centerY, endY = centerY - 200f) }
        compose.waitForIdle()
        compose.onNodeWithText(agendaTitleOf(today)).assertIsDisplayed()

        // 收起之后左右滑，翻的是一周：选中日跟着走七天。
        compose.onNodeWithText("全部日程").performTouchInput {
            swipeLeft(startX = centerX + 200f, endX = centerX - 200f)
        }
        compose.waitForIdle()
        compose.onNodeWithText(agendaTitleOf(today.plusDays(7))).assertIsDisplayed()

        compose.onNodeWithText("全部日程").performTouchInput {
            swipeRight(startX = centerX - 200f, endX = centerX + 200f)
        }
        compose.waitForIdle()
        compose.onNodeWithText(agendaTitleOf(today)).assertIsDisplayed()
    }

    private companion object {
        val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }
}
