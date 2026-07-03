package dev.rup1n13.whisperdictate.ime

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import dev.rup1n13.whisperdictate.R
import dev.rup1n13.whisperdictate.api.TranscriptionClient
import dev.rup1n13.whisperdictate.api.TranscriptionException
import dev.rup1n13.whisperdictate.audio.RecordResult
import dev.rup1n13.whisperdictate.audio.Recorder
import dev.rup1n13.whisperdictate.data.History
import dev.rup1n13.whisperdictate.data.Settings
import dev.rup1n13.whisperdictate.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VoiceInputService : InputMethodService() {

    private enum class State { IDLE, RECORDING, TRANSCRIBING, ERROR }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val recorder = Recorder()
    private val client = TranscriptionClient()
    private lateinit var settings: Settings
    private lateinit var history: History

    private var recordingJob: Job? = null
    private var state = State.IDLE
    private var secureField = false

    private lateinit var root: View
    private lateinit var status: TextView
    private lateinit var level: ProgressBar
    private lateinit var mic: ImageButton
    private lateinit var retryBtn: Button
    private lateinit var panelMain: View
    private lateinit var panelHistory: View
    private lateinit var historyItems: LinearLayout

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        history = History(this)
    }

    override fun onCreateInputView(): View {
        root = layoutInflater.inflate(R.layout.keyboard_view, null)
        status = root.findViewById(R.id.status)
        level = root.findViewById(R.id.level)
        mic = root.findViewById(R.id.mic)
        retryBtn = root.findViewById(R.id.btn_retry)
        panelMain = root.findViewById(R.id.panel_main)
        panelHistory = root.findViewById(R.id.panel_history)
        historyItems = root.findViewById(R.id.history_items)

        mic.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            when (state) {
                State.RECORDING -> recorder.stopRequested = true
                State.TRANSCRIBING -> Unit
                else -> startDictation()
            }
        }
        retryBtn.setOnClickListener { retryLastAudio() }
        root.findViewById<Button>(R.id.btn_settings).setOnClickListener { openSettings() }
        root.findViewById<Button>(R.id.btn_switch).setOnClickListener { switchAway() }
        root.findViewById<Button>(R.id.btn_history).setOnClickListener { showHistory() }
        root.findViewById<Button>(R.id.btn_history_back).setOnClickListener { showMain() }
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        secureField = isSecure(info)
        showMain()
        setState(if (secureField) State.ERROR else State.IDLE)
        status.text = getString(
            if (secureField) R.string.status_secure_field else R.string.status_idle
        )
        mic.isEnabled = !secureField
        mic.visibility = if (secureField) View.INVISIBLE else View.VISIBLE
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // never keep the mic open once the keyboard is hidden
        recordingJob?.cancel()
        recordingJob = null
        setState(State.IDLE)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ---- dictation flow ----

    private fun startDictation() {
        if (secureField) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            status.text = getString(R.string.status_no_mic_permission)
            openSettings()
            return
        }
        setState(State.RECORDING)
        recordingJob = scope.launch {
            val result = recorder.record(
                history.lastAudio,
                settings.silenceSeconds,
                Settings.MAX_DURATION_SECONDS,
            ) { rms -> level.post { level.progress = rms.toInt().coerceAtMost(level.max) } }
            root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            when (result) {
                is RecordResult.Done -> transcribeAndCommit()
                RecordResult.NoSpeech -> showError(getString(R.string.status_no_speech), retryable = false)
                RecordResult.Cancelled -> setState(State.IDLE)
                is RecordResult.Failed -> showError(result.message, retryable = false)
            }
        }
    }

    private fun retryLastAudio() {
        if (!history.lastAudio.exists()) {
            showError(getString(R.string.status_no_speech), retryable = false)
            return
        }
        scope.launch { transcribeAndCommit() }
    }

    private suspend fun transcribeAndCommit() {
        setState(State.TRANSCRIBING)
        try {
            val text = client.transcribe(settings, history.lastAudio)
            if (text.isBlank()) {
                showError(getString(R.string.status_no_speech), retryable = false)
                return
            }
            history.save(text)
            insert(text)
            setState(State.IDLE)
        } catch (e: TranscriptionException) {
            showError(e.message ?: "Transcription failed", retryable = true)
        }
    }

    private fun insert(text: String) {
        val ic = currentInputConnection
        if (ic != null) {
            ic.commitText("$text ", 1)
        } else {
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("whisper-dictate", text))
            status.text = getString(R.string.status_idle)
        }
    }

    // ---- UI state ----

    private fun setState(s: State) {
        state = s
        level.visibility = if (s == State.RECORDING) View.VISIBLE else View.INVISIBLE
        retryBtn.visibility = View.GONE
        mic.setBackgroundResource(
            if (s == State.RECORDING) R.drawable.bg_mic_button_recording else R.drawable.bg_mic_button
        )
        status.text = when (s) {
            State.RECORDING -> getString(R.string.status_recording)
            State.TRANSCRIBING -> getString(R.string.status_transcribing)
            else -> status.text
        }
        if (s == State.IDLE) status.text = getString(R.string.status_idle)
    }

    private fun showError(message: String, retryable: Boolean) {
        setState(State.ERROR)
        status.text = message
        retryBtn.visibility = if (retryable) View.VISIBLE else View.GONE
    }

    private fun showHistory() {
        historyItems.removeAllViews()
        val items = history.entries()
        if (items.isEmpty()) {
            historyItems.addView(TextView(this).apply {
                text = getString(R.string.history_empty)
                setTextColor(0xFFE8E8F0.toInt())
                setPadding(16, 12, 16, 12)
            })
        } else {
            items.forEach { entry ->
                historyItems.addView(TextView(this).apply {
                    text = if (entry.length > 80) entry.take(80) + "…" else entry
                    setTextColor(0xFFE8E8F0.toInt())
                    setPadding(16, 12, 16, 12)
                    setOnClickListener {
                        insert(entry)
                        showMain()
                    }
                })
            }
        }
        panelMain.visibility = View.GONE
        panelHistory.visibility = View.VISIBLE
    }

    private fun showMain() {
        panelHistory.visibility = View.GONE
        panelMain.visibility = View.VISIBLE
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun switchAway() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToPreviousInputMethod()
        } else {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        }
    }

    private fun isSecure(info: EditorInfo?): Boolean {
        val variation = (info?.inputType ?: 0) and InputType.TYPE_MASK_VARIATION
        val cls = (info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            (cls == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }
}
