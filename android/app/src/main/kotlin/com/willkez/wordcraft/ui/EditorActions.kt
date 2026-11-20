package com.willkez.wordcraft.ui

/** Things the UI asks of the host activity (file pickers, system clipboard). */
interface EditorActions {
    fun save()
    fun saveCopy(format: String)
    fun openFile()
    fun pickPicture()
    fun copy(cut: Boolean)
    fun paste()
    fun close()
    /** Opens the save-location dialog (permanent folder, or ask every time). */
    fun chooseSaveFolder()
    /** Opens the app-theme dialog. */
    fun chooseTheme()
}
