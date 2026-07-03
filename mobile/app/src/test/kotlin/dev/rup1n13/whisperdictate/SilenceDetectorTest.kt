package dev.rup1n13.whisperdictate

import dev.rup1n13.whisperdictate.audio.SilenceDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceDetectorTest {

    private val frameSamples = 480 // 30 ms at 16 kHz

    private fun frame(amplitude: Int): ShortArray =
        ShortArray(frameSamples) { amplitude.toShort() }

    @Test
    fun `pure silence aborts after twice the silence window`() {
        val d = SilenceDetector(silenceSeconds = 7)
        val framesUntilAbort = 2 * 7 * 1000 / 30
        var verdict: SilenceDetector.Verdict = SilenceDetector.Verdict.Continue
        for (i in 0 until framesUntilAbort + 1) {
            verdict = d.feed(frame(50))
            if (verdict != SilenceDetector.Verdict.Continue) break
        }
        assertEquals(SilenceDetector.Verdict.NoSpeechAbort, verdict)
        assertFalse(d.hasEnoughSpeech())
    }

    @Test
    fun `speech then silence auto-stops after the silence window`() {
        val d = SilenceDetector(silenceSeconds = 5)
        d.feed(frame(100)) // lead-in ambient establishes the noise floor
        repeat(40) { assertEquals(SilenceDetector.Verdict.Continue, d.feed(frame(3000))) }
        assertTrue(d.heardSpeech)
        var verdict: SilenceDetector.Verdict = SilenceDetector.Verdict.Continue
        var silentFrames = 0
        while (verdict == SilenceDetector.Verdict.Continue && silentFrames < 1000) {
            verdict = d.feed(frame(50))
            silentFrames++
        }
        assertEquals(SilenceDetector.Verdict.AutoStop, verdict)
        assertEquals(5 * 1000 / 30, silentFrames)
        assertTrue(d.hasEnoughSpeech())
    }

    @Test
    fun `a single noise spike does not count as speech`() {
        val d = SilenceDetector(silenceSeconds = 7)
        d.feed(frame(100))   // establish noise floor
        d.feed(frame(4000))  // lone spike: streak of 1
        assertFalse(d.heardSpeech)
        d.feed(frame(100))
        d.feed(frame(4000))
        assertFalse(d.heardSpeech)
        // two consecutive loud frames = speech
        d.feed(frame(4000))
        d.feed(frame(4000))
        assertTrue(d.heardSpeech)
    }

    @Test
    fun `short blip is rejected by the min voiced guard`() {
        val d = SilenceDetector(silenceSeconds = 7)
        d.feed(frame(100))
        repeat(SilenceDetector.MIN_VOICED_FRAMES - 2) { d.feed(frame(4000)) }
        assertTrue(d.heardSpeech)
        assertFalse(d.hasEnoughSpeech())
    }
}
