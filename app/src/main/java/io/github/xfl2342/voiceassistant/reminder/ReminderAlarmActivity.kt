package io.github.xfl2342.voiceassistant.reminder

import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.xfl2342.voiceassistant.ui.theme.VoiceAssistantTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 到点时自动弹出来的那一屏（全屏提醒）。
 *
 * 它一直响、一直震，直到用户选「稍后 10 分钟」或「完成」；**两分钟没人理就自己停下** ——
 * 闹钟响个没完比不响还烦，停下来之后通知还留在通知栏里，该做的事不会丢。
 *
 * 走的是系统「闹钟」那一路的铃声与音量，所以静音模式下的表现也和闹钟一致；
 * 系统没给全屏提醒权限时这一屏不会自动弹出，但通知照响，两个按钮也在。
 */
class ReminderAlarmActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val eventId = intent.getStringExtra(ReminderReceiver.EXTRA_EVENT_ID).orEmpty()
        val title = intent.getStringExtra(ReminderReceiver.EXTRA_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "行程提醒"

        startRinging()
        lifecycleScope.launch {
            delay(AUTO_STOP_MILLIS)
            stopRinging()
            finish()
        }

        setContent {
            VoiceAssistantTheme {
                AlarmScreen(
                    title = title,
                    onSnooze = {
                        NotificationHelper.cancel(this, title)
                        ReminderScheduler(this).snooze(
                            eventId = eventId,
                            title = title,
                            minutes = NotificationHelper.SNOOZE_MINUTES,
                        )
                        finish()
                    },
                    onDone = {
                        NotificationHelper.cancel(this, title)
                        finish()
                    },
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 屏幕不看了就别响：留在通知栏里的那条还在，声音不该追着人跑。
        stopRinging()
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    private fun startRinging() {
        if (ringtone == null) {
            ringtone = AlarmSound.URI?.let { uri ->
                RingtoneManager.getRingtone(this, uri)?.apply {
                    audioAttributes = AlarmSound.ATTRIBUTES
                    isLooping = true
                    play()
                }
            }
        }
        if (vibrator == null) {
            vibrator = (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
            vibrator?.vibrate(VibrationEffect.createWaveform(AlarmSound.VIBRATION_PATTERN, 0))
        }
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    companion object {

        /** 没人理的闹钟响多久：两分钟。 */
        private const val AUTO_STOP_MILLIS = 2 * 60 * 1000L

        fun intent(context: Context, eventId: String, title: String): Intent =
            Intent(context, ReminderAlarmActivity::class.java).apply {
                // 从通知/闹钟里起来，得自己开一个任务。
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
                putExtra(ReminderReceiver.EXTRA_TITLE, title)
            }
    }
}

@Composable
private fun AlarmScreen(
    title: String,
    onSnooze: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "行程提醒",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = LocalTime.now().format(timeFormatter),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onSnooze,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("稍后 ${NotificationHelper.SNOOZE_MINUTES} 分钟")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("完成")
        }
    }
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
