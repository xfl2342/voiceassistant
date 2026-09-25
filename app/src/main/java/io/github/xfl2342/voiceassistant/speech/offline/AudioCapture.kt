package io.github.xfl2342.voiceassistant.speech.offline

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 麦克风录音，输出识别引擎要的浮点采样。
 *
 * 参数固定为 16 kHz、单声道、16 位——这是语音识别的标准输入格式，
 * 所有识别引擎都按这个规格吃数据，不要随意改动。
 */
class AudioCapture {

    private val sampleRate = WavFileReader.SAMPLE_RATE

    /** 预分配缓冲区，够录 [MAX_SECONDS] 秒。录满就自动停止，避免内存无限增长。 */
    private val buffer = ShortArray(sampleRate * MAX_SECONDS)
    private var sampleCount = 0

    private var recorder: AudioRecord? = null
    private var captureJob: Job? = null

    @Volatile
    private var running = false

    val isCapturing: Boolean get() = running

    /**
     * 开始录音。返回 false 表示麦克风不可用（比如没有权限，或者设备被占用）。
     */
    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope): Boolean {
        if (running) return false

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) {
            Log.e(TAG, "麦克风不支持 16 kHz 单声道录音")
            return false
        }

        val audioRecord = try {
            AudioRecord(
                // VOICE_RECOGNITION 这个音源会关掉一部分针对通话的音频处理，更适合识别。
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "创建录音器失败", t)
            return false
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "录音器初始化失败")
            audioRecord.release()
            return false
        }

        recorder = audioRecord
        sampleCount = 0
        running = true

        try {
            audioRecord.startRecording()
        } catch (t: Throwable) {
            Log.e(TAG, "开始录音失败", t)
            running = false
            audioRecord.release()
            recorder = null
            return false
        }

        captureJob = scope.launch(Dispatchers.IO) {
            val chunk = ShortArray(minBufferSize)
            while (isActive && running && sampleCount < buffer.size) {
                val read = try {
                    audioRecord.read(chunk, 0, chunk.size)
                } catch (t: Throwable) {
                    Log.e(TAG, "读取音频失败", t)
                    -1
                }
                if (read <= 0) break

                val copyCount = minOf(read, buffer.size - sampleCount)
                System.arraycopy(chunk, 0, buffer, sampleCount, copyCount)
                sampleCount += copyCount
            }
        }
        return true
    }

    /** 结束录音并返回采集到的采样。 */
    suspend fun stop(): FloatArray {
        val audioRecord = recorder ?: return FloatArray(0)
        running = false
        recorder = null

        // 先停录音，阻塞中的 read 会立刻返回，采集循环随之退出。
        runCatching { audioRecord.stop() }
        captureJob?.join()
        captureJob = null
        audioRecord.release()

        return FloatArray(sampleCount) { buffer[it] / 32768f }
    }

    /** 放弃本次录音。 */
    fun cancel() {
        running = false
        val audioRecord = recorder ?: return
        recorder = null
        runCatching { audioRecord.stop() }
        runCatching { audioRecord.release() }
        captureJob = null
    }

    private companion object {
        const val TAG = "AudioCapture"

        /** 单次录音上限，防止一直按着不放。 */
        const val MAX_SECONDS = 120
    }
}
