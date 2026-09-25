package io.github.xfl2342.voiceassistant.speech.offline

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File

/**
 * 离线（设备端）语音识别。
 *
 * 与系统识别最大的区别：**完全不联网**，音频不出手机，也没有服务端时长限制。
 * 模型放在应用的私有目录里，第一次使用时下载或由用户手动放置。
 */
class OfflineSpeechRecognizer(
    private val context: Context,
    private val modelDir: File,
) {

    private var recognizer: OfflineRecognizer? = null

    /** 模型文件是否齐全、并且真的读得到。 */
    fun isModelReady(): Boolean = modelStatus() == null

    /**
     * 检查模型是否可用，返回第一个问题；一切正常时返回 null。
     *
     * 这个检查不能省：原生库在文件读不到时会**直接终止进程**，属于无法拦截的崩溃。
     * 提前自己检查一遍，就能把它变成一条能看懂的提示。
     */
    fun modelStatus(): String? {
        val model = File(modelDir, MODEL_FILE)
        val tokens = File(modelDir, TOKENS_FILE)
        return when {
            !model.isFile -> "缺少模型文件：${model.absolutePath}"
            !tokens.isFile -> "缺少词表文件：${tokens.absolutePath}"
            !canRead(model) -> "模型文件读不出来：${model.absolutePath}"
            !canRead(tokens) -> "词表文件读不出来：${tokens.absolutePath}"
            else -> null
        }
    }

    /** 模型文件大小（MB），用于在界面上确认模型确实是完整的那一份。 */
    fun modelSizeMb(): Int =
        ((File(modelDir, MODEL_FILE).length() + File(modelDir, TOKENS_FILE).length()) / 1024 / 1024).toInt()

    private fun canRead(file: File): Boolean = try {
        file.inputStream().use { it.read() }
        true
    } catch (t: Throwable) {
        Log.w(TAG, "读不到文件：${file.absolutePath}", t)
        false
    }

    /** 模型目录，出错时用来提示用户放在哪里。 */
    fun modelPath(): String = modelDir.absolutePath

    /**
     * 识别一段音频。
     *
     * 这是耗时操作，必须放在后台线程执行；几秒钟的音频在手机上通常不到一秒就能出结果。
     */
    fun recognize(samples: FloatArray): String {
        // 文件读不到时直接返回，不能让原生库去读——那会直接把进程干掉。
        if (modelStatus() != null) return ""

        val engine = ensureRecognizer() ?: return ""
        val stream = engine.createStream()
        return try {
            stream.acceptWaveform(samples, WavFileReader.SAMPLE_RATE)
            engine.decode(stream)
            engine.getResult(stream).text
        } finally {
            stream.release()
        }
    }

    fun release() {
        recognizer?.release()
        recognizer = null
    }

    /** 提前加载模型，第一次识别时就不用等模型初始化。 */
    fun warmUp() {
        ensureRecognizer()
    }

    private fun ensureRecognizer(): OfflineRecognizer? {
        recognizer?.let { return it }
        if (!isModelReady()) return null

        return try {
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = WavFileReader.SAMPLE_RATE,
                    featureDim = FEATURE_DIM,
                ),
                modelConfig = OfflineModelConfig(
                    paraformer = OfflineParaformerModelConfig(
                        model = File(modelDir, MODEL_FILE).absolutePath,
                    ),
                    tokens = File(modelDir, TOKENS_FILE).absolutePath,
                    numThreads = THREADS,
                    modelType = MODEL_TYPE,
                ),
            )
            // 这里的 assetManager 必须传 null。
            //
            // 该库的实现是：只要传了 AssetManager，读取模型文件时就只会去 APK 的
            // assets 里找（AAssetManager_open），绝对路径一律找不到，然后直接终止进程。
            // 我们的模型放在应用私有目录，所以必须传 null 让它走文件系统。
            OfflineRecognizer(assetManager = null, config = config).also {
                Log.i(TAG, "离线识别模型加载完成：${modelDir.absolutePath}")
                recognizer = it
            }
        } catch (t: Throwable) {
            Log.e(TAG, "离线识别模型加载失败", t)
            null
        }
    }

    companion object {

        private const val TAG = "OfflineAsr"

        /** 模型文件名，与下载的模型包保持一致。 */
        const val MODEL_FILE = "model.int8.onnx"
        const val TOKENS_FILE = "tokens.txt"

        private const val FEATURE_DIM = 80

        /** 线程数：手机上是 2 个线程比较稳，再多收益有限还费电。 */
        private const val THREADS = 2

        private const val MODEL_TYPE = "paraformer"

        /** 模型在应用私有目录下的位置。 */
        /**
         * 模型的存放位置：应用内部私有目录。
         *
         * 刻意不用外部存储（Android/data）。实测发现外部存储目录受文件属主影响，
         * 用调试通道推进去的文件应用自己反而读不到；内部私有目录没有这个问题，
         * 也不需要任何存储权限。
         */
        fun defaultModelDir(context: Context): File = File(context.filesDir, "sherpa")
    }
}
