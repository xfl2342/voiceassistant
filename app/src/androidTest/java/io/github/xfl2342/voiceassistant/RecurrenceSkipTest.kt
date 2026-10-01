package io.github.xfl2342.voiceassistant

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
import io.github.xfl2342.voiceassistant.domain.RecurrenceExpander
import io.github.xfl2342.voiceassistant.domain.ReminderPlanner
import io.github.xfl2342.voiceassistant.domain.ReminderSync
import io.github.xfl2342.voiceassistant.reminder.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 跳过重复行程的某一次：落库之后那一天不再出现，它的提醒也跟着撤掉。
 *
 * 只能在真机上跑（要用真的 SQLite 与真的闹钟注册）。跑法：
 *
 *     gradlew :app:connectedDebugAndroidTest
 *
 * 数据全在内存数据库里；注册出去的闹钟在收尾时逐个取消，免得过几天真的弹出来。
 */
@RunWith(AndroidJUnit4::class)
class RecurrenceSkipTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun 跳过某一次之后那一天不再出现() = runBlocking {
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

        // 从今天起的下一个周三，每周三 15:00 的例会，提前 15 分钟提醒。
        val first = LocalDate.now(zone)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
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

        try {
            service.save(
                EventSaveBundle(event, rule, listOf(ReminderPlanner.create(event, 15))),
            )

            val skipped = first.plusWeeks(1)
            service.skipOccurrence(event.id, skipped)

            val occurrences = RecurrenceExpander.expand(
                events = repository.loadEvents(),
                rules = repository.loadRules(),
                from = first.minusDays(1),
                to = first.plusWeeks(4),
                zone = zone,
                weekStartDay = DayOfWeek.MONDAY,
            )
            assertTrue("被跳过的那次不该出现", occurrences.none { it.date == skipped })
            assertTrue("其余各次照旧", occurrences.any { it.date == first })

            // 提醒是按新的规则重算的：被跳过那天的闹钟不该还留着。
            val reminderDays = repository.remindersOf(event.id)
                .map { Instant.ofEpochMilli(it.triggerAt).atZone(zone).toLocalDate() }
            assertTrue("被跳过那天的提醒还在", skipped !in reminderDays)
            assertTrue("后面几次的提醒不该一起没掉", reminderDays.size >= 2)

            // 跳过记录本身要落库，不然改完规则就丢了。
            val savedRule = repository.loadRules().single()
            assertEquals(skipped.toEpochDay().toString(), savedRule.exceptionDates?.trim())
        } finally {
            repository.remindersOf("weekly").forEach { scheduler.cancel(it.id) }
            database.close()
        }
    }
}
