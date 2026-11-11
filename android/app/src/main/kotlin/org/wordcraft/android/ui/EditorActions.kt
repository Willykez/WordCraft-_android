package org.wordcraft.android.ui

/** Things the UI asks of the host activity (file pickers, system clipboard). */
interface EditorActions {
    fun save()
    fun saveCopy(format: String)
    fun openFile()
    fun pickPicture()
    fun copy(cut: Boolean)
    fun paste()
    fun close()
}
