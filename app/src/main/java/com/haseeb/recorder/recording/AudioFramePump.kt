package com.haseeb.recorder.recording

/** Collects non-blocking PCM reads without letting either source stall the encoder. */
internal class AudioFramePump(
    private val readMic: (ShortArray, Int, Int) -> Int,
    private val readSystem: (ShortArray, Int, Int) -> Int,
    private val submit: (ByteArray, Long) -> Boolean,
    private val frameSamples: Int = 1024,
    sampleRate: Int = 48_000
) {
    private val mic = ShortArray(frameSamples)
    private val system = ShortArray(frameSamples)
    private val pcm = ByteArray(frameSamples * 2)
    private val frameDurationUs = frameSamples * 1_000_000L / sampleRate
    private var micCount = 0
    private var systemCount = 0
    private var deadlineUs = 0L
    private var pending = false
    private var pendingPtsUs = 0L

    fun step(nowUs: Long, paused: Boolean, muted: Boolean) {
        if (deadlineUs == 0L) deadlineUs = nowUs + frameDurationUs
        if (paused) {
            // Keep draining the capture buffers during a pause; never replay paused audio.
            readMic(mic, 0, frameSamples)
            readSystem(system, 0, frameSamples)
            micCount = 0
            systemCount = 0
            pending = false
            deadlineUs = nowUs + frameDurationUs
            return
        }
        if (!pending) {
            micCount += readAvailable(readMic, mic, micCount)
            systemCount += readAvailable(readSystem, system, systemCount)
            if (nowUs < deadlineUs) return
            for (i in 0 until frameSamples) {
                val sample = if (muted) 0 else {
                    val m = if (i < micCount) mic[i].toInt() else 0
                    val s = if (i < systemCount) system[i].toInt() else 0
                    (m + s).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                }
                pcm[i * 2] = sample.toByte()
                pcm[i * 2 + 1] = (sample shr 8).toByte()
            }
            pendingPtsUs = nowUs - frameDurationUs
            pending = true
        }
        // Retain a frame while the codec is backpressured instead of dropping its PCM.
        if (submit(pcm, pendingPtsUs)) {
            pending = false
            micCount = 0
            systemCount = 0
            deadlineUs = maxOf(deadlineUs + frameDurationUs, nowUs)
        }
    }

    private fun readAvailable(
        read: (ShortArray, Int, Int) -> Int,
        buffer: ShortArray,
        count: Int
    ): Int {
        if (count == frameSamples) return 0
        return read(buffer, count, frameSamples - count).coerceIn(0, frameSamples - count)
    }
}
