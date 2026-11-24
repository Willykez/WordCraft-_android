package com.willkez.wordcraft.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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

    // ---- permanent save folder ------------------------------------------------------------------

    /** The folder chosen once for every new document, Save As and export; null when none is chosen or access was revoked. */
    fun saveTree(): Uri? {
        val uri = prefs.getString("save_tree", null)?.let(Uri::parse) ?: return null
        val ok = ctx.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        return uri.takeIf { ok }
    }

    fun setSaveTree(uri: Uri?) {
        prefs.edit().apply { if (uri == null) remove("save_tree") else putString("save_tree", uri.toString()) }.apply()
    }

    /** Old behaviour on request: show the system "save as" picker every time. */
    var askEveryTime: Boolean
        get() = prefs.getBoolean("ask_each_time", false)
        set(v) { prefs.edit().putBoolean("ask_each_time", v).apply() }

    /** Capitalise the first letter of each sentence while typing. */
    var autoCapitalize: Boolean
        get() = prefs.getBoolean("auto_cap", true)
        set(v) { prefs.edit().putBoolean("auto_cap", v).apply() }

    // ---- AI assistant (the user's own Gemini key; plain app-private storage) ---------------------
    fun aiKey(): String = prefs.getString("ai_key", "").orEmpty()
    fun aiModel(): String = prefs.getString("ai_model", "gemini-2.5-flash").orEmpty().ifBlank { "gemini-2.5-flash" }
    fun setAi(key: String, model: String) { prefs.edit().putString("ai_key", key.trim()).putString("ai_model", model.trim()).apply() }

    /** A readable name for the folder ("Documents/WordCraft"). */
    fun saveTreeName(): String? = saveTree()?.let { treeLabel(it) }

    fun treeLabel(tree: Uri): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return "Chosen folder"
        val path = id.substringAfter(':', "")
        return if (path.isBlank()) "Internal storage" else path
    }

    /** Creates `wanted` in the folder, or `name(1).ext`, `name(2).ext`… when that name is taken (never `name.ext (1)`). */
    fun createUnique(tree: Uri, wanted: String, mime: String): Uri {
        val resolver = ctx.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        val taken = mutableSetOf<String>()
        resolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { while (it.moveToNext()) it.getString(0)?.let { n -> taken += n.lowercase() } }
        val name = uniqueName(wanted, taken)
        return DocumentsContract.createDocument(resolver, parent, mime, name) ?: error("Cannot create $name in the save folder")
    }

    companion object {
        /** `report.pdf` → `report(1).pdf` → `report(2).pdf` (`taken` holds lower-case names). */
        fun uniqueName(wanted: String, taken: Set<String>): String {
            if (wanted.lowercase() !in taken) return wanted
            val dot = wanted.lastIndexOf('.')
            val base = if (dot > 0) wanted.substring(0, dot) else wanted
            val ext = if (dot > 0) wanted.substring(dot) else ""
            var i = 1
            while ("$base($i)$ext".lowercase() in taken) i++
            return "$base($i)$ext"
        }

        /** The real MIME type for a format, so the system never appends its own " (1)" or extension. */
        fun mimeFor(ext: String): String = when (ext.lowercase()) {
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "rtf" -> "application/rtf"
            else -> "application/octet-stream"
        }
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
    fun forget(uri: String) { thumbFile(uri).delete(); save(recents().filter { it.uri != uri }) }
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

    // ---- page-1 thumbnails for the home screen (kept on the device) ----------------------------

    private fun thumbFile(key: String) = java.io.File(ctx.filesDir, "thumbs/${key.hashCode().toUInt()}.png")

    fun writeThumbnail(key: String, bmp: android.graphics.Bitmap) {
        val f = thumbFile(key)
        f.parentFile?.mkdirs()
        runCatching { f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) } }
    }

    fun thumbnail(key: String): android.graphics.Bitmap? =
        thumbFile(key).takeIf { it.exists() }?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it.path) }.getOrNull() }
}
