package dev.rup1n13.whisperdictate.api

import dev.rup1n13.whisperdictate.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class TranscriptionException(message: String) : Exception(message)

/** OpenAI-compatible /audio/transcriptions client — same contract as the desktop one. */
class TranscriptionClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(settings: Settings, audio: File): String = withContext(Dispatchers.IO) {
        val key = settings.apiKey
            ?: throw TranscriptionException("No API key — open Settings and scan the QR or paste it")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", audio.name, audio.asRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", settings.model)
            .addFormDataPart("response_format", "json")
            .build()
        val request = Request.Builder()
            .url("${settings.baseUrl}/audio/transcriptions")
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()

        var lastIo: IOException? = null
        repeat(2) { // one retry on transient network failure
            try {
                http.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    when {
                        resp.code == 401 -> throw TranscriptionException("API key rejected (401) — re-provision in Settings")
                        resp.code == 429 -> throw TranscriptionException("Rate limited (429) — wait a moment")
                        !resp.isSuccessful -> throw TranscriptionException("API error ${resp.code}: ${text.take(200)}")
                        else -> return@withContext parseText(text)
                    }
                }
            } catch (e: IOException) {
                lastIo = e
            }
        }
        throw TranscriptionException("Network unreachable: ${lastIo?.message ?: "unknown"}")
    }

    private fun parseText(raw: String): String = try {
        JSONObject(raw).getString("text").trim()
    } catch (e: Exception) {
        throw TranscriptionException("Unexpected API response: ${raw.take(200)}")
    }
}
