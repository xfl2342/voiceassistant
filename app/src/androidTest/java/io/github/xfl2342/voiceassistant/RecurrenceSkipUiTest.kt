package io.github.xfl2342.voiceassistant

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xfl2342.voiceassistant.data.EventRepository
import io.github.xfl2342.voiceassistant.data.SettingsStore
import io.github.xfl2342.voiceassistant.data.db.AppDatabase
import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import io.github.xfl2342.voiceassistant.domain.EventSaveBundle
import io.github.xfl2342.voiceassistant.domain.EventService
import io.github.xfl2342.voiceassistant.domain.ReminderPlanner
import io.github.xfl2342.voiceassistant.domain.ReminderSync
import io.github.xfl2342.voiceassistant.reminder.ReminderScheduler
import io.github.xfl2342.voiceassistant.ui.detail.EventDetailScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 从日历某一天点进来的重复行程，删除时能选「只删这一次」。
 *
 * 只能在真机上跑，而且**要先把「后台弹出界面」放开**，否则测试用的空 Activity 起不来，
 * 测试会一直卡着等而不是报错（同一个坑记在 `FeedbackExportTest` 里）：
 *
 *     adb shell appops set io.github.xfl2342.voiceassistant 10021 allow
 *     gradlew :app:connectedDebugAndroidTest \
 *         -Pandroid.testInstrumentationRunnerArguments.class=io.github.xfl2342.voiceassistant.RecurrenceSkipUiTest
 *
 * 默认标了 @Ignore：没放开那个权限时它会一直卡着等，挂在 `connectedAndroidTest` 里
 * 会把整个测试集拖死。要跑就把下面这行注解去掉。
 */
@Ignore("需要先 adb shell appops set 放开「后台弹出界面」，否则会卡住整个测试集")
@RunWith(AndroidJUnit4::class)
class RecurrenceSkipUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun 删除时可以选择只删这一次() {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repository = EventRepository(database)
        val scheduler = ReminderScheduler(context)
        val settings = SettingsStore(context)
        val service = EventService(
            repository,
            ReminderSync(repository, scheduler, settings),
            scheduler,
            settings,
        )

        val first = LocalDate.now(zone)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
        val skipped = first.plusWeeks(1)
        val event = EventEntity(
            id = "weekly",
            title = "每周例会",
            allDay = false,
            startAt = first.atTime(15, 0).atZone(zone).toInstant().toEpochMilli(),
            endAt = first.atTime(16, 0).atZone(zone).toInstant().toEpochMilli(),
            timeZone = zone.id,
            createdAt = 0,
            updatedAt = 0,
        )
        val rule = RecurrenceRuleEntity(
            id = "rule",
            eventId = event.id,
            frequency = RecurrenceRuleEntity.FREQUENCY_WEEKLY,
            byDay = "WE",
        )
        runBlocking {
            service.save(EventSaveBundle(event, rule, listOf(ReminderPlanner.create(event, 15))))
        }

        try {
            compose.setContent {
                EventDetailScreen(
                    service = service,
                    eventId = event.id,
                    occurrenceDate = skipped,
                    onBack = {},
                    onEdit = {},
                    onDeleted = {},
                )
            }
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText("每周例会").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText("删除").performClick()
            compose.waitForIdle()

            compose.onNodeWithText("删哪一次？").assertExists()
            // 说明文字里也有「只删这一次」四个字，所以这里挑带点击动作的那个（按钮）。
            val skipButton = compose.onNode(
                hasText("只删这一次", substring = true) and hasClickAction(),
            )
            skipButton.assertExists()
            compose.onNodeWithText("删除整条重复行程").assertExists()

            skipButton.performClick()
            awaitCondition("例外落库") {
                runBlocking { repository.loadRules().single().exceptionDates } != null
            }

            val saved = runBlocking { repository.loadRules().single() }
            assertTrue(
                "例外没写进规则：${saved.exceptionDates}",
                saved.exceptionDates.orEmpty().contains(skipped.toEpochDay().toString()),
            )
        } finally {
            runBlocking { repository.remindersOf(event.id).forEach { scheduler.cancel(it.id) } }
            database.close()
        }
    }

    /** 点完是协程里落库的，要等一小会儿。 */
    private fun awaitCondition(what: String, check: () -> Boolean) {
        repeat(50) {
            if (check()) return
            Thread.sleep(100)
        }
        throw AssertionError("等不到：$what")
    }
}
