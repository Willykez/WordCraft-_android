package com.willkez.wordcraft.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

/** A document found while looking through the device's storage. */
data class DeviceDoc(val uri: String, val name: String, val folder: String, val size: Long, val modified: Long) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()

    /** Formats the editor can open; the rest (PDF) go to another app. */
    val editable: Boolean get() = extension in DeviceDocs.EDITABLE
}

/**
 * Finds every document on the device, from the three places Android lets an app look:
 *  1. the media index (`MediaStore.Files`), which covers documents the app is allowed to see;
 *  2. a walk of shared storage, when the user has granted "All files access" (or the old storage permission);
 *  3. the permanent save folder, through the Storage Access Framework, so what WordCraft saved always shows up.
 */
object DeviceDocs {
    val EDITABLE = setOf("docx", "docm", "dotx", "odt", "rtf", "txt", "doc", "html", "htm", "md")
    val EXTENSIONS = EDITABLE + "pdf"

    private const val MAX_RESULTS = 6000
    private const val MAX_DEPTH = 14

    /** True when the whole of shared storage can be read, so the walk in step 2 is possible. */
    fun hasFullAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun scan(ctx: Context, saveTree: Uri?): List<DeviceDoc> {
        val found = LinkedHashMap<String, DeviceDoc>() // keyed by folder + name, so one file is listed once
        fun add(d: DeviceDoc) { if (found.size < MAX_RESULTS) found.putIfAbsent(d.folder.lowercase() + "/" + d.name.lowercase(), d) }

        runCatching { fromMediaStore(ctx, ::add) }
        if (hasFullAccess(ctx)) runCatching { fromStorageWalk(::add) }
        if (saveTree != null) runCatching { fromTree(ctx, saveTree, ::add) }

        return found.values.sortedByDescending { it.modified }
    }

    // ---- 1. media index ---------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun fromMediaStore(ctx: Context, add: (DeviceDoc) -> Unit) {
        val files = MediaStore.Files.getContentUri("external")
        val name = MediaStore.Files.FileColumns.DISPLAY_NAME
        val selection = EXTENSIONS.joinToString(" OR ") { "LOWER($name) LIKE ?" }
        val args = EXTENSIONS.map { "%.$it" }.toTypedArray()
        val cols = arrayOf(
            MediaStore.Files.FileColumns._ID, name, MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED, MediaStore.Files.FileColumns.DATA,
        )
        ctx.contentResolver.query(files, cols, selection, args, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val iName = c.getColumnIndexOrThrow(name)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val iDate = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val iData = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            while (c.moveToNext()) {
                val n = c.getString(iName) ?: continue
                if (n.substringAfterLast('.', "").lowercase() !in EXTENSIONS) continue
                val data = c.getString(iData).orEmpty()
                add(
                    DeviceDoc(
                        ContentUris.withAppendedId(files, c.getLong(iId)).toString(), n,
                        niceFolder(data.substringBeforeLast('/', "")), c.getLong(iSize), c.getLong(iDate) * 1000,
                    ),
                )
            }
        }
    }

    // ---- 2. all-files walk ------------------------------------------------------------------------

    private fun fromStorageWalk(add: (DeviceDoc) -> Unit) {
        val root = Environment.getExternalStorageDirectory()
        root.walkTopDown()
            .maxDepth(MAX_DEPTH)
            .onEnter { dir ->
                // Other apps' private folders hold nothing the user would call a document; hidden folders are caches and trash.
                !dir.name.startsWith('.') && !(dir.parentFile == root && dir.name == "Android")
            }
            .filter { it.isFile && it.extension.lowercase() in EXTENSIONS }
            .forEach { f -> add(DeviceDoc(Uri.fromFile(f).toString(), f.name, niceFolder(f.parent.orEmpty()), f.length(), f.lastModified())) }
    }

    // ---- 3. the save folder -----------------------------------------------------------------------

    private fun fromTree(ctx: Context, tree: Uri, add: (DeviceDoc) -> Unit) {
        fun walk(parentId: String, path: String, depth: Int) {
            if (depth > 6) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            val cols = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
            val rows = mutableListOf<List<Any?>>()
            ctx.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) rows += listOf(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getLong(4))
            }
            for (r in rows) {
                val id = r[0] as String; val n = r[1] as? String ?: continue; val mime = r[2] as? String
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) walk(id, "$path/$n", depth + 1)
                else if (n.substringAfterLast('.', "").lowercase() in EXTENSIONS)
                    add(DeviceDoc(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), n, niceFolder(path), r[3] as Long, r[4] as Long))
            }
        }
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        walk(rootId, rootId.substringAfter(':', "").ifBlank { "Internal storage" }, 0)
    }

    private fun niceFolder(path: String): String =
        path.removePrefix(Environment.getExternalStorageDirectory().path).trim('/').ifBlank { "Internal storage" }
}
