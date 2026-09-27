package io.github.xfl2342.voiceassistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        EventEntity::class,
        RecurrenceRuleEntity::class,
        ReminderEntity::class,
        FeedbackEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    abstract fun recurrenceRuleDao(): RecurrenceRuleDao

    abstract fun reminderDao(): ReminderDao

    abstract fun feedbackDao(): FeedbackDao

    companion object {

        private const val DATABASE_NAME = "voice_assistant.db"

        /**
         * v1 → v2：加一张「改进意见」表。
         *
         * 手机上已经有在用的旧版本，升级安装时不能丢行程数据，所以写正经迁移，
         * 不用破坏式重建（fallbackToDestructiveMigration）。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `feedback` (" +
                        "`id` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`done` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        /**
         * v2 → v3：行程加一列「急迫程度」，日历按它上色。
         *
         * 已有的行程一律按「常规」处理——以前没记过这件事，与其瞎猜，
         * 不如用中性颜色，用户想区分时再自己改。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `events` ADD COLUMN `urgency` TEXT NOT NULL DEFAULT 'normal'",
                )
            }
        }

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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
