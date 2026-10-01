package io.github.xfl2342.voiceassistant.reminder

import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri

/**
 * 闹钟该用什么声音、怎么震。
 *
 * 刻意用系统里**闹钟**那一路的声音，而不是通知音：提醒要盖得住、听得见，
 * 而且用户在系统设置里换掉闹钟铃声时，这里也跟着换。
 */
object AlarmSound {

    /** 走闹钟音量通道：静音模式下的行为也跟闹钟一致。 */
    val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** 系统默认闹钟铃声；机型没配的话退回通知音，总比彻底没声音好。 */
    val URI: Uri?
        get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    /** 跟着铃声一起震：响 0.6 秒、停 0.6 秒，配合 repeat 一直循环。 */
    val VIBRATION_PATTERN: LongArray = longArrayOf(0, 600, 600)
}
