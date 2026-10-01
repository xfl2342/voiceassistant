package io.github.xfl2342.voiceassistant

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xfl2342.voiceassistant.ai.EventDraft
import io.github.xfl2342.voiceassistant.data.EventRepository
import io.github.xfl2342.voiceassistant.data.SettingsStore
import io.github.xfl2342.voiceassistant.data.db.AppDatabase
import io.github.xfl2342.voiceassistant.domain.EventDraftMapper
import io.github.xfl2342.voiceassistant.domain.EventService
import io.github.xfl2342.voiceassistant.domain.RecurrenceExpander
import io.github.xfl2342.voiceassistant.domain.ReminderSync
import io.github.xfl2342.voiceassistant.reminder.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * 不设时间的待办：从模型返回的 json 到落库、再到日历展开。
 *
 * 只能在真机上跑（`org.json` 在电脑上的单元测试里是空壳，而模型返回的正是 json；
 * 内存数据库也走真机的 SQLite）。界面那一半在 `TodoUiTest` 里，那个要先放开
 * 「后台弹出界面」才能跑。
 *
 *     gradlew :app:connectedDebugAndroidTest
 *
 * 数据全在内存数据库里，不碰手机上真实的那份行程。
 */
@RunWith(AndroidJUnit4::class)
class TodoFlowTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun newDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    private fun serviceOf(repository: EventRepository): EventService {
        val settings = SettingsStore(context)
        val scheduler = ReminderScheduler(context)
        return EventService(repository, ReminderSync(repository, scheduler, settings), scheduler, settings)
    }

    @Test
    fun 模型说是待办时解析成待办而不是缺时间的行程() {
        val todoJson = """
            {
              "title": "买牛奶",
              "start": null,
              "end": null,
              "all_day": false,
              "kind": "todo",
              "location": "",
              "reminders": [],
              "urgency": "low",
              "confidence": 0.9,
              "missing_fields": []
            }
        """.trimIndent()

        val todo = EventDraft.parse(todoJson).getOrThrow()

        assertTrue(todo.isTodo)
        // 待办不该被报成「开始时间缺失」——它本来就没有时间。
        assertTrue(todo.warnings.isEmpty())

        // 老提示词里没有 kind 这一项，那种返回要继续当成普通行程。
        val legacy = EventDraft.parse(todoJson.replace("\"kind\": \"todo\",", "")).getOrThrow()
        assertTrue(!legacy.isTodo)
    }

    @Test
    fun 存下来的待办不占日历格子() = runBlocking {
        val database = newDatabase()
        val repository = EventRepository(database)
        try {
            val draft = EventDraft.parse(
                """
                {"title":"买电池","start":null,"end":null,"all_day":false,
                 "kind":"todo","location":"","reminders":[],"urgency":"normal",
                 "confidence":0.9,"missing_fields":[]}
                """.trimIndent(),
            ).getOrThrow()
            val bundle = EventDraftMapper.toBundle(draft, rawText = "记个待办买电池", zone = zone)
                .getOrThrow()
            serviceOf(repository).save(bundle)

            assertEquals(1, repository.loadEvents().size)
            assertTrue(repository.loadEvents().single().isTodo)

            // 往前看两年也一天都不该出现：日历、桌面小组件、冲突检测都读这个展开结果。
            val occurrences = RecurrenceExpander.expand(
                events = repository.loadEvents(),
                rules = repository.loadRules(),
                from = LocalDate.of(2026, 1, 1),
                to = LocalDate.of(2027, 12, 31),
                zone = zone,
                weekStartDay = DayOfWeek.MONDAY,
            )
            assertTrue(occurrences.isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun 打勾与取消打勾都会落库() = runBlocking {
        val database = newDatabase()
        val repository = EventRepository(database)
        try {
            val bundle = EventDraftMapper.toBundle(
                EventDraft.parse(
                    """
                    {"title":"买牛奶","start":null,"end":null,"all_day":false,
                     "kind":"todo","location":"","reminders":[],"urgency":"normal",
                     "confidence":0.9,"missing_fields":[]}
                    """.trimIndent(),
                ).getOrThrow(),
                rawText = null,
                zone = zone,
            ).getOrThrow()
            val service = serviceOf(repository)
            service.save(bundle)

            // 刚记下时没打勾
            assertTrue(!repository.loadEvents().single().done)

            service.setTodoDone(bundle.event.id, true)
            assertTrue(repository.loadEvents().single().done)

            service.setTodoDone(bundle.event.id, false)
            assertTrue(!repository.loadEvents().single().done)
        } finally {
            database.close()
        }
    }

}
