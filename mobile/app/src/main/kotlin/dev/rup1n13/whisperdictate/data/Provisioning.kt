package dev.rup1n13.whisperdictate.data

import org.json.JSONObject

/** Payload carried by the desktop-generated QR code. */
data class Provisioning(val baseUrl: String, val apiKey: String) {

    companion object {
        /** Returns null on anything that isn't a valid payload — caller falls back to paste. */
        fun parse(raw: String?): Provisioning? {
            if (raw.isNullOrBlank()) return null
            return try {
                val json = JSONObject(raw)
                val url = json.getString("api_base_url").trim()
                val key = json.getString("api_key").trim()
                if (!url.startsWith("http") || key.isEmpty()) null
                else Provisioning(url.trimEnd('/'), key)
            } catch (_: Exception) {
                null
            }
        }
    }
}
