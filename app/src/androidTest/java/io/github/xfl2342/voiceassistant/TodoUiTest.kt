package io.github.xfl2342.voiceassistant

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xfl2342.voiceassistant.ai.EventDraft
import io.github.xfl2342.voiceassistant.data.EventRepository
import io.github.xfl2342.voiceassistant.data.SettingsStore
import io.github.xfl2342.voiceassistant.data.db.AppDatabase
import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.domain.EventDraftMapper
import io.github.xfl2342.voiceassistant.domain.EventService
import io.github.xfl2342.voiceassistant.domain.ReminderSync
import io.github.xfl2342.voiceassistant.reminder.ReminderScheduler
import io.github.xfl2342.voiceassistant.ui.edit.EventEditScreen
import io.github.xfl2342.voiceassistant.ui.list.EventListScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * 待办在界面上的样子：全部日程里单列一区，编辑页能选「不设时间」并存成待办。
 *
 * 只能在真机上跑，而且**要先把「后台弹出界面」放开**，否则测试用的空 Activity 起不来，
 * 测试会一直卡着等而不是报错（同一个坑记在 `FeedbackExportTest` 里）。装一次应用就会
 * 重置这个权限，所以每次都得先：
 *
 *     adb shell appops set io.github.xfl2342.voiceassistant 10021 allow
 *     gradlew :app:connectedDebugAndroidTest \
 *         -Pandroid.testInstrumentationRunnerArguments.class=io.github.xfl2342.voiceassistant.TodoUiTest
 *
 * 默认标了 @Ignore：没放开那个权限时它会一直卡着等，挂在 `connectedAndroidTest` 里
 * 会把整个测试集拖死。要跑就把下面这行注解去掉。
 */
@Ignore("需要先 adb shell appops set 放开「后台弹出界面」，否则会卡住整个测试集")
@RunWith(AndroidJUnit4::class)
class TodoUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun newDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    private fun serviceOf(repository: EventRepository): EventService {
        val settings = SettingsStore(context)
        val scheduler = ReminderScheduler(context)
        return EventService(
            repository,
            ReminderSync(repository, scheduler, settings),
            scheduler,
            settings,
        )
    }

    private fun todoBundle() = EventDraftMapper.toBundle(
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

    @Test
    fun 待办在全部日程里单独一区() {
        val database = newDatabase()
        val repository = EventRepository(database)
        runBlocking {
            serviceOf(repository).save(todoBundle())
            val day = LocalDate.now(zone).plusDays(3)
            repository.save(
                EventEntity(
                    id = "timed",
                    title = "项目评审会",
                    allDay = false,
                    startAt = day.atTime(15, 0).atZone(zone).toInstant().toEpochMilli(),
                    endAt = day.atTime(16, 0).atZone(zone).toInstant().toEpochMilli(),
                    timeZone = zone.id,
                    createdAt = 1,
                    updatedAt = 1,
                ),
            )
        }

        try {
            compose.setContent {
                EventListScreen(repository = repository, onBack = {}, onEventClick = {})
            }

            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText("待办（1）").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("待办（1）").assertExists()
            compose.onNodeWithText("买牛奶").assertExists()
            compose.onNodeWithText("不定时间").assertExists()
            // 有时间的行程照旧落在「即将到来」里，没被待办带偏。
            compose.onNodeWithText("项目评审会").assertExists()
        } finally {
            database.close()
        }
    }

    @Test
    fun 编辑页选不设时间时存成待办() {
        val database = newDatabase()
        val repository = EventRepository(database)

        compose.setContent {
            EventEditScreen(
                service = serviceOf(repository),
                settingsStore = SettingsStore(context),
                eventId = null,
                initialDate = null,
                onBack = {},
                onSaved = {},
            )
        }

        compose.onNodeWithText("新建行程").assertExists()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("买牛奶")
        compose.onNodeWithText("不设时间").performClick()
        compose.waitForIdle()

        // 选了「不设时间」之后：标题改口叫待办，开始与结束两栏收起来。
        compose.onNodeWithText("新建待办").assertExists()
        compose.onNodeWithText("开始").assertDoesNotExist()

        compose.onNodeWithText("保存").performClick()
        awaitEvent(repository)

        val saved = runBlocking { repository.loadEvents() }.single()
        assertTrue(saved.isTodo)
        assertEquals(null, saved.startAt)
        assertEquals(null, saved.startEpochDay)
        assertEquals("买牛奶", saved.title)
        database.close()
    }

    @Test
    fun 待办打勾后挪到已完成() {
        val database = newDatabase()
        val repository = EventRepository(database)
        runBlocking { serviceOf(repository).save(todoBundle()) }

        try {
            compose.setContent {
                EventListScreen(repository = repository, onBack = {}, onEventClick = {})
            }
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText("待办（1）").fetchSemanticsNodes().isNotEmpty()
            }

            // 行里那个「完成」按钮：点一下，这一条就该挪到「已完成」去。
            compose.onNodeWithText("完成").performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText("已完成（1）").fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText("已完成（1）").assertExists()
            compose.onNodeWithText("撤销").assertExists()
            compose.onNodeWithText("买牛奶").assertExists()
        } finally {
            database.close()
        }
    }

    /** 保存是协程里做的，落库要等一小会儿。 */
    private fun awaitEvent(repository: EventRepository) {
        repeat(50) {
            if (runBlocking { repository.loadEvents() }.isNotEmpty()) return
            Thread.sleep(100)
        }
        throw AssertionError("等不到待办落库")
    }
}
