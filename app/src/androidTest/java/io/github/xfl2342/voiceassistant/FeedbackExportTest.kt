package io.github.xfl2342.voiceassistant

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机自检：记一条意见，确认它真的落到了下载目录，并且内容能读回来。
 *
 * 这一段没法用单元测试覆盖 —— 写下载目录要经过 MediaStore，只有真机跑得出来。
 * 跑法：gradlew :app:connectedDebugAndroidTest
 *
 * 这里只测数据与文件，不测界面。界面那一层试过用 Compose 测试框架来跑，结论是
 * 在这台机器上不划算：小米 / 澎湃默认禁止应用「后台弹出界面」，测试用的空 Activity
 * 起不来，测试会一直卡着等而不是报错；而重装一次应用就会把这个权限重置回去，
 * 于是每跑一次都得先手动放行：
 *
 *     adb shell appops set io.github.xfl2342.voiceassistant 10021 allow
 *
 * 界面本身的操作（输入、点「记录」、列表里出现新条目）已按上面的方式实测通过，
 * 只是没把它固化成会拖累别人的测试。
 */
@RunWith(AndroidJUnit4::class)
class FeedbackExportTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun recordedFeedbackIsWrittenToDownloads() = runBlocking {
        val text = "$TAG " + System.currentTimeMillis()
        val repository = AppGraph(context).feedbackRepository

        // 先清掉以前几次自检留下的条目，别让测试数据在手机上越攒越多。
        repository.loadAll()
            .filter { it.content.startsWith(TAG) }
            .forEach { repository.delete(it.id) }

        assertTrue(repository.add(text))

        val name = repository.exportToDownloads()
        assertNotNull(name)

        val content = readDownloadedText(context.contentResolver, name!!)
        assertNotNull("下载目录里没有找到 $name", content)
        assertTrue("文件里没有刚记下的内容：\n$content", content!!.contains(text))

        // 验完就删掉，测试不留痕迹。
        repository.loadAll()
            .filter { it.content == text }
            .forEach { repository.delete(it.id) }
    }

    /** 通过 MediaStore 找到下载目录里的那份文件，再按自己的权限读回来。 */
    private fun readDownloadedText(resolver: ContentResolver, name: String): String? {
        val uri: Uri = resolver.query(
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
        } ?: return null

        return resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private companion object {
        /** 自检条目统一用这个前缀，便于识别与清理。 */
        const val TAG = "自检意见"
    }
}
