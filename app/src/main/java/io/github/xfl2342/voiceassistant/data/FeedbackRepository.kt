package io.github.xfl2342.voiceassistant.data

import io.github.xfl2342.voiceassistant.data.db.AppDatabase
import io.github.xfl2342.voiceassistant.data.db.FeedbackEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * 改进意见的统一入口。
 *
 * 数据库是正本，下载目录里的那份 Markdown 是给电脑看的副本。每次改动都顺手重写
 * 副本，于是「下次连上电脑就能读到最新的」不依赖用户记得点导出——记性不可靠，
 * 自动重写要可靠得多。
 */
class FeedbackRepository(
    private val database: AppDatabase,
    private val exporter: FeedbackExporter,
) {

    private val dao = database.feedbackDao()

    fun observeAll(): Flow<List<FeedbackEntity>> = dao.observeAll()

    suspend fun loadAll(): List<FeedbackEntity> = dao.loadAll()

    /** 记一条新意见；内容全是空白时不记，返回 false。 */
    suspend fun add(content: String): Boolean {
        val text = content.trim()
        if (text.isEmpty()) return false
        val now = System.currentTimeMillis()
        dao.upsert(
            FeedbackEntity(
                id = UUID.randomUUID().toString(),
                content = text,
                createdAt = now,
                updatedAt = now,
            ),
        )
        mirror()
        return true
    }

    /** 改一条已记下的意见；改成空白视为无效，直接忽略。 */
    suspend fun update(id: String, content: String) {
        val text = content.trim()
        if (text.isEmpty()) return
        val old = dao.loadAll().firstOrNull { it.id == id } ?: return
        dao.upsert(old.copy(content = text, updatedAt = System.currentTimeMillis()))
        mirror()
    }

    suspend fun setDone(id: String, done: Boolean) {
        dao.setDone(id, done, System.currentTimeMillis())
        mirror()
    }

    suspend fun delete(id: String) {
        dao.delete(id)
        mirror()
    }

    /**
     * 手动重写一份到下载目录，返回写出的文件名；失败返回 null。
     *
     * 自动重写已经覆盖了绝大多数情况，这个按钮是留给「怀疑文件没更新」时用的。
     */
    suspend fun exportToDownloads(): String? =
        runCatching { exporter.write(dao.loadAll()) }.getOrNull()

    /**
     * 自动重写副本。
     *
     * 失败只当没发生：意见已经存进数据库了，不会丢；下载目录写不进去（存储满、
     * 被系统拦住）不该连累界面，用户可以稍后手动导出。
     */
    private suspend fun mirror() {
        runCatching { exporter.write(dao.loadAll()) }
    }
}
