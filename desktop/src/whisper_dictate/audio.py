"""Microphone capture with VAD-based auto-stop. Output: 16 kHz mono FLAC."""

import queue
from pathlib import Path
from threading import Event

import numpy as np
import sounddevice as sd
import soundfile as sf
import webrtcvad

SAMPLE_RATE = 16000
FRAME_MS = 30  # webrtcvad accepts 10/20/30 ms frames
FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS // 1000
VAD_AGGRESSIVENESS = 3
# webrtcvad alone flags fan/mic hiss as speech; frames must also clear an
# energy gate relative to the quietest frame heard so far (the noise floor).
RMS_FLOOR = 250.0
RMS_NOISE_MULTIPLIER = 3.0
# A lone noisy frame must not reset the silence timer.
CONSECUTIVE_SPEECH_FRAMES = 2
# A recording with less total voiced audio than this is an accidental trigger.
MIN_VOICED_FRAMES = 10  # 300 ms


class MicError(Exception):
    pass


def _rms(frame: bytes) -> float:
    samples = np.frombuffer(frame, dtype=np.int16).astype(np.float64)
    return float(np.sqrt(np.mean(samples * samples)))


class NoSpeechError(Exception):
    pass


def record(path: Path, silence_seconds: float, max_duration_seconds: int, stop: Event) -> float:
    """Record until trailing silence, max duration, or `stop` is set.

    Returns the recorded duration in seconds. Raises NoSpeechError if no
    speech was ever detected (nothing worth sending to the API).
    """
    vad = webrtcvad.Vad(VAD_AGGRESSIVENESS)
    frames: queue.Queue[bytes] = queue.Queue()

    def callback(indata, _frames, _time, _status):
        frames.put(bytes(indata))

    silence_limit = int(silence_seconds * 1000 / FRAME_MS)
    # Grace period before anyone has spoken: don't hold the mic open forever
    initial_limit = silence_limit * 2
    max_frames = int(max_duration_seconds * 1000 / FRAME_MS)

    audio = bytearray()
    trailing_silence = 0
    heard_speech = False
    speech_streak = 0
    voiced_frames = 0
    noise_floor = None
    n = 0
    try:
        with sd.RawInputStream(
            samplerate=SAMPLE_RATE,
            channels=1,
            dtype="int16",
            blocksize=FRAME_SAMPLES,
            callback=callback,
        ):
            while n < max_frames and not stop.is_set():
                try:
                    frame = frames.get(timeout=1.0)
                except queue.Empty:
                    continue
                audio.extend(frame)
                n += 1
                rms = _rms(frame)
                noise_floor = rms if noise_floor is None else min(noise_floor, rms)
                gate = max(RMS_FLOOR, noise_floor * RMS_NOISE_MULTIPLIER)
                if rms >= gate and vad.is_speech(frame, SAMPLE_RATE):
                    speech_streak += 1
                    voiced_frames += 1
                    if speech_streak >= CONSECUTIVE_SPEECH_FRAMES:
                        heard_speech = True
                        trailing_silence = 0
                else:
                    speech_streak = 0
                    trailing_silence += 1
                    if heard_speech and trailing_silence >= silence_limit:
                        break
                    if not heard_speech and trailing_silence >= initial_limit:
                        raise NoSpeechError("No speech detected.")
    except sd.PortAudioError as exc:
        raise MicError(f"Cannot open microphone: {exc}") from exc

    if not heard_speech or voiced_frames < MIN_VOICED_FRAMES:
        raise NoSpeechError("No speech detected.")

    data = np.frombuffer(bytes(audio), dtype=np.int16)
    path.parent.mkdir(parents=True, exist_ok=True)
    sf.write(str(path), data, SAMPLE_RATE, format="FLAC")
    return n * FRAME_MS / 1000
