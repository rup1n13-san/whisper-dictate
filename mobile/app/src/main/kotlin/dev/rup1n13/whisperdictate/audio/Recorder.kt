package dev.rup1n13.whisperdictate.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed interface RecordResult {
    data class Done(val file: File, val durationMs: Long) : RecordResult
    data object NoSpeech : RecordResult
    data object Cancelled : RecordResult
    data class Failed(val message: String) : RecordResult
}

class Recorder {

    @Volatile
    var stopRequested = false

    /**
     * Records 16 kHz mono PCM16 until silence auto-stop, max duration, stop request,
     * or coroutine cancellation. Writes a WAV file. onLevel receives the frame RMS
     * on the recording thread (~33/s) for the level meter.
     */
    @SuppressLint("MissingPermission") // caller checks RECORD_AUDIO before starting
    suspend fun record(
        out: File,
        silenceSeconds: Int,
        maxDurationSeconds: Int,
        onLevel: (Double) -> Unit,
    ): RecordResult = withContext(Dispatchers.IO) {
        stopRequested = false
        val frameSamples = SAMPLE_RATE * SilenceDetector.FRAME_MS / 1000
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return@withContext RecordResult.Failed("Microphone unavailable")
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, frameSamples * 4),
            )
        } catch (e: IllegalArgumentException) {
            return@withContext RecordResult.Failed("Cannot open microphone: ${e.message}")
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return@withContext RecordResult.Failed("Cannot open microphone")
        }

        val detector = SilenceDetector(silenceSeconds)
        val pcm = ByteArrayOutputStream()
        val frame = ShortArray(frameSamples)
        val maxFrames = maxDurationSeconds * 1000 / SilenceDetector.FRAME_MS
        var frames = 0
        var verdict: SilenceDetector.Verdict = SilenceDetector.Verdict.Continue

        try {
            record.startRecording()
            while (isActive && !stopRequested && frames < maxFrames) {
                val read = record.read(frame, 0, frame.size)
                if (read <= 0) return@withContext RecordResult.Failed("Microphone read error ($read)")
                pcm.write(toBytes(frame, read))
                frames++
                verdict = detector.feed(if (read == frame.size) frame else frame.copyOf(read))
                onLevel(detector.lastRms)
                if (verdict != SilenceDetector.Verdict.Continue) break
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }

        when {
            !isActive -> RecordResult.Cancelled
            verdict == SilenceDetector.Verdict.NoSpeechAbort -> RecordResult.NoSpeech
            !detector.hasEnoughSpeech() -> RecordResult.NoSpeech
            else -> {
                writeWav(out, pcm.toByteArray())
                RecordResult.Done(out, frames.toLong() * SilenceDetector.FRAME_MS)
            }
        }
    }

    private fun toBytes(frame: ShortArray, count: Int): ByteArray {
        val buf = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) buf.putShort(frame[i])
        return buf.array()
    }

    private fun writeWav(out: File, pcm: ByteArray) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        val byteRate = SAMPLE_RATE * 2
        header.put("RIFF".toByteArray())
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)               // PCM chunk size
        header.putShort(1)              // PCM format
        header.putShort(1)              // mono
        header.putInt(SAMPLE_RATE)
        header.putInt(byteRate)
        header.putShort(2)              // block align
        header.putShort(16)             // bits per sample
        header.put("data".toByteArray())
        header.putInt(pcm.size)
        out.outputStream().use {
            it.write(header.array())
            it.write(pcm)
        }
    }

    companion object {
        const val SAMPLE_RATE = 16000
    }
}
