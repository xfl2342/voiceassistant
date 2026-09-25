package io.github.xfl2342.voiceassistant.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * 对系统语音识别接口的薄封装。
 *
 * 注意：[SpeechRecognizer] 的创建与调用都必须发生在主线程，本类不做线程切换，
 * 请只在主线程（例如 Compose 的事件回调）中使用。
 *
 * 后续如果接入云 ASR 或离线引擎，替换掉这个类的实现即可，上层界面不用动。
 */
class SpeechRecognitionEngine(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var onEvent: ((SpeechEvent) -> Unit)? = null
    private var lastIntent: Intent? = null
    /** 当前用的是离线识别还是联网识别。 */
    private var usingOnDevice = false
    /** 离线识别试过不行之后，就不再试了。 */
    private var forceNetwork = false

    /** 是否正在监听。 */
    var isListening: Boolean = false
        private set

    /**
     * 开始一次识别。识别结果通过 [onEvent] 回调返回。
     *
     * @param languageTag BCP-47 语言标签，默认简体中文。
     */
    fun start(languageTag: String = DEFAULT_LANGUAGE, onEvent: (SpeechEvent) -> Unit) {
        this.onEvent = onEvent

        val instance = ensureRecognizer()
        if (instance == null) {
            onEvent(
                SpeechEvent.Failed(
                    errorCode = NO_ENGINE,
                    errorName = "NO_ENGINE",
                    hint = "这台设备上没有可用的语音识别服务，需要改用云 ASR 或本地识别",
                )
            )
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }

        lastIntent = intent
        isListening = true
        instance.startListening(intent)
    }

    /** 主动结束说话，等待结果返回。 */
    fun stop() {
        if (!isListening) return
        isListening = false
        recognizer?.stopListening()
    }

    /** 放弃本次识别，不等待结果。 */
    fun cancel() {
        isListening = false
        recognizer?.cancel()
    }

    /** 释放资源，界面销毁时调用。 */
    fun release() {
        isListening = false
        onEvent = null
        lastIntent = null
        usingOnDevice = false
        recognizer?.let {
            it.cancel()
            it.destroy()
        }
        recognizer = null
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return null

        val preferOnDevice = !forceNetwork && isOnDeviceRecognitionAvailable()
        return try {
            val instance = if (preferOnDevice) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            usingOnDevice = preferOnDevice
            // 这行日志用来确认到底走的离线还是联网：排查超时问题时一眼就能看出。
            Log.i(TAG, "使用离线识别 = $preferOnDevice")
            instance.setRecognitionListener(Listener())
            recognizer = instance
            instance
        } catch (t: Throwable) {
            // 部分 ROM 上创建识别器本身就可能抛异常，这里降级处理。
            null
        }
    }

    private fun emit(event: SpeechEvent) {
        onEvent?.invoke(event)
    }

    /**
     * 设备是否支持离线识别。
     *
     * 有本地语音模型的机型可以完全脱离网络工作；没有的话识别就必须联网，
     * 网络一抖动就报超时——这正是我们遇到的情况。
     */
    fun isOnDeviceRecognitionAvailable(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }
    } catch (t: Throwable) {
        false
    }

    private inner class Listener : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            emit(SpeechEvent.Ready)
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            emit(SpeechEvent.EndOfSpeech)
        }

        override fun onError(error: Int) {
            // 有些设备有离线识别框架，但没有装中文模型：这种情况直接换回联网识别重试，
            // 对用户来说就是「多等一会儿」，而不是弹一个看不懂的错误。
            if (usingOnDevice && isOnDeviceUnsupported(error)) {
                Log.w(TAG, "离线识别不支持中文（code=$error），改用联网识别重试")
                forceNetwork = true
                val intent = lastIntent
                recognizer?.destroy()
                recognizer = null
                if (intent != null) {
                    val fallback = ensureRecognizer()
                    if (fallback != null) {
                        fallback.startListening(intent)
                        return
                    }
                }
            }

            isListening = false
            Log.w(TAG, "识别失败 code=$error (${errorName(error)})")
            emit(SpeechEvent.Failed(error, errorName(error), errorHint(error)))
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val alternatives = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            emit(SpeechEvent.Final(alternatives))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotEmpty()) emit(SpeechEvent.Partial(text))
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    companion object {

        private const val TAG = "SpeechEngine"

        const val DEFAULT_LANGUAGE = "zh-CN"

        /** 自定义错误码：设备上没有可用的识别服务。 */
        const val NO_ENGINE = -1

        /** 离线识别不支持中文时，需要换回联网识别。 */
        private fun isOnDeviceUnsupported(error: Int): Boolean = when (error) {
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            -> true
            else -> false
        }

        fun errorName(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "ERROR_TOO_MANY_REQUESTS"
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "ERROR_SERVER_DISCONNECTED"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "ERROR_LANGUAGE_NOT_SUPPORTED"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "ERROR_LANGUAGE_UNAVAILABLE"
            else -> "UNKNOWN_ERROR"
        }

        fun errorHint(code: Int): String = when (code) {
            // 系统识别其实是云端服务（小米的），网络链路不稳时会超时，
            // 提示里直接给出可操作的做法，比只说「需要联网」有用。
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "识别超时。系统自带的识别服务处理不了太长的语句（大约 5 秒上限），" +
                    "请把话分成两句说；如果短句也超时，再检查网络"
            SpeechRecognizer.ERROR_NETWORK ->
                "网络不通。识别用的是小米的云端服务，请检查网络后重试"
            SpeechRecognizer.ERROR_AUDIO -> "录音出错，可能被其他应用占用了麦克风"
            SpeechRecognizer.ERROR_SERVER -> "识别服务端返回错误"
            SpeechRecognizer.ERROR_CLIENT -> "客户端调用异常，常见原因是缺少录音权限"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听到声音，请靠近麦克风再试一次"
            SpeechRecognizer.ERROR_NO_MATCH -> "听到了声音但没能识别出内容"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器正忙，稍等片刻再试"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限，请在系统设置中允许"
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "请求过于频繁，稍后再试"
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "与识别服务的连接中断"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "识别服务不支持该语言"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "该语言当前不可用，可能需要下载语言包"
            NO_ENGINE -> "这台设备上没有可用的语音识别服务"
            else -> "未收录的错误码，请记录错误码 $code 便于排查"
        }
    }
}
