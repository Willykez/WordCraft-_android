package com.willkez.wordcraft.ui

/** Things the UI asks of the host activity (file pickers, system clipboard). */
interface EditorActions {
    fun save()
    fun saveCopy(format: String)
    fun openFile()
    fun pickPicture()
    fun copy(cut: Boolean)
    fun paste()
    /** Paste from the system clipboard as `text` (no formatting) or `merge` (take the surrounding formatting). */
    fun pasteAs(mode: String)
    fun close()
    /** Share the document (`docx` or `pdf`) through Android's share sheet. */
    fun share(format: String)
    /** Opens the save-location dialog (permanent folder, or ask every time). */
    fun chooseSaveFolder()
    /** Opens the app-theme dialog. */
    fun chooseTheme()
}
