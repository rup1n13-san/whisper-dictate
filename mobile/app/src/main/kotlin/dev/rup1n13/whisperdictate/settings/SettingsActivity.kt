package dev.rup1n13.whisperdictate.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.rup1n13.whisperdictate.R
import dev.rup1n13.whisperdictate.data.Provisioning
import dev.rup1n13.whisperdictate.data.Settings

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var micStatus: TextView
    private lateinit var keyStatus: TextView
    private lateinit var silenceLabel: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var baseUrlInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var silenceSeek: SeekBar

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    private val qrScanner = registerForActivityResult(ScanContract()) { result ->
        val prov = Provisioning.parse(result.contents)
        if (prov == null) {
            toast(getString(R.string.settings_qr_invalid))
        } else {
            settings.baseUrl = prov.baseUrl
            settings.apiKey = prov.apiKey
            toast(getString(R.string.settings_provisioned))
            refresh()
        }
    }

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchScanner()
            else toast(getString(R.string.settings_qr_invalid))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        setContentView(R.layout.activity_settings)

        micStatus = findViewById(R.id.mic_status)
        keyStatus = findViewById(R.id.key_status)
        silenceLabel = findViewById(R.id.silence_label)
        apiKeyInput = findViewById(R.id.input_api_key)
        baseUrlInput = findViewById(R.id.input_base_url)
        modelInput = findViewById(R.id.input_model)
        silenceSeek = findViewById(R.id.silence_seek)

        findViewById<Button>(R.id.btn_grant_mic).setOnClickListener {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        findViewById<Button>(R.id.btn_enable_keyboard).setOnClickListener {
            startActivity(Intent(ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btn_pick_keyboard).setOnClickListener {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        }
        findViewById<Button>(R.id.btn_scan_qr).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) launchScanner()
            else cameraPermission.launch(Manifest.permission.CAMERA)
        }
        silenceSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                silenceLabel.text = getString(R.string.settings_silence_label, progress + 5)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }

        baseUrlInput.setText(settings.baseUrl)
        modelInput.setText(settings.model)
        silenceSeek.progress = settings.silenceSeconds - 5
        silenceLabel.text = getString(R.string.settings_silence_label, settings.silenceSeconds)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun launchScanner() {
        qrScanner.launch(ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("")
            setBeepEnabled(false)
            setOrientationLocked(true)
        })
    }

    private fun save() {
        val typedKey = apiKeyInput.text.toString().trim()
        if (typedKey.isNotEmpty()) {
            settings.apiKey = typedKey
            apiKeyInput.text.clear()
        }
        val url = baseUrlInput.text.toString().trim()
        if (url.startsWith("https://")) settings.baseUrl = url
        val model = modelInput.text.toString().trim()
        if (model.isNotEmpty()) settings.model = model
        settings.silenceSeconds = silenceSeek.progress + 5
        toast(getString(R.string.settings_saved))
        refresh()
    }

    private fun refresh() {
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        micStatus.text = getString(
            if (micGranted) R.string.settings_mic_granted else R.string.settings_grant_mic
        )
        keyStatus.text = getString(
            if (settings.apiKey != null) R.string.settings_key_set else R.string.settings_key_missing
        )
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
