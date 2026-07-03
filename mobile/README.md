# mobile — Whisper Dictate keyboard (Android)

Voice-input keyboard (IME) in native Kotlin. Switch to it in any app, tap the
mic, dictate in French/English or both — the transcript is typed directly
into the focused field. Same OpenAI-compatible transcription backend as the
desktop client.

## Build

Requirements: JDK 17+, Android SDK (a Flutter install provides both).

```bash
cd mobile
echo "sdk.dir=$HOME/Android" > local.properties   # or your SDK path
./gradlew test assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

CI (`.github/workflows/mobile-ci.yml`) builds on every push to `dev` touching
`mobile/**` and distributes the APK to the `testers` group via Firebase App
Distribution (secrets `FIREBASE_APP_ID`, `FIREBASE_TOKEN`).

## Setup on the phone

1. Install the APK (Firebase App Tester or sideload).
2. Open **Whisper Dictate** → grant microphone, enable the keyboard, and
   provision the API access:
   - **Scan QR**: run `whisper-dictate qr` on the desktop and scan it, or
   - paste the API key manually.
3. In any text field: switch keyboard (the ABC/globe affordance) → Whisper
   Dictate → tap the mic.

## Behavior

- Auto-stops after `silence` seconds of silence (5–10, default 7); pauses
  shorter than that never cut a dictation. Hard cap 5 min. Tap the mic again
  to stop immediately.
- Recording with no detectable speech is discarded — nothing is sent.
- On API/network failure the audio is kept and a Retry button appears.
- Last 5 transcripts under the History button — tap one to insert it again.
- Password/secure fields: dictation is disabled by design.
- Recording stops the moment the keyboard is hidden.

## Architecture

```
ime/VoiceInputService     InputMethodService — state machine, commitText
audio/Recorder            AudioRecord 16 kHz mono → WAV
audio/SilenceDetector     energy-gate VAD ported from the desktop client
api/TranscriptionClient   OkHttp, OpenAI-compatible /audio/transcriptions
data/Settings             prefs + EncryptedSharedPreferences for the key
data/History              last 5 transcripts, app-private storage
settings/SettingsActivity onboarding, QR provisioning, config
```
