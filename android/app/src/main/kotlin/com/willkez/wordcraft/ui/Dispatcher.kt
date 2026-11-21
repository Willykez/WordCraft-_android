package com.willkez.wordcraft.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.willkez.wordcraft.editor.EditorViewModel

/** The app theme: one accent (title bar, buttons, headers) chosen by the user; document colours are separate. */
object AppTheme {
    class Spec(val name: String, val primary: Color, val dark: Color, val gradient: List<Color>)
    val specs = listOf(
        Spec("Ocean", Color(0xFF2B579A), Color(0xFF1E3F73), listOf(Color(0xFF2B579A), Color(0xFF3949AB), Color(0xFF8E24AA), Color(0xFFD81B60))),
        Spec("Forest", Color(0xFF2E7D32), Color(0xFF1B5E20), listOf(Color(0xFF2E7D32), Color(0xFF00897B), Color(0xFF00838F), Color(0xFF2B579A))),
        Spec("Sunset", Color(0xFFE65100), Color(0xFFBF360C), listOf(Color(0xFFEF6C00), Color(0xFFC62828), Color(0xFFD81B60), Color(0xFF8E24AA))),
        Spec("Berry", Color(0xFFAD1457), Color(0xFF880E4F), listOf(Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFF3949AB), Color(0xFF2B579A))),
        Spec("Lagoon", Color(0xFF00796B), Color(0xFF004D40), listOf(Color(0xFF00897B), Color(0xFF00838F), Color(0xFF2B579A), Color(0xFF3949AB))),
        Spec("Slate", Color(0xFF455A64), Color(0xFF263238), listOf(Color(0xFF455A64), Color(0xFF3949AB), Color(0xFF2B579A), Color(0xFF00897B))),
    )
    var index by mutableIntStateOf(0)
    val current: Spec get() = specs[index.coerceIn(specs.indices)]
}
val WordBlue: Color get() = AppTheme.current.primary
val WordBlueDark: Color get() = AppTheme.current.dark
val ThemeGradient: List<Color> get() = AppTheme.current.gradient
val RibbonBg = Color(0xFFF3F3F3)
/** The flat ribbon surface, its icon ink and the hairlines between groups: neutral, like the desktop app. */
val RibbonPanel = Color(0xFFFAFAFA)
val RibbonInk = Color(0xFF222222)
val RibbonIcon = Color(0xFF3D3D3D)
val RibbonLine = Color(0xFFDADADA)
val ContextAccent = Color(0xFFC55A11)

// ---- Rich palette: every tab, group, screen and file type gets its own colour ------------------
val PaletteBlue = Color(0xFF2B579A)
val PaletteTeal = Color(0xFF00897B)
val PalettePurple = Color(0xFF8E24AA)
val PalettePink = Color(0xFFD81B60)
val PaletteOrange = Color(0xFFEF6C00)
val PaletteGreen = Color(0xFF2E7D32)
val PaletteIndigo = Color(0xFF3949AB)
val PaletteCrimson = Color(0xFFC62828)
val PaletteCyan = Color(0xFF00838F)
val PaletteAmber = Color(0xFFF9A825)
val Palette = listOf(PaletteBlue, PaletteTeal, PalettePurple, PalettePink, PaletteOrange, PaletteGreen, PaletteIndigo, PaletteCrimson, PaletteCyan, PaletteAmber)

/** The colour a ribbon tab (and everything inside it) is drawn in. */
fun tabColor(tab: String): Color = when (tab) {
    "Home" -> PaletteBlue
    "Insert" -> PaletteTeal
    "Draw" -> PalettePurple
    "Design" -> PalettePink
    "Layout" -> PaletteOrange
    "References" -> PaletteGreen
    "Mailings" -> PaletteIndigo
    "Review" -> PaletteCrimson
    "View" -> PaletteCyan
    "Table Design", "Table Layout", "Picture Format" -> ContextAccent
    else -> Palette[(tab.hashCode() and 0x7fffffff) % Palette.size]
}

/** A pale wash of `c` (0 = white, 1 = full colour), for backgrounds that should still read as that colour. */
fun tint(c: Color, amount: Float = 0.12f): Color = lerp(Color.White, c, amount)

/** The accent of the ribbon tab being drawn; group titles, dividers and toggles pick it up. */
val LocalAccent = compositionLocalOf { WordBlue }

/** Workspace behind the pages: a soft multi-colour wash instead of flat grey. */
val WorkspaceBrush: Brush = Brush.linearGradient(
    listOf(Color(0xFFBBD7FF), Color(0xFFD9C8FF), Color(0xFFFFC9E0), Color(0xFFFFE0B8)),
)

/** Colour and short label for a document type (home screen, recent list, explorer). */
fun typeStyle(ext: String): Pair<Color, String> = when (ext.lowercase()) {
    "pdf" -> PaletteCrimson to "PDF"
    "txt" -> Color(0xFF546E7A) to "TXT"
    "md" -> PaletteIndigo to "MD"
    "odt" -> PaletteTeal to "ODT"
    "html", "htm" -> PaletteOrange to "HTM"
    "rtf" -> PalettePurple to "RTF"
    "doc" -> PaletteCyan to "DOC"
    "docm", "dotx" -> PalettePink to "DOC"
    else -> PaletteBlue to "DOCX"
}

/**
 * Routes a ribbon/menu command. Commands that need the Android side (file pickers, clipboard) or a
 * front-end surface (panes, zoom) are handled here; everything else goes to the engine.
 */
class Dispatcher(val vm: EditorViewModel, private val actions: EditorActions) {
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
            // These two read the engine's own clipboard unless given text, so a copy made in another app was never pasted.
            "edit.pasteText" -> actions.pasteAs("text")
            "edit.pasteMerge" -> actions.pasteAs("merge")
            "insert.picture", "insert.pictureFromFile" -> actions.pickPicture()
            "view.zoomIn" -> vm.setZoom(vm.ui.value.zoom * 1.15f)
            "view.zoomOut" -> vm.setZoom(vm.ui.value.zoom / 1.15f)
            "view.zoom100", "view.pageWidth", "view.onePage", "view.multiplePages" -> vm.setZoom(1f)
            "view.zoom" -> vm.openPanel("zoom")
            "view.ruler" -> vm.toggleRuler()
            "view.navigationPane" -> vm.togglePanel("nav")
            "edit.find", "edit.advancedFind", "edit.replace" -> vm.openPanel("find")
            "view.stylesPane", "styles.pane", "styles.inspector" -> vm.togglePanel("styles")
            "view.commentsPane", "review.comments" -> vm.togglePanel("comments")
            "edit.clipboardPane" -> vm.togglePanel("clipboard")
            "file.info", "file.properties" -> vm.openPanel("info")
            "format.fontDialog" -> vm.openPanel("format")
            "review.changes", "review.issues" -> vm.openPanel("review")
            "tools.search" -> vm.openPanel("search")
            "references.toc" -> vm.openPanel("toc")
            "para.borders" -> vm.openPanel("borders")
            "design.pageBorders" -> vm.openPanel("pageBorders")
            "table.borders", "table.shading", "table.borderPainter" -> vm.openPanel("tableFormat")
            // Both commands' params are written with a "?" or a "|" alternative, so the ribbon
            // never treats them as needing input (see Cmd.needsInput) and would otherwise run
            // them with nothing chosen: insert.link with no url/bookmark/heading fails outright,
            // and table.quick with no kind always inserts the same default table. Routing them
            // to their own sheet is what actually lets the user choose.
            "insert.link" -> vm.openPanel("link")
            // The engine only asks for these dialogs; the sheets collect the values and run the command with them.
            "edit.goto" -> vm.openPanel("goto")
            "layout.pageSetup" -> vm.openPanel("pageSetup")
            "para.dialog" -> vm.openPanel("paragraph")
            "file.options" -> vm.openPanel("options")
            "insert.chart" -> vm.openPanel("chart")
            "insert.icon" -> vm.openPanel("icons")
            "insert.smartArt" -> vm.openPanel("smartArt")
            "references.caption" -> vm.openPanel("caption")
            "table.quick" -> vm.openPanel("quickTables")
            else -> return false
        }
        return true
    }
}
