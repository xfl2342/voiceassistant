package io.github.xfl2342.voiceassistant.speech

/**
 * 语音识别过程中产生的事件。
 *
 * 系统 [android.speech.SpeechRecognizer] 的回调比较零散，统一收敛成这几个事件，
 * 上层界面只需要处理这一组状态。
 */
sealed interface SpeechEvent {

    /** 已开始录音，用户可以说话了。 */
    data object Ready : SpeechEvent

    /** 检测到说话结束，正在等待识别结果。 */
    data object EndOfSpeech : SpeechEvent

    /** 中间结果，之后还会更新。 */
    data class Partial(val text: String) : SpeechEvent

    /** 最终结果，按可能性从高到低排列。 */
    data class Final(val alternatives: List<String>) : SpeechEvent

    /** 识别失败，带上错误码与可读的说明。 */
    data class Failed(
        val errorCode: Int,
        val errorName: String,
        val hint: String,
    ) : SpeechEvent
}
