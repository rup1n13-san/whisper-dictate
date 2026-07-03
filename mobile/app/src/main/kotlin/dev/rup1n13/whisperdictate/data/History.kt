package dev.rup1n13.whisperdictate.data

import android.content.Context
import java.io.File

class History(context: Context) {

    private val dir = File(context.filesDir, "history")
    val lastAudio = File(context.filesDir, "last.wav")

    fun save(text: String) {
        dir.mkdirs()
        File(dir, "${System.currentTimeMillis()}.txt").writeText(text)
        dir.listFiles { f -> f.extension == "txt" }
            ?.sortedBy { it.name }
            ?.dropLast(KEEP)
            ?.forEach { it.delete() }
    }

    /** Newest first. */
    fun entries(): List<String> =
        dir.listFiles { f -> f.extension == "txt" }
            ?.sortedByDescending { it.name }
            ?.map { it.readText() }
            ?: emptyList()

    companion object {
        const val KEEP = 5
    }
}
