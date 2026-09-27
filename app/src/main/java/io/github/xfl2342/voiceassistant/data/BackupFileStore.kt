package io.github.xfl2342.voiceassistant.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 备份文件的读写。
 *
 * 写：和改进意见那份镜像不同，备份**每次都是新的一份**，文件名带导出时间，不覆盖以前导出的。
 * 理由很直白——备份的价值就是留着某个时间点的样子，覆盖掉旧的反倒把这份价值弄没了。
 *
 * 默认落在手机的「下载」目录（连上电脑就能看到），也可以由用户用「另存为」自己挑位置，
 * 放到网盘或 U 盘上。写文件走 MediaStore 与系统的文件选择框，不需要申请存储权限。
 *
 * 读：只认系统文件选择框给的那一个 uri，不加任何自作主张的路径猜测。
 */
class BackupFileStore(context: Context) {

    private val appContext = context.applicationContext

    /** 写一份到下载目录，返回实际写出的文件名。 */
    suspend fun writeToDownloads(text: String, now: Long = System.currentTimeMillis()): String =
        withContext(Dispatchers.IO) {
            val resolver = appContext.contentResolver
            val name = fileName(now)
            val uri = resolver.insert(collection(), newFileValues(name))
                ?: throw IllegalStateException("下载目录写不进去")
            if (!writeInto(resolver, uri, text)) {
                // 建好了却写不进去，别在下载目录里留一个空文件。
                runCatching { resolver.delete(uri, null, null) }
                throw IllegalStateException("下载目录写不进去")
            }
            // 同一秒里连点两次，系统可能把后来那份改成「xxx (1).json」；
            // 返回实际的名字，界面提示才和下载目录里看到的一致。
            displayNameOf(resolver, uri) ?: name
        }

    /** 写到用户自己挑的位置（另存为），位置由系统文件选择框给出。 */
    suspend fun writeTo(uri: Uri, text: String) {
        withContext(Dispatchers.IO) {
            val stream = appContext.contentResolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("这个位置写不进去")
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    /** 读回用户挑的那份备份。读不动时抛异常，由上层翻译成一句人话。 */
    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("这个文件打不开")
        stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun writeInto(resolver: ContentResolver, uri: Uri, text: String): Boolean =
        runCatching {
            val stream = resolver.openOutputStream(uri, "wt") ?: return@runCatching false
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            true
        }.getOrDefault(false)

    private fun newFileValues(name: String): ContentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/")
    }

    private fun displayNameOf(resolver: ContentResolver, uri: Uri): String? =
        runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()

    private fun collection(): Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI

    companion object {

        /** 手机上的文件名前缀。 */
        const val FILE_PREFIX = "生活助理-备份-"

        const val FILE_SUFFIX = ".json"

        /** 导出的文件名，也用作「另存为」对话框里的建议名字。 */
        fun fileName(now: Long = System.currentTimeMillis()): String =
            FILE_PREFIX +
                STAMP.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())) +
                FILE_SUFFIX

        private const val MIME_TYPE = "application/json"

        private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
    }
}
