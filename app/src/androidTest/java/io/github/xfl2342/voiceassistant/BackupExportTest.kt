package io.github.xfl2342.voiceassistant

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.xfl2342.voiceassistant.data.BackupFileStore
import io.github.xfl2342.voiceassistant.data.BackupJson
import io.github.xfl2342.voiceassistant.data.BackupSnapshot
import io.github.xfl2342.voiceassistant.data.db.EventEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机自检：导出一份备份，确认它真的落到下载目录，并且能当一个 JSON 读回来。
 *
 * 这一段没法用单元测试覆盖 —— 写下载目录要经过 MediaStore，只有真机跑得出来。
 * 跑法：gradlew :app:connectedDebugAndroidTest
 *
 * 顺带把「文件本身是合法 JSON」也验了：单元测试能盯住渲染出来的字符串，但只有一个
 * 真正的解析器才能证明它没写坏 —— 多一个逗号、少一个引号都会在这里现形。
 */
@RunWith(AndroidJUnit4::class)
class BackupExportTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun backupIsWrittenToDownloadsAsJson() = runBlocking {
        val repository = AppGraph(context).backupRepository

        val result = repository.exportToDownloads()
        assertNotNull("备份没写出来", result)
        val name = result!!.fileName
        assertTrue("文件名应该带 .json", name.endsWith(BackupFileStore.FILE_SUFFIX))

        try {
            val text = readDownloadedText(context, name)
            assertNotNull("下载目录里没有找到 $name", text)

            val root = JSONObject(text!!)
            assertEquals(BackupJson.FORMAT, root.getString("format"))
            assertEquals(BackupJson.FORMAT_VERSION, root.getInt("formatVersion"))
            assertEquals(result.counts.events, root.getJSONObject("counts").getInt("events"))
            assertEquals(result.counts.feedback, root.getJSONObject("counts").getInt("feedback"))
            assertEquals(result.counts.events, root.getJSONArray("events").length())
            assertEquals(result.counts.reminders, root.getJSONArray("reminders").length())

            // 每一条都带着 id，日后写导入时靠它认人。
            val events = root.getJSONArray("events")
            for (index in 0 until events.length()) {
                assertTrue(events.getJSONObject(index).getString("id").isNotBlank())
            }
        } finally {
            // 自检不留痕：删掉这份文件，下载目录保持原样。
            deleteDownload(context, name)
        }
    }

    /** 转义过的字符要能原样读回来，这才说明文件是给程序读的，不只是看着像 JSON。 */
    @Test
    fun escapedTextSurvivesRoundTrip() {
        val tricky = "引号\"反斜杠\\换行\n制表\t结束"
        val text = BackupJson.render(
            BackupSnapshot(
                events = listOf(
                    EventEntity(
                        id = "round-trip",
                        title = tricky,
                        allDay = false,
                        startAt = 1_774_000_000_000L,
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                ),
                rules = emptyList(),
                reminders = emptyList(),
                feedback = emptyList(),
            ),
        )

        val readBack = JSONObject(text).getJSONArray("events").getJSONObject(0).getString("title")
        assertEquals(tricky, readBack)
    }

    /** 通过 MediaStore 找到下载目录里的那份文件，再按自己的权限读回来。 */
    private fun readDownloadedText(context: Context, name: String): String? {
        val uri = findDownload(context.contentResolver, name) ?: return null
        return context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun deleteDownload(context: Context, name: String) {
        val uri = findDownload(context.contentResolver, name) ?: return
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    private fun findDownload(resolver: ContentResolver, name: String): Uri? =
        resolver.query(
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
}
