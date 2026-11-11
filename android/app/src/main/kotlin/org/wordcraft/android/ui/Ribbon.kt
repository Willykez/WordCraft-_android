package org.wordcraft.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import org.wordcraft.android.editor.EditorUiState

val FONTS = listOf("Calibri", "Arial", "Times New Roman", "Georgia", "Verdana", "Courier New", "Cambria", "Consolas", "Segoe UI", "Roboto", "Noto Serif", "Open Sans")
val SIZES = listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36, 48, 72)

/** The ribbon body for one tab: Home is hand-laid-out, every other tab is built from the engine's registry. */
@Composable
fun RibbonBody(ui: EditorUiState, tab: String, d: Dispatcher) {
    Surface(color = RibbonBg, contentColor = RibbonInk, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
            if (tab == "Home") HomeGroups(ui, d)
            ui.ribbon?.groups(tab)?.filter { tab != "Home" || it.name !in HOME_CURATED }?.forEach { g -> GenericGroup(g, ui, d) }
        }
    }
}

private val HOME_CURATED = setOf("Clipboard", "Font", "Paragraph", "Styles", "Editing")

private fun Cmd.entry(ui: EditorUiState, d: Dispatcher) = MenuEntry(label, ui.isEnabled(id), shortcut.substringBefore(" / ")) { d(this) }

/** Buttons for a group's own commands, with sub-groups folded into drop-downs and the overflow into "More". */
@Composable
private fun GenericGroup(g: RibbonGroup, ui: EditorUiState, d: Dispatcher) {
    val direct = g.cmds.filter { it.sub.isEmpty() }
    val subs = g.cmds.filter { it.sub.isNotEmpty() }.groupBy { it.sub }
    val shown = if (direct.size > 5) direct.take(4) else direct
    val rest = direct.drop(shown.size)
    GroupBox(g.name) {
        shown.forEach { c -> RBtn(c.label, null, ui.isEnabled(c.id), cmd = c) { d(c) } }
        subs.forEach { (name, cs) ->
            if (cs.size == 1) RBtn(cs[0].label, null, ui.isEnabled(cs[0].id), cmd = cs[0]) { d(cs[0]) }
            else MenuBtn(name.substringAfterLast(" › "), null, cs.map { it.entry(ui, d) }, cmd = cs[0])
        }
        if (rest.isNotEmpty()) MenuBtn("More", Icons.Default.MoreHoriz, rest.map { it.entry(ui, d) })
    }
}

/** Overflow menu: the group's registry commands that the hand-built Home controls don't already show. */
@Composable
private fun MoreMenu(group: String, shownIds: Set<String>, ui: EditorUiState, d: Dispatcher) {
    val rest = ui.ribbon?.tabCmds("Home")?.filter { it.group == group && it.id !in shownIds }.orEmpty()
    if (rest.isNotEmpty()) MenuBtn("More", Icons.Default.MoreHoriz, rest.map { it.entry(ui, d) })
}

@Composable
private fun HomeGroups(ui: EditorUiState, d: Dispatcher) {
    val st = ui.state
    fun on(id: String) = ui.isEnabled(id)
    fun tool(id: String, label: String, icon: ImageVector, active: Boolean = false) =
        @Composable { MiniToggle(icon, label, active, on(id)) { d.id(id) } }

    // Clipboard
    GroupBox("Clipboard") {
        MenuBtn(
            "Paste", Icons.Default.ContentPaste,
            listOf("edit.paste", "edit.pasteText", "edit.pasteMerge", "edit.pasteSpecial").mapNotNull { ui.ribbon?.byId?.get(it) }.map { it.entry(ui, d) },
        )
        Column {
            Row {
                tool("edit.cut", "Cut", Icons.Default.ContentCut)()
                tool("edit.copy", "Copy", Icons.Default.ContentCopy)()
            }
            Row { tool("edit.formatPainter", "Format Painter", Icons.Default.FormatPaint)() }
        }
        MoreMenu("Clipboard", setOf("edit.paste", "edit.pasteText", "edit.pasteMerge", "edit.pasteSpecial", "edit.cut", "edit.copy", "edit.formatPainter"), ui, d)
    }

    // Font
    GroupBox("Font") {
        Column(Modifier.padding(horizontal = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Combo(st?.font ?: "Calibri", 112, FONTS.map { f -> MenuEntry(f) { d.id("format.font", mapOf("name" to f)) } })
                Spacer(Modifier.width(4.dp))
                Combo(st?.size?.let { (if (it % 1f == 0f) it.toInt().toString() else it.toString()) } ?: "11", 52, SIZES.map { sz -> MenuEntry("$sz") { d.id("format.size", mapOf("size" to sz)) } })
                tool("format.growFont", "Grow font", Icons.Default.Add)()
                tool("format.shrinkFont", "Shrink font", Icons.Default.Remove)()
            }
            Row {
                tool("format.bold", "Bold", Icons.Default.FormatBold, st?.bold == true)()
                tool("format.italic", "Italic", Icons.Default.FormatItalic, st?.italic == true)()
                tool("format.underline", "Underline", Icons.Default.FormatUnderlined, st?.underline == true)()
                tool("format.strikethrough", "Strikethrough", Icons.Default.StrikethroughS, st?.strike == true)()
                tool("format.subscript", "Subscript", Icons.Default.Subscript, st?.subscript == true)()
                tool("format.superscript", "Superscript", Icons.Default.Superscript, st?.superscript == true)()
                ColorMenu("Font color", Icons.Default.FormatColorText, on("format.color")) { d.id("format.color", mapOf("color" to it)) }
                ColorMenu("Highlight", Icons.Default.FormatColorFill, on("format.highlight")) { d.id("format.highlight", mapOf("color" to it)) }
                tool("format.clear", "Clear formatting", Icons.Default.FormatClear)()
            }
        }
        MoreMenu(
            "Font",
            setOf("format.font", "format.size", "format.growFont", "format.shrinkFont", "format.bold", "format.italic", "format.underline", "format.strikethrough", "format.subscript", "format.superscript", "format.color", "format.highlight", "format.clear", "format.state"),
            ui, d,
        )
    }

    // Paragraph
    GroupBox("Paragraph") {
        Column {
            Row {
                tool("para.bullets", "Bullets", Icons.Default.FormatListBulleted)()
                tool("para.numbering", "Numbering", Icons.Default.FormatListNumbered)()
                tool("para.outdent", "Decrease indent", Icons.Default.FormatIndentDecrease)()
                tool("para.indent", "Increase indent", Icons.Default.FormatIndentIncrease)()
            }
            Row {
                tool("para.alignLeft", "Left", Icons.Default.FormatAlignLeft, st?.alignment == "left")()
                tool("para.alignCenter", "Center", Icons.Default.FormatAlignCenter, st?.alignment == "center")()
                tool("para.alignRight", "Right", Icons.Default.FormatAlignRight, st?.alignment == "right")()
                tool("para.justify", "Justify", Icons.Default.FormatAlignJustify, st?.alignment == "justify" || st?.alignment == "both")()
            }
        }
        val spacing = ui.ribbon?.tabCmds("Home").orEmpty().filter { it.sub == "Line Spacing" }
        if (spacing.isNotEmpty()) MenuBtn("Spacing", Icons.Default.FormatLineSpacing, spacing.map { it.entry(ui, d) })
        MoreMenu("Paragraph", setOf("para.bullets", "para.numbering", "para.outdent", "para.indent", "para.alignLeft", "para.alignCenter", "para.alignRight", "para.justify") + spacing.map { it.id }, ui, d)
    }

    // Styles gallery
    GroupBox("Styles") {
        val names = ui.styles.ifEmpty { listOf("Normal", "Heading 1", "Heading 2", "Heading 3") }
        Row(Modifier.widthIn(max = 260.dp).horizontalScroll(rememberScrollState())) {
            names.forEach { n ->
                FilterChip(
                    selected = st?.styleName == n, onClick = { d.id("para.style", mapOf("style" to n)) },
                    label = { Text(n) }, modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
        RBtn("Styles pane", Icons.Default.Style) { d.id("styles.pane") }
        MoreMenu("Styles", setOf("para.style", "para.normal", "para.heading1", "para.heading2", "para.heading3", "styles.pane", "styles.list", "view.stylesPane"), ui, d)
    }

    // Editing
    GroupBox("Editing") {
        RBtn("Find", Icons.Default.Search, on("edit.find")) { d.id("edit.find") }
        RBtn("Replace", Icons.Default.FindReplace, on("edit.replace")) { d.id("edit.replace") }
        val select = ui.ribbon?.tabCmds("Home").orEmpty().filter { it.sub == "Select" }
        if (select.isNotEmpty()) MenuBtn("Select", Icons.Default.SelectAll, select.map { it.entry(ui, d) })
        MoreMenu("Editing", setOf("edit.find", "edit.replace") + select.map { it.id }, ui, d)
    }
}

private val PALETTE = listOf(
    "auto" to "Automatic", "000000" to "Black", "C00000" to "Dark red", "FF0000" to "Red", "FFC000" to "Orange", "FFFF00" to "Yellow",
    "00B050" to "Green", "00B0F0" to "Light blue", "0070C0" to "Blue", "7030A0" to "Purple", "808080" to "Gray", "FFFFFF" to "White",
)

@Composable
fun ColorMenu(label: String, icon: ImageVector, enabled: Boolean, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        MiniToggle(icon, label, false, enabled) { open = true }
        DropdownMenu(open, { open = false }) {
            PALETTE.forEach { (hex, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = {
                        Box(
                            Modifier.size(18.dp).background(if (hex == "auto") Color.Transparent else Color(("FF$hex").toLong(16)), androidx.compose.foundation.shape.CircleShape)
                                .border(1.dp, Color.Gray, androidx.compose.foundation.shape.CircleShape),
                        )
                    },
                    onClick = { open = false; onPick(hex) },
                )
            }
        }
    }
}
