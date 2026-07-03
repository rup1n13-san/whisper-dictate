package dev.rup1n13.whisperdictate.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secrets = EncryptedSharedPreferences.create(
        context,
        "secrets",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var baseUrl: String
        get() = prefs.getString("api_base_url", DEFAULT_BASE_URL)!!.trimEnd('/')
        set(value) = prefs.edit().putString("api_base_url", value.trimEnd('/')).apply()

    var model: String
        get() = prefs.getString("model", DEFAULT_MODEL)!!
        set(value) = prefs.edit().putString("model", value).apply()

    var silenceSeconds: Int
        get() = prefs.getInt("silence_seconds", DEFAULT_SILENCE_SECONDS)
        set(value) = prefs.edit().putInt("silence_seconds", value.coerceIn(5, 10)).apply()

    var apiKey: String?
        get() = secrets.getString("api_key", null)?.takeIf { it.isNotBlank() }
        set(value) = secrets.edit().putString("api_key", value?.trim()).apply()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"
        const val DEFAULT_MODEL = "whisper-large-v3-turbo"
        const val DEFAULT_SILENCE_SECONDS = 7
        const val MAX_DURATION_SECONDS = 300
    }
}
