package io.github.xfl2342.voiceassistant

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xfl2342.voiceassistant.data.BackupFileStore
import io.github.xfl2342.voiceassistant.data.BackupFormatException
import io.github.xfl2342.voiceassistant.data.BackupJson
import io.github.xfl2342.voiceassistant.data.BackupRepository
import io.github.xfl2342.voiceassistant.data.BackupSnapshot
import io.github.xfl2342.voiceassistant.data.EventRepository
import io.github.xfl2342.voiceassistant.data.SettingsStore
import io.github.xfl2342.voiceassistant.data.db.AppDatabase
import io.github.xfl2342.voiceassistant.data.db.EventEntity
import io.github.xfl2342.voiceassistant.data.db.FeedbackEntity
import io.github.xfl2342.voiceassistant.data.db.RecurrenceRuleEntity
import io.github.xfl2342.voiceassistant.data.db.ReminderEntity
import io.github.xfl2342.voiceassistant.domain.BackupService
import io.github.xfl2342.voiceassistant.domain.ReminderSync
import io.github.xfl2342.voiceassistant.reminder.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 备份的恢复方向：读文件、算账、落库、把提醒重新排上。
 *
 * 这一段只能在真机上跑：写下载目录、读下载目录都要经过 MediaStore；提醒那一步还要真的
 * 去注册系统闹钟。跑法：`gradlew :app:connectedDebugAndroidTest`
 *
 * 数据落在内存数据库里，不碰手机上真实的那份行程；导出的文件写进下载目录，跑完自行删掉；
 * 注册过的闹钟也在收尾时逐个取消，免得自检的通知过几天真的弹出来。
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** 导出一份、删掉行程、再拿那份备份恢复，行程和提醒都该回来。 */
    @Test
    fun deletedEventComesBackFromItsBackup() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scheduler = ReminderScheduler(context)
        val eventRepository = EventRepository(database)
        val service = backupService(database, eventRepository, scheduler)

        val eventId = "自检恢复-${System.currentTimeMillis()}"
        var fileName: String? = null

        try {
            // 三天后的一场会，提前 15 分钟提醒。
            val startAt = System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000L
            eventRepository.save(
                event = EventEntity(
                    id = eventId,
                    title = "自检行程（恢复用）",
                    allDay = false,
                    startAt = startAt,
                    endAt = startAt + 60 * 60 * 1000L,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
                rule = RecurrenceRuleEntity(
                    id = "$eventId-rule",
                    eventId = eventId,
                    frequency = RecurrenceRuleEntity.FREQUENCY_WEEKLY,
                    byDay = "TU",
                ),
                reminders = listOf(
                    ReminderEntity(
                        id = "$eventId-reminder",
                        eventId = eventId,
                        triggerType = ReminderEntity.TYPE_BEFORE,
                        minutesBefore = 15,
                        triggerAt = startAt - 15 * 60 * 1000L,
                    ),
                ),
            )

            val exported = service.exportToDownloads()
            assertNotNull("备份没写出来", exported)
            fileName = exported!!.fileName

            // 手滑删了，手里只剩这份备份。
            eventRepository.delete(eventId)
            assertTrue(eventRepository.loadDetail(eventId)!!.event.deleted)

            // 恢复：先看账目，再落库。
            val uri = downloadUri(context, fileName!!)
            assertNotNull("下载目录里没有找到 $fileName", uri)

            val plan = service.prepareImport(uri!!)
            assertEquals("删掉的那条应该算「找回」", 1, plan.summary.restoredEvents)
            assertEquals(0, plan.summary.newEvents)

            val summary = service.restore(plan)
            assertEquals(1, summary.restoredEvents)
            assertTrue(summary.changedAny)

            val restored = eventRepository.loadDetail(eventId)
            assertNotNull("行程没有被恢复", restored)
            assertFalse("恢复之后不该还带着删除标记", restored!!.event.deleted)
            assertEquals("自检行程（恢复用）", restored.event.title)
            assertEquals(
                "重复规则也该跟着回来",
                RecurrenceRuleEntity.FREQUENCY_WEEKLY,
                restored.rule?.frequency,
            )

            // 提醒被重新算过：提前量照旧，触发时刻在未来，而不是照抄备份里的老时刻。
            assertTrue("恢复后应该有提醒", restored.reminders.isNotEmpty())
            assertTrue(restored.reminders.all { it.minutesBefore == 15 })
            assertTrue(restored.reminders.all { it.triggerAt > System.currentTimeMillis() })

            // 同一份备份再来一次：不算新增、不算找回，也不该变成两条行程。
            val again = service.prepareImport(uri!!)
            assertEquals("手机上已经有了，第二次应该整条跳过", 1, again.summary.skippedEvents)
            assertFalse("什么都没要动，就别再写一遍", again.summary.changedAny)
            assertEquals(1, eventRepository.loadEvents().size)
        } finally {
            // 收尾：撤掉自检注册的闹钟，关掉内存库，删掉导出的文件。
            runCatching { eventRepository.remindersOf(eventId) }
                .getOrDefault(emptyList())
                .forEach { scheduler.cancel(it.id) }
            runCatching { database.close() }
            fileName?.let { deleteDownload(context, it) }
        }
    }

    /** 导出什么、读回来就该是什么，包括引号换行这些容易在转义上翻车的内容。 */
    @Test
    fun readingWhatWasWrittenKeepsEveryField() {
        val original = sampleSnapshot()

        val parsed = BackupJson.parse(BackupJson.render(original, now = 1_774_000_000_000L))

        assertEquals(original, parsed.snapshot)
        assertNotNull("备份时间应该被读回来", parsed.exportedAtText)
    }

    /** 拿错文件时要说人话，而不是把一个解析异常丢给用户看。 */
    @Test
    fun otherJsonFilesAreRejectedWithAReadableMessage() {
        val wrongFile = assertThrows(BackupFormatException::class.java) {
            BackupJson.parse("""{"hello":"world"}""")
        }
        assertTrue(wrongFile.message!!.contains("不是生活助理"))

        val broken = assertThrows(BackupFormatException::class.java) {
            BackupJson.parse("{这不是 JSON")
        }
        assertTrue(broken.message!!.contains("读不出 JSON"))
    }

    /** 将来格式升级了，旧版本应用要明确说「读不了」，而不是照着老字段硬读一半。 */
    @Test
    fun newerFormatVersionIsRefused() {
        val fromFuture = """{"format":"voiceassistant-backup","formatVersion":2,"events":[]}"""

        val error = assertThrows(BackupFormatException::class.java) {
            BackupJson.parse(fromFuture)
        }

        assertTrue(error.message!!.contains("更新版本"))
    }

    private fun backupService(
        database: AppDatabase,
        eventRepository: EventRepository,
        scheduler: ReminderScheduler,
    ): BackupService {
        val reminderSync = ReminderSync(eventRepository, scheduler, SettingsStore(context))
        val repository = BackupRepository(database, BackupFileStore(context))
        return BackupService(repository, reminderSync)
    }

    private fun sampleSnapshot() = BackupSnapshot(
        events = listOf(
            EventEntity(
                id = "e1",
                title = "会\"议\\记录\n第二行",
                allDay = false,
                startAt = 1_774_000_000_000L,
                endAt = 1_774_003_600_000L,
                location = "三楼会议室",
                notes = "带上上周的\"数据\"",
                urgency = EventEntity.URGENCY_LOW,
                rawText = "明天下午三点开会",
                createdAt = 1_773_000_000_000L,
                updatedAt = 1_773_000_000_000L,
            ),
            EventEntity(
                id = "e2",
                title = "休一天",
                allDay = true,
                startEpochDay = 20_687L,
                endEpochDay = 20_688L,
                createdAt = 1_773_000_000_000L,
                updatedAt = 1_773_000_000_000L,
            ),
        ),
        rules = listOf(
            RecurrenceRuleEntity(
                id = "r1",
                eventId = "e2",
                frequency = RecurrenceRuleEntity.FREQUENCY_WEEKLY,
                interval = 2,
                byDay = "MO,WE",
                endType = RecurrenceRuleEntity.END_TYPE_ON_DATE,
                endEpochDay = 21_000L,
            ),
        ),
        reminders = listOf(
            ReminderEntity(
                id = "m1",
                eventId = "e1",
                triggerType = ReminderEntity.TYPE_BEFORE,
                minutesBefore = 15,
                triggerAt = 1_773_999_100_000L,
            ),
            ReminderEntity(
                id = "m2",
                eventId = "e1",
                triggerType = ReminderEntity.TYPE_AT,
                minutesBefore = null,
                triggerAt = 1_774_000_000_000L,
            ),
        ),
        feedback = listOf(
            FeedbackEntity(
                id = "f1",
                content = "日历首页想看到天气\n第二行",
                createdAt = 1_773_000_000_000L,
                updatedAt = 1_773_000_000_000L,
                done = true,
            ),
        ),
    )

    private fun downloadUri(context: Context, name: String): Uri? =
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(name),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                Uri.withAppendedPath(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    cursor.getLong(0).toString(),
                )
            } else {
                null
            }
        }

    private fun deleteDownload(context: Context, name: String) {
        val resolver: ContentResolver = context.contentResolver
        val uri = downloadUri(context, name) ?: return
        runCatching { resolver.delete(uri, null, null) }
    }
}
