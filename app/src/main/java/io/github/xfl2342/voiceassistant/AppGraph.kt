package io.github.xfl2342.voiceassistant

import android.content.Context
import io.github.xfl2342.voiceassistant.data.BackupFileStore
import io.github.xfl2342.voiceassistant.data.BackupRepository
import io.github.xfl2342.voiceassistant.data.EventRepository
import io.github.xfl2342.voiceassistant.data.FeedbackExporter
import io.github.xfl2342.voiceassistant.data.FeedbackRepository
import io.github.xfl2342.voiceassistant.data.SettingsStore
import io.github.xfl2342.voiceassistant.data.db.AppDatabase
import io.github.xfl2342.voiceassistant.domain.BackupService
import io.github.xfl2342.voiceassistant.domain.EventService
import io.github.xfl2342.voiceassistant.domain.ReminderSync
import io.github.xfl2342.voiceassistant.reminder.ReminderScheduler
import io.github.xfl2342.voiceassistant.widget.TodayWidget

/**
 * 依赖的集中创建处。
 *
 * 目前只有数据库和设置两项，手动组装就够了，不引入依赖注入框架；
 * 等对象多起来再考虑换 Hilt 或 Koin 也不迟。
 */
class AppGraph(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.get(appContext) }

    val eventRepository: EventRepository by lazy { EventRepository(database) }

    val feedbackRepository: FeedbackRepository by lazy {
        FeedbackRepository(database, FeedbackExporter(appContext))
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(database, BackupFileStore(appContext))
    }

    val backupService: BackupService by lazy {
        BackupService(backupRepository, reminderSync) { TodayWidget.refresh(appContext) }
    }

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }

    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(appContext) }

    val reminderSync: ReminderSync by lazy {
        ReminderSync(eventRepository, reminderScheduler, settingsStore)
    }

    val eventService: EventService by lazy {
        EventService(eventRepository, reminderSync, reminderScheduler, settingsStore) {
            TodayWidget.refresh(appContext)
        }
    }
}
