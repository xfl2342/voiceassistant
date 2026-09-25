package io.github.xfl2342.voiceassistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        EventEntity::class,
        RecurrenceRuleEntity::class,
        ReminderEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    abstract fun recurrenceRuleDao(): RecurrenceRuleDao

    abstract fun reminderDao(): ReminderDao

    companion object {

        private const val DATABASE_NAME = "voice_assistant.db"

        /**
         * 全局唯一实例。
         *
         * 后台广播（比如开机后重新注册提醒）和界面可能在不同入口各自取数据库，
         * 共用同一个实例可以避免同时打开两次同一份数据库文件。
         */
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
