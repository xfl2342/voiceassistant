package io.github.xfl2342.voiceassistant.speech.offline

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 语音模型的下载与安装。
 *
 * 模型有 74 MB，不适合塞进安装包（会让包体积翻倍，也没法单独更新），
 * 所以做成下载：只需要下一次，装好之后识别完全离线，再也不联网。
 */
class ModelInstaller(private val context: Context) {

    fun targetDir(): File = OfflineSpeechRecognizer.defaultModelDir(context)

    fun isInstalled(): Boolean = OfflineSpeechRecognizer.isModelPresent(targetDir())

    /**
     * 下载并安装模型。
     *
     * [onProgress] 会不断回报已下载与总字节数，用来显示进度。
     */
    suspend fun install(onProgress: (downloaded: Long, total: Long) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val archive = File(context.cacheDir, ARCHIVE_NAME)
                try {
                    download(archive, onProgress)
                    extract(archive)
                } finally {
                    // 压缩包用完就删，74 MB 没必要留在手机上。
                    archive.delete()
                }
                check(isInstalled()) { "解压后没有找到模型文件，可能下载不完整，请重试" }
                Log.i(TAG, "语音模型安装完成：${targetDir().absolutePath}")
                Unit
            }
        }

    /**
     * 从用户选中的文件安装。
     *
     * 这条路不依赖网络：模型不常更新，配合「下载一次」的思路，
     * 更应该保证的是「无论如何都装得上」——比如从电脑下载后传进手机。
     */
    suspend fun installFromFile(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val archive = File(context.cacheDir, ARCHIVE_NAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                archive.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) }
            } ?: error("读取不到所选文件，请换一个试试")

            try {
                extract(archive)
            } finally {
                archive.delete()
            }
            check(isInstalled()) { "这个文件里没有找到模型，请确认选的是模型压缩包（.tar.bz2）" }
            Log.i(TAG, "已从文件安装语音模型")
            Unit
        }
    }

    /** 删除已安装的模型，用于重装或释放空间。 */
    fun uninstall() {
        targetDir().deleteRecursively()
    }

    private fun download(archive: File, onProgress: (Long, Long) -> Unit) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                // GitHub 的下载地址会跳转，必须允许跟随。
                instanceFollowRedirects = true
            }
            val code = connection.responseCode
            check(code in 200..299) { "下载失败：服务器返回 HTTP $code" }

            val total = connection.contentLengthLong.coerceAtLeast(0L)
            connection.inputStream.use { input ->
                archive.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
        } catch (t: Throwable) {
            // 失败原因一定要落到日志里，否则线上出问题只能靠猜。
            Log.e(TAG, "下载模型失败", t)
            throw t
        } finally {
            connection?.disconnect()
        }
    }

    /** 从压缩包里只取出需要的两个文件。 */
    private fun extract(archive: File) {
        val target = targetDir()
        if (!target.isDirectory && !target.mkdirs()) {
            error("无法创建模型目录：${target.absolutePath}")
        }
        val wanted = setOf(OfflineSpeechRecognizer.MODEL_FILE, OfflineSpeechRecognizer.TOKENS_FILE)

        BZip2CompressorInputStream(archive.inputStream().buffered(BUFFER_SIZE)).use { compressed ->
            TarArchiveInputStream(compressed).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val name = entry.name.substringAfterLast('/')
                    if (!entry.isDirectory && name in wanted) {
                        // 先写到临时文件再改名：中途失败也不会留下半个模型文件让应用误判。
                        val temp = File(target, "$name.part")
                        temp.outputStream().use { output -> tar.copyTo(output, BUFFER_SIZE) }
                        val destination = File(target, name)
                        destination.delete()
                        if (!temp.renameTo(destination)) error("写入模型文件失败：$name")
                    }
                    entry = tar.nextEntry
                }
            }
        }
    }

    private companion object {
        const val TAG = "ModelInstaller"
        const val BUFFER_SIZE = 64 * 1024
        const val ARCHIVE_NAME = "voice_model.tar.bz2"

        /** 官方发布页的模型压缩包，约 74 MB。 */
        const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                "sherpa-onnx-paraformer-zh-small-2024-03-09.tar.bz2"
    }
}
