package io.github.xfl2342.voiceassistant.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.SpeechRecognizer

/**
 * Phase 0 用的环境探测：这台设备上到底有没有可用的语音识别服务。
 *
 * 之所以单独拿出来，是因为真机上「识别不可用」有两种完全不同的原因：
 * 一种是设备根本没有识别服务，另一种是有服务但调用失败。先把这两者区分开，
 * 后面排查问题会快很多。
 */
object SpeechDiagnostics {

    data class Report(
        val isRecognitionAvailable: Boolean,
        val services: List<String>,
        val deviceModel: String,
        val androidRelease: String,
        val sdkInt: Int,
    )

    /** 系统语音识别服务的 action，与 [android.speech.RecognitionService.SERVICE_INTERFACE] 一致。 */
    private const val ACTION_RECOGNITION_SERVICE = "android.speech.RecognitionService"

    fun collect(context: Context): Report = Report(
        isRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context),
        services = (
            queryServices(context, ACTION_RECOGNITION_SERVICE) +
                queryServices(context, android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            ).distinct(),
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
        androidRelease = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
    )

    private fun queryServices(context: Context, action: String): List<String> = try {
        @Suppress("DEPRECATION")
        context.packageManager
            .queryIntentServices(Intent(action), 0)
            .mapNotNull { info ->
                info.serviceInfo?.let { "${it.packageName}/${it.name}" }
            }
    } catch (t: Throwable) {
        emptyList()
    }
}
