package org.wordcraft.android.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

data class RecentDoc(val uri: String, val name: String, val openedAt: Long)

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
            if (p.size == 3) RecentDoc(p[0], p[1], p[2].toLongOrNull() ?: 0L) else null
        }

    fun remember(uri: Uri, name: String) {
        val all = listOf(RecentDoc(uri.toString(), name, System.currentTimeMillis())) +
            recents().filter { it.uri != uri.toString() }
        prefs.edit().putString("list", all.take(20).joinToString("\n") { "${it.uri}\t${it.name.replace('\t', ' ')}\t${it.openedAt}" }).apply()
    }

    fun forget(uri: String) {
        prefs.edit().putString("list", recents().filter { it.uri != uri }.joinToString("\n") { "${it.uri}\t${it.name}\t${it.openedAt}" }).apply()
    }
}
