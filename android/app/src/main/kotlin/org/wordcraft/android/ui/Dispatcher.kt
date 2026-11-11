package org.wordcraft.android.ui

import androidx.compose.ui.graphics.Color
import org.wordcraft.android.editor.EditorViewModel

val WordBlue = Color(0xFF2B579A)
val WordBlueDark = Color(0xFF1E3F73)
val RibbonBg = Color(0xFFF3F3F3)
val RibbonInk = Color(0xFF222222)
val ContextAccent = Color(0xFFC55A11)

/**
 * Routes a ribbon/menu command. Commands that need the Android side (file pickers, clipboard) or a
 * front-end surface (panes, zoom) are handled here; everything else goes to the engine.
 */
class Dispatcher(private val vm: EditorViewModel, private val actions: EditorActions) {
    operator fun invoke(cmd: Cmd) {
        if (!special(cmd.id)) vm.exec(cmd)
    }

    /** Run by id with known parameters (never asks for input). */
    fun id(id: String, params: Map<String, Any?> = emptyMap()) {
        if (params.isNotEmpty() || !special(id)) vm.run(id, params)
    }

    private fun special(id: String): Boolean {
        when (id) {
            "file.save" -> actions.save()
            "file.saveAs" -> actions.saveCopy("docx")
            "file.exportPdf", "file.print", "file.printMarkup" -> actions.saveCopy("pdf")
            "file.open" -> actions.openFile()
            "file.close" -> actions.close()
            "file.new" -> vm.newDocument()
            "edit.undo" -> vm.undo()
            "edit.redo" -> vm.redo()
            "edit.copy" -> actions.copy(false)
            "edit.cut" -> actions.copy(true)
            "edit.paste" -> actions.paste()
            "insert.picture", "insert.pictureFromFile" -> actions.pickPicture()
            "view.zoomIn" -> vm.setZoom(vm.ui.value.zoom * 1.15f)
            "view.zoomOut" -> vm.setZoom(vm.ui.value.zoom / 1.15f)
            "view.zoom100", "view.pageWidth", "view.onePage", "view.multiplePages" -> vm.setZoom(1f)
            "view.zoom" -> vm.openPanel("zoom")
            "view.navigationPane" -> vm.togglePanel("nav")
            "edit.find", "edit.advancedFind", "edit.replace" -> vm.openPanel("find")
            "view.stylesPane", "styles.pane", "styles.inspector" -> vm.togglePanel("styles")
            "view.commentsPane", "review.comments" -> vm.togglePanel("comments")
            "edit.clipboardPane" -> vm.togglePanel("clipboard")
            "file.info", "file.properties" -> vm.openPanel("info")
            "format.fontDialog" -> vm.openPanel("format")
            "tools.search" -> vm.openPanel("search")
            else -> return false
        }
        return true
    }
}
