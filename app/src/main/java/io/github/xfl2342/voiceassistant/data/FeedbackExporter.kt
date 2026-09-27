package io.github.xfl2342.voiceassistant.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import io.github.xfl2342.voiceassistant.data.db.FeedbackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 把改进意见写成一份 Markdown，放进手机的「下载」目录。
 *
 * 为什么落在下载目录：手机用数据线连上电脑后，这个目录在 Windows 资源管理器里
 * 直接可见（内部存储 → Download），也可以用 adb 拉走。于是「手机上随手记、
 * 连上电脑就能读」这件事不需要任何同步服务，也不需要联网。
 *
 * 写文件走 MediaStore（而不是直接拼 /sdcard 路径），这样 Android 10 以后不用申请
 * 存储权限，卸载重装也不会把用户的下载目录搞得一团乱。
 */
class FeedbackExporter(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 写一份到下载目录，返回实际写出的文件名。
     *
     * 同名文件已存在时直接覆盖它，不在下载目录里堆「(1)(2)(3)」；确实写不进去
     * （比如那份文件是别的应用建的）才另建一份，此时返回的是新文件名。
     */
    suspend fun write(
        items: List<FeedbackEntity>,
        now: Long = System.currentTimeMillis(),
    ): String = withContext(Dispatchers.IO) {
        val text = render(items, now)
        val resolver = appContext.contentResolver
        // 依次尝试：上次写过的那份 → 下载目录里的同名文件 → 新建一份。
        // 必须是「试一个、成了就停」，不能先把候选全建出来，否则每写一次就多一个文件。
        savedUri()?.let { uri ->
            if (writeInto(resolver, uri, text)) return@withContext finish(resolver, uri)
        }
        findByName(resolver)?.let { uri ->
            if (writeInto(resolver, uri, text)) return@withContext finish(resolver, uri)
        }
        val created = resolver.insert(collection(), newFileValues())
            ?: throw IllegalStateException("下载目录写不进去")
        if (writeInto(resolver, created, text)) {
            return@withContext finish(resolver, created)
        }
        // 建好了却写不进去，别在下载目录里留一个空文件。
        runCatching { resolver.delete(created, null, null) }
        throw IllegalStateException("下载目录写不进去")
    }

    /** 把意见渲染成 Markdown。抽出来是为了能单独测，也方便日后换格式。 */
    fun render(items: List<FeedbackEntity>, now: Long): String {
        val zone = ZoneId.systemDefault()
        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val pending = items.filterNot { it.done }
        val done = items.filter { it.done }

        return buildString {
            appendLine("# 生活助理 · 改进意见")
            appendLine()
            appendLine(
                "> 手机上随手记的想法，攒着一起改。最后写出：" +
                    stamp.format(Instant.ofEpochMilli(now).atZone(zone)),
            )
            appendLine("> 待处理 ${pending.size} 条 · 已完成 ${done.size} 条")
            appendLine()
            appendSection("待处理", pending, zone, stamp, checked = false)
            if (done.isNotEmpty()) {
                appendSection("已完成", done, zone, stamp, checked = true)
            }
        }
    }

    private fun StringBuilder.appendSection(
        title: String,
        items: List<FeedbackEntity>,
        zone: ZoneId,
        stamp: DateTimeFormatter,
        checked: Boolean,
    ) {
        if (items.isEmpty()) return
        appendLine("## $title")
        appendLine()
        items.forEach { appendItem(it, zone, stamp, checked) }
        appendLine()
    }

    private fun StringBuilder.appendItem(
        item: FeedbackEntity,
        zone: ZoneId,
        stamp: DateTimeFormatter,
        checked: Boolean,
    ) {
        val time = stamp.format(Instant.ofEpochMilli(item.createdAt).atZone(zone))
        val lines = item.content.trim().lines().ifEmpty { listOf("") }
        append("- [").append(if (checked) "x" else " ").append("] ")
        append(time).append(" — ").append(lines.first().trim())
        // 正文里的换行缩进两格，保证在 Markdown 里仍然属于同一个列表项。
        lines.drop(1).forEach { append('\n').append("  ").append(it.trim()) }
        append('\n')
    }

    private fun finish(resolver: ContentResolver, uri: Uri): String {
        prefs.edit().putString(KEY_URI, uri.toString()).apply()
        return displayNameOf(resolver, uri) ?: FILE_NAME
    }

    private fun writeInto(resolver: ContentResolver, uri: Uri, text: String): Boolean =
        runCatching {
            val stream = resolver.openOutputStream(uri, "wt") ?: return@runCatching false
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            true
        }.getOrDefault(false)

    private fun newFileValues(): ContentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
        put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/")
    }

    private fun findByName(resolver: ContentResolver): Uri? =
        runCatching {
            resolver.query(
                collection(),
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(FILE_NAME),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    Uri.withAppendedPath(collection(), cursor.getLong(0).toString())
                } else {
                    null
                }
            }
        }.getOrNull()

    private fun displayNameOf(resolver: ContentResolver, uri: Uri): String? =
        runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()

    private fun savedUri(): Uri? =
        prefs.getString(KEY_URI, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    private fun collection(): Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI

    companion object {

        /** 手机上的文件名。改这里要同步改 tools/pull-feedback.ps1。 */
        const val FILE_NAME = "生活助理-改进意见.md"

        private const val MIME_TYPE = "text/markdown"
        private const val PREFS_NAME = "feedback_mirror"
        private const val KEY_URI = "document_uri"
    }
}
