package dev.rup1n13.whisperdictate.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Port of the desktop energy-gate silence detection (desktop/src/whisper_dictate/audio.py).
 * A frame counts as speech only when its RMS clears an adaptive gate above the
 * quietest frame heard so far. Constants calibrated on desktop; phone mics use
 * the VOICE_RECOGNITION source to keep processing comparable.
 */
class SilenceDetector(silenceSeconds: Int, private val frameMs: Int = FRAME_MS) {

    sealed interface Verdict {
        data object Continue : Verdict
        data object AutoStop : Verdict
        data object NoSpeechAbort : Verdict
    }

    private val silenceLimit = silenceSeconds * 1000 / frameMs
    private val initialLimit = silenceLimit * 2

    private var noiseFloor = Double.MAX_VALUE
    private var streak = 0
    private var trailingSilence = 0
    var voicedFrames = 0
        private set
    var heardSpeech = false
        private set
    var lastRms = 0.0
        private set

    fun feed(frame: ShortArray): Verdict {
        var sum = 0.0
        for (s in frame) sum += s.toDouble() * s.toDouble()
        val rms = sqrt(sum / frame.size)
        lastRms = rms
        noiseFloor = min(noiseFloor, rms)
        val gate = max(RMS_FLOOR, noiseFloor * RMS_NOISE_MULTIPLIER)

        if (rms >= gate) {
            streak++
            voicedFrames++
            if (streak >= CONSECUTIVE_SPEECH_FRAMES) {
                heardSpeech = true
                trailingSilence = 0
            }
        } else {
            streak = 0
            trailingSilence++
            if (heardSpeech && trailingSilence >= silenceLimit) return Verdict.AutoStop
            if (!heardSpeech && trailingSilence >= initialLimit) return Verdict.NoSpeechAbort
        }
        return Verdict.Continue
    }

    /** A recording with less voiced audio than this is an accidental trigger. */
    fun hasEnoughSpeech(): Boolean = heardSpeech && voicedFrames >= MIN_VOICED_FRAMES

    companion object {
        const val FRAME_MS = 30
        const val RMS_FLOOR = 250.0
        const val RMS_NOISE_MULTIPLIER = 3.0
        const val CONSECUTIVE_SPEECH_FRAMES = 2
        const val MIN_VOICED_FRAMES = 10 // 300 ms
    }
}
