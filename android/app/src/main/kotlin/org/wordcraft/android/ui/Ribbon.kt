package org.wordcraft.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.wordcraft.android.editor.EditorUiState

val FONTS = listOf("Calibri", "Arial", "Times New Roman", "Georgia", "Verdana", "Courier New", "Cambria", "Consolas", "Segoe UI", "Roboto", "Noto Serif", "Open Sans")
val SIZES = listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36, 48, 72)

private val BULLETS = listOf("Round bullet •" to "bullet", "Circle ○" to "○", "Square ■" to "■", "Arrow ➢" to "➢", "Check ✓" to "✓", "Dash –" to "–")
private val NUMBERINGS = listOf("1. 2. 3." to "numbered", "1) 2) 3)" to "numberedParen", "A. B. C." to "upperLetter", "a. b. c." to "lowerLetter", "i. ii. iii." to "lowerRoman", "Outline 1. 1.1. 1.1.1." to "outline")
private val SPACINGS = listOf("1.0" to 1.0, "1.15" to 1.15, "1.5" to 1.5, "2.0" to 2.0, "2.5" to 2.5, "3.0" to 3.0)
private val CASES = listOf("Sentence case" to "sentence", "lowercase" to "lower", "UPPERCASE" to "upper", "Capitalize Each Word" to "title", "tOGGLE cASE" to "toggle")

/** Groups drawn by hand (with a "More" menu for the rest of their commands); the registry fills in everything else. */
private val CURATED: Map<String, Set<String>> = mapOf(
    "Home" to setOf("Clipboard", "Font", "Paragraph", "Styles", "Editing"),
    "Insert" to setOf("Pages", "Tables", "Illustrations", "Header & Footer", "Text", "Links", "Symbols"),
    "Layout" to setOf("Page Setup", "Paragraph"),
) + CONTEXT_CURATED

/** The ribbon body for one tab: Home, Insert and Layout are hand-laid-out; the rest is built from the engine's registry. */
@Composable
fun RibbonBody(ui: EditorUiState, tab: String, d: Dispatcher) {
    Surface(color = RibbonBg, contentColor = RibbonInk, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
            when (tab) {
                "Home" -> HomeGroups(ui, d)
                "Insert" -> InsertGroups(ui, d)
                "Layout" -> LayoutGroups(ui, d)
                "Table Design" -> TableDesignGroups(ui, d)
                "Table Layout" -> TableLayoutGroups(ui, d)
                "Picture Format" -> PictureFormatGroups(ui, d)
            }
            val skip = CURATED[tab].orEmpty()
            ui.ribbon?.groups(tab)?.filter { it.name !in skip }?.forEach { g -> GenericGroup(g, ui, d) }
        }
    }
}

/** Whether a toggle command is currently on; null for commands that aren't toggles. */
internal fun toggleState(ui: EditorUiState, id: String): Boolean? = when (id) {
    "review.trackChanges" -> ui.state?.trackChanges
    "view.marks" -> ui.state?.showMarks
    else -> null
}

internal fun Cmd.entry(ui: EditorUiState, d: Dispatcher) = MenuEntry(label, ui.isEnabled(id), shortcut.substringBefore(" / ")) { d(this) }

/** Buttons for a group's own commands, with sub-groups folded into drop-downs and the overflow into "More". */
@Composable
private fun GenericGroup(g: RibbonGroup, ui: EditorUiState, d: Dispatcher) {
    val direct = g.cmds.filter { it.sub.isEmpty() }
    val subs = g.cmds.filter { it.sub.isNotEmpty() }.groupBy { it.sub }
    val shown = if (direct.size > 5) direct.take(4) else direct
    val rest = direct.drop(shown.size)
    GroupBox(g.name) {
        shown.forEach { c ->
            val on = toggleState(ui, c.id)
            RBtn(c.label, null, ui.isEnabled(c.id), active = on == true, cmd = c) {
                if (on != null && c.id == "review.trackChanges") d.id(c.id, mapOf("value" to !on)) else d(c)
            }
        }
        subs.forEach { (name, cs) ->
            if (cs.size == 1) RBtn(cs[0].label, null, ui.isEnabled(cs[0].id), active = toggleState(ui, cs[0].id) == true, cmd = cs[0]) { d(cs[0]) }
            else MenuBtn(name.substringAfterLast(" › "), null, cs.map { it.entry(ui, d) }, cmd = cs[0])
        }
        if (rest.isNotEmpty()) MenuBtn("More", Icons.Default.MoreHoriz, rest.map { it.entry(ui, d) })
    }
}

/** Overflow menu: the group's registry commands that the hand-built controls don't already show. */
@Composable
internal fun MoreMenu(tab: String, group: String, shownIds: Set<String>, ui: EditorUiState, d: Dispatcher) {
    val rest = ui.ribbon?.tabCmds(tab)?.filter { it.group == group && it.id !in shownIds }.orEmpty()
    if (rest.isNotEmpty()) MenuBtn("More", Icons.Default.MoreHoriz, rest.map { it.entry(ui, d) })
}

@Composable
internal fun Tool(ui: EditorUiState, d: Dispatcher, id: String, label: String, icon: ImageVector, active: Boolean = false) =
    MiniToggle(icon, label, active, ui.isEnabled(id)) { d.id(id) }

@Composable
private fun HomeGroups(ui: EditorUiState, d: Dispatcher) {
    val st = ui.state
    fun on(id: String) = ui.isEnabled(id)
    var paraBorders by remember { mutableStateOf(false) }

    // ---- Clipboard
    GroupBox("Clipboard") {
        MenuBtn(
            "Paste", Icons.Default.ContentPaste,
            listOf("edit.paste", "edit.pasteText", "edit.pasteMerge", "edit.pasteSpecial").mapNotNull { ui.ribbon?.byId?.get(it) }.map { it.entry(ui, d) }.ifEmpty { listOf(MenuEntry("Paste") { d.id("edit.paste") }) },
        )
        Column {
            Row {
                Tool(ui, d, "edit.cut", "Cut", Icons.Default.ContentCut)
                Tool(ui, d, "edit.copy", "Copy", Icons.Default.ContentCopy)
            }
            Row { Tool(ui, d, "edit.formatPainter", "Format Painter", Icons.Default.FormatPaint) }
        }
        MoreMenu("Home", "Clipboard", setOf("edit.paste", "edit.pasteText", "edit.pasteMerge", "edit.pasteSpecial", "edit.cut", "edit.copy", "edit.formatPainter"), ui, d)
    }

    // ---- Font
    GroupBox("Font") {
        Column(Modifier.padding(horizontal = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Combo(st?.font ?: "Calibri", 112, FONTS.map { f -> MenuEntry(f) { d.id("format.font", mapOf("name" to f)) } })
                Spacer(Modifier.width(4.dp))
                Combo(st?.size?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() } ?: "11", 52, SIZES.map { sz -> MenuEntry("$sz") { d.id("format.size", mapOf("size" to sz)) } })
                Tool(ui, d, "format.growFont", "Grow font", Icons.Default.Add)
                Tool(ui, d, "format.shrinkFont", "Shrink font", Icons.Default.Remove)
                MiniMenu(Icons.Default.TextFields, "Change case", on("format.changeCase"), CASES.map { (n, m) -> MenuEntry(n) { d.id("format.changeCase", mapOf("mode" to m)) } })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tool(ui, d, "format.bold", "Bold", Icons.Default.FormatBold, st?.bold == true)
                Tool(ui, d, "format.italic", "Italic", Icons.Default.FormatItalic, st?.italic == true)
                Tool(ui, d, "format.underline", "Underline", Icons.Default.FormatUnderlined, st?.underline == true)
                Tool(ui, d, "format.strikethrough", "Strikethrough", Icons.Default.StrikethroughS, st?.strike == true)
                Tool(ui, d, "format.subscript", "Subscript", Icons.Default.Subscript, st?.subscript == true)
                Tool(ui, d, "format.superscript", "Superscript", Icons.Default.Superscript, st?.superscript == true)
                ColorMenu("Font color", Icons.Default.FormatColorText, on("format.color"), st?.color ?: "auto", fallbackBar = Color.Red) { d.id("format.color", mapOf("color" to it)) }
                ColorMenu("Highlight", Icons.Default.FormatColorFill, on("format.highlight"), st?.highlight, fallbackBar = Color(0xFFFFEB3B)) { d.id("format.highlight", mapOf("color" to it)) }
                Tool(ui, d, "format.clear", "Clear formatting", Icons.Default.FormatClear)
            }
        }
        MoreMenu(
            "Home", "Font",
            setOf("format.font", "format.size", "format.growFont", "format.shrinkFont", "format.bold", "format.italic", "format.underline", "format.strikethrough", "format.subscript", "format.superscript", "format.color", "format.highlight", "format.clear", "format.state", "format.changeCase"),
            ui, d,
        )
    }

    // ---- Paragraph
    GroupBox("Paragraph") {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SplitMini(
                    Icons.Default.FormatListBulleted, "Bullets", false, on("para.bullets"),
                    BULLETS.map { (n, k) -> MenuEntry(n) { d.id("para.bullets", mapOf("kind" to k)) } } + MenuEntry("None") { d.id("para.bullets", mapOf("off" to true)) },
                ) { d.id("para.bullets") }
                SplitMini(
                    Icons.Default.FormatListNumbered, "Numbering", false, on("para.numbering"),
                    NUMBERINGS.map { (n, k) -> MenuEntry(n) { d.id("para.numbering", mapOf("kind" to k)) } } + MenuEntry("None") { d.id("para.numbering", mapOf("off" to true)) },
                ) { d.id("para.numbering") }
                Tool(ui, d, "para.outdent", "Decrease indent", Icons.Default.FormatIndentDecrease)
                Tool(ui, d, "para.indent", "Increase indent", Icons.Default.FormatIndentIncrease)
                MiniMenu(Icons.Default.FormatLineSpacing, "Line and paragraph spacing", on("para.lineSpacing"), lineSpacingEntries(d))
                ColorMenu("Shading", Icons.Default.FormatColorFill, on("para.shading"), null, fallbackBar = Color(0xFFB0BEC5)) { d.id("para.shading", mapOf("color" to if (it == "auto") org.json.JSONObject.NULL else it)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tool(ui, d, "para.alignLeft", "Align left", Icons.Default.FormatAlignLeft, st?.alignment == "left")
                Tool(ui, d, "para.alignCenter", "Center", Icons.Default.FormatAlignCenter, st?.alignment == "center")
                Tool(ui, d, "para.alignRight", "Align right", Icons.Default.FormatAlignRight, st?.alignment == "right")
                Tool(ui, d, "para.justify", "Justify", Icons.Default.FormatAlignJustify, st?.alignment == "justify" || st?.alignment == "both")
                Tool(ui, d, "view.marks", "Show/Hide ¶", Icons.Default.Visibility, st?.showMarks == true)
                Tool(ui, d, "para.sort", "Sort", Icons.Default.SortByAlpha)
                MiniToggle(Icons.Default.BorderAll, "Borders", false, on("para.borders")) { paraBorders = true }
            }
        }
        val lineIds = ui.ribbon?.tabCmds("Home").orEmpty().filter { it.sub == "Line Spacing" }.map { it.id }
        MoreMenu(
            "Home", "Paragraph",
            setOf("para.bullets", "para.numbering", "para.outdent", "para.indent", "para.alignLeft", "para.alignCenter", "para.alignRight", "para.justify", "para.lineSpacing", "para.shading", "view.marks", "para.sort", "para.borders") + lineIds,
            ui, d,
        )
    }

    // ---- Styles gallery
    GroupBox("Styles") {
        val names = ui.styles.ifEmpty { listOf("Normal", "Heading 1", "Heading 2", "Heading 3", "Title") }
        Row(Modifier.widthIn(max = 260.dp).horizontalScroll(rememberScrollState())) {
            names.forEach { n ->
                FilterChip(
                    selected = st?.styleName == n, onClick = { d.id("para.style", mapOf("style" to n)) },
                    label = { Text(n) }, modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
        RBtn("Styles pane", Icons.Default.Style) { d.id("styles.pane") }
        MoreMenu("Home", "Styles", setOf("para.style", "styles.pane", "styles.list", "view.stylesPane"), ui, d)
    }

    // ---- Editing
    GroupBox("Editing") {
        RBtn("Find", Icons.Default.Search, on("edit.find")) { d.id("edit.find") }
        RBtn("Replace", Icons.Default.FindReplace, on("edit.replace")) { d.id("edit.replace") }
        val select = ui.ribbon?.tabCmds("Home").orEmpty().filter { it.sub == "Select" }
        if (select.isNotEmpty()) MenuBtn("Select", Icons.Default.SelectAll, select.map { it.entry(ui, d) })
        MoreMenu("Home", "Editing", setOf("edit.find", "edit.replace") + select.map { it.id }, ui, d)
    }

    if (paraBorders) BordersDialog(false, { paraBorders = false }) { k, style, w, c ->
        paraBorders = false
        d.id("para.borders", mapOf("kind" to k, "style" to style, "width" to w, "color" to c))
    }
}

/** Word's Line and Paragraph Spacing menu: multiples plus space before/after the paragraph. */
internal fun lineSpacingEntries(d: Dispatcher): List<MenuEntry> =
    SPACINGS.map { (n, v) -> MenuEntry("Line spacing $n") { d.id("para.lineSpacing", mapOf("value" to v)) } } +
        listOf(
            MenuEntry("Add 12 pt before paragraph") { d.id("para.spacing", mapOf("before" to 12)) },
            MenuEntry("Remove space before paragraph") { d.id("para.spacing", mapOf("before" to 0)) },
            MenuEntry("Add 12 pt after paragraph") { d.id("para.spacing", mapOf("after" to 12)) },
            MenuEntry("Remove space after paragraph") { d.id("para.spacing", mapOf("after" to 0)) },
        )

private val PALETTE = listOf(
    "auto" to "Automatic", "000000" to "Black", "C00000" to "Dark red", "FF0000" to "Red", "FFC000" to "Orange", "FFFF00" to "Yellow",
    "92D050" to "Light green", "00B050" to "Green", "00B0F0" to "Light blue", "0070C0" to "Blue", "7030A0" to "Purple", "808080" to "Gray", "D9D9D9" to "Light gray", "FFFFFF" to "White",
)

private fun hexColor(hex: String?): Color? = hex?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }

/** A colour button with Word's colour bar under the icon showing the current value. */
@Composable
fun ColorMenu(label: String, icon: ImageVector, enabled: Boolean, current: String? = null, fallbackBar: Color? = null, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val bar = hexColor(current) ?: fallbackBar
    Box {
        Box(contentAlignment = Alignment.BottomCenter) {
            MiniToggle(icon, label, false, enabled) { open = true }
            if (bar != null) Box(Modifier.padding(bottom = 2.dp).width(16.dp).height(3.dp).background(bar))
        }
        DropdownMenu(open, { open = false }, Modifier.heightIn(max = 400.dp)) {
            PALETTE.forEach { (hex, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = {
                        Box(Modifier.size(18.dp).background(hexColor(hex) ?: Color.Transparent, CircleShape).border(1.dp, Color.Gray, CircleShape))
                    },
                    onClick = { open = false; onPick(hex) },
                )
            }
        }
    }
}
