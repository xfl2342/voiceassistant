package io.github.xfl2342.voiceassistant.speech.offline

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 读取 16 位单声道 WAV 文件，转成识别引擎要的浮点采样。
 *
 * 只支持最简单的一种格式（PCM、16 位、单声道）——这正是语音识别的标准输入，
 * 也是我们自己录音时会用的格式。遇到别的格式直接报错，不猜。
 */
object WavFileReader {

    /** 识别的标准采样率。 */
    const val SAMPLE_RATE = 16_000

    fun read(file: File): FloatArray {
        val bytes = file.readBytes()
        require(bytes.size > 44) { "音频文件太小，不是有效的 WAV" }

        var offset = 12 // 跳过 RIFF 头
        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0
        var dataStart = -1
        var dataLength = 0

        // 依次读取各个数据块，直到找到真正的音频数据块。
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = readInt(bytes, offset + 4)
            val chunkStart = offset + 8

            when (chunkId) {
                "fmt " -> {
                    channels = readShort(bytes, chunkStart + 2)
                    sampleRate = readInt(bytes, chunkStart + 4)
                    bitsPerSample = readShort(bytes, chunkStart + 14)
                }
                "data" -> {
                    dataStart = chunkStart
                    dataLength = minOf(chunkSize, bytes.size - chunkStart)
                }
            }

            if (chunkSize <= 0) break
            offset = chunkStart + chunkSize + (chunkSize % 2)
        }

        require(dataStart >= 0) { "WAV 文件里没有找到音频数据" }
        require(channels == 1) { "只支持单声道音频，当前是 $channels 声道" }
        require(bitsPerSample == 16) { "只支持 16 位音频，当前是 $bitsPerSample 位" }

        val sampleCount = dataLength / 2
        val samples = FloatArray(sampleCount)
        val buffer = ByteBuffer.wrap(bytes, dataStart, dataLength).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            samples[i] = buffer.short / 32768f
        }
        return samples
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun readShort(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
}
