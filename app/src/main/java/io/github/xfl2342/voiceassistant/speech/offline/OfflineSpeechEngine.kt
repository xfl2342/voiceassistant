package io.github.xfl2342.voiceassistant.speech.offline

import android.content.Context
import android.util.Log
import io.github.xfl2342.voiceassistant.speech.SpeechEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 录音 + 离线识别的完整链路。
 *
 * 与系统识别相比，这里没有任何时长限制：按住说多久都行，松开后本地解码，
 * 几秒的音频通常几十毫秒就出结果。
 */
class OfflineSpeechEngine(
    context: Context,
    private val scope: CoroutineScope,
) {

    private val recognizer = OfflineSpeechRecognizer(
        context = context,
        modelDir = OfflineSpeechRecognizer.defaultModelDir(context),
    )
    private val capture = AudioCapture()
    private var onEvent: ((SpeechEvent) -> Unit)? = null

    /** 提前把模型加载进内存，避免第一次说话时多等一两秒。 */
    fun warmUp() {
        if (!recognizer.isModelReady()) return
        scope.launch(Dispatchers.Default) { recognizer.warmUp() }
    }

    fun start(onEvent: (SpeechEvent) -> Unit) {
        this.onEvent = onEvent

        val problem = recognizer.modelStatus()
        if (problem != null) {
            Log.w(TAG, "模型不可用：$problem")
            onEvent(
                SpeechEvent.Failed(
                    NO_ENGINE,
                    "MODEL_MISSING",
                    "还没有安装语音模型。请到「设置 → 语音模型」里下载一次（约 74 MB），装好后就不再需要联网。",
                )
            )
            return
        }

        if (!capture.start(scope)) {
            onEvent(
                SpeechEvent.Failed(
                    NO_ENGINE,
                    "MIC_UNAVAILABLE",
                    "麦克风不可用：可能是没有录音权限，或已被其他应用占用",
                )
            )
            return
        }

        // 对上层来说，这里就相当于「可以说话了」；实际的识别在松手之后。
        onEvent(SpeechEvent.Ready)
    }

    fun stop() {
        val callback = onEvent
        scope.launch {
            val samples = capture.stop()
            if (samples.isEmpty()) {
                callback?.invoke(
                    SpeechEvent.Failed(NO_ENGINE, "NO_AUDIO", "没有录到声音，请靠近麦克风再试一次")
                )
                return@launch
            }

            callback?.invoke(SpeechEvent.EndOfSpeech)

            val text = withContext(Dispatchers.Default) {
                try {
                    recognizer.recognize(samples)
                } catch (t: Throwable) {
                    Log.e(TAG, "识别失败", t)
                    ""
                }
            }

            callback?.invoke(SpeechEvent.Final(listOf(text)))
        }
    }

    fun cancel() {
        capture.cancel()
    }

    fun release() {
        capture.cancel()
        recognizer.release()
        onEvent = null
    }

    private companion object {
        const val TAG = "OfflineEngine"
        const val NO_ENGINE = -1
    }
}
