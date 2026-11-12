package org.wordcraft.android.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** One entry in the activity list: what happened to a file (`Opened`, `Saved`, `Exported`) and when. */
data class RecentDoc(val uri: String, val name: String, val openedAt: Long, val action: String = "Opened", val pinned: Boolean = false) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()

    /** Formats the editor can open again; anything else (an exported PDF…) opens in another app. */
    val editable: Boolean get() = extension in setOf("docx", "docm", "dotx", "odt", "rtf", "txt", "doc", "html", "htm", "md", "")
}

/** The last unsaved state of the open document, kept in app-private storage so a crash or kill never loses typing. */
data class Recovery(val name: String, val sourceUri: String?, val savedAt: Long)

/** Storage Access Framework I/O plus a small recent-documents list. */
class DocumentStore(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("recent", Context.MODE_PRIVATE)

    fun displayName(uri: Uri): String =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "Document"

    fun read(uri: Uri): ByteArray =
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot open $uri")

    /** "wt" truncates first, so a shorter document never leaves old bytes at the end. */
    fun write(uri: Uri, bytes: ByteArray) {
        val out = ctx.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot write $uri")
        out.use { it.write(bytes) }
    }

    fun recents(): List<RecentDoc> =
        (prefs.getString("list", "") ?: "").lines().mapNotNull {
            val p = it.split('\t')
            if (p.size >= 3) RecentDoc(p[0], p[1], p[2].toLongOrNull() ?: 0L, p.getOrNull(3)?.ifBlank { null } ?: "Opened", p.getOrNull(4) == "1") else null
        }

    private fun save(list: List<RecentDoc>) {
        prefs.edit().putString("list", list.joinToString("\n") {
            "${it.uri}\t${it.name.replace('\t', ' ').replace('\n', ' ')}\t${it.openedAt}\t${it.action}\t${if (it.pinned) "1" else "0"}"
        }).apply()
    }

    /** Newest first; a file already in the list moves to the top and keeps its pin. */
    fun remember(uri: Uri, name: String, action: String = "Opened") {
        val old = recents()
        val pinned = old.firstOrNull { it.uri == uri.toString() }?.pinned ?: false
        save((listOf(RecentDoc(uri.toString(), name, System.currentTimeMillis(), action, pinned)) + old.filter { it.uri != uri.toString() }).take(60))
    }

    fun togglePin(uri: String) = save(recents().map { if (it.uri == uri) it.copy(pinned = !it.pinned) else it })
    fun forget(uri: String) = save(recents().filter { it.uri != uri })
    fun clear() = save(recents().filter { it.pinned })

    // ---- recovery copy (never leaves the device) ------------------------------------------------

    private val recoveryFile get() = java.io.File(ctx.filesDir, "recovery/autosave.bin")

    fun writeRecovery(bytes: ByteArray, name: String, sourceUri: String?) {
        val f = recoveryFile
        f.parentFile?.mkdirs()
        val tmp = java.io.File(f.parentFile, "autosave.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(f) // replace atomically: a crash mid-write keeps the previous copy
        prefs.edit().putString("rec_name", name).putString("rec_src", sourceUri).putLong("rec_time", System.currentTimeMillis()).apply()
    }

    fun recovery(): Recovery? {
        if (!recoveryFile.exists()) return null
        return Recovery(prefs.getString("rec_name", null) ?: "Untitled.docx", prefs.getString("rec_src", null), prefs.getLong("rec_time", recoveryFile.lastModified()))
    }

    fun recoveryBytes(): ByteArray? = runCatching { recoveryFile.readBytes() }.getOrNull()

    fun clearRecovery() {
        recoveryFile.delete()
        prefs.edit().remove("rec_name").remove("rec_src").remove("rec_time").apply()
    }
}
