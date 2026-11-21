package com.willkez.wordcraft.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.willkez.wordcraft.editor.EditorUiState

private val SHAPES = listOf(
    "Rectangle" to "rectangle", "Rounded rectangle" to "roundedRectangle", "Oval" to "ellipse", "Triangle" to "triangle", "Diamond" to "diamond",
    "Line" to "line", "Arrow" to "arrow", "Star" to "star", "Heart" to "heart",
    "Straight connector" to "straightConnector", "Elbow connector" to "elbowConnector", "Curved connector" to "curvedConnector",
)
private val SYMBOLS = "© ® ™ § ¶ † ‡ • ° ± × ÷ € £ ¥ ¢ ← → ↑ ↓ ↔ ≤ ≥ ≠ ≈ ∞ π Ω α β µ ½ ¼ ¾ ✓ ✗ ★".split(' ')
private val MARGINS = listOf("Normal" to "normal", "Narrow" to "narrow", "Moderate" to "moderate", "Wide" to "wide", "Mirrored" to "mirrored", "Office 2003 default" to "office2003")
private val SIZES_PAGE = listOf("Letter", "Legal", "A4", "A3", "A5", "Executive")
private val BREAKS = listOf("Page" to "page", "Column" to "column", "Text wrapping" to "textWrapping", "Next page (section)" to "nextPage", "Continuous (section)" to "continuous", "Even page (section)" to "evenPage", "Odd page (section)" to "oddPage")
private val GAPS = listOf(0, 6, 12, 18, 24, 36)
private val INDENTS = listOf("0\"" to 0, "0.25\"" to 18, "0.5\"" to 36, "0.75\"" to 54, "1\"" to 72)
private val POSITIONS = listOf(
    "Top left" to "topLeft", "Top center" to "topCenter", "Top right" to "topRight",
    "Middle left" to "middleLeft", "Center" to "middleCenter", "Middle right" to "middleRight",
    "Bottom left" to "bottomLeft", "Bottom center" to "bottomCenter", "Bottom right" to "bottomRight",
)
private val THEME_NAMES = listOf("Craft", "Studio", "Gallery", "Atelier", "Darkroom", "Sketchbook")
private val STYLE_SETS = listOf("Default" to "default", "Basic" to "basic", "Lines" to "lines", "Shaded" to "shaded", "Casual" to "casual", "Centered" to "centered", "Minimalist" to "minimalist", "Title" to "title")
private val PARA_SPACINGS = listOf("Default" to "default", "None" to "none", "Compact" to "compact", "Tight" to "tight", "Open" to "open", "Relaxed" to "relaxed", "Double" to "double")
private val FONT_PAIRS = listOf("Georgia" to "Georgia", "Helvetica Neue" to "Helvetica Neue", "Palatino" to "Palatino", "Futura / Avenir Next" to "Futura|Avenir Next", "Gill Sans" to "Gill Sans")

@Composable
internal fun InsertGroups(ui: EditorUiState, d: Dispatcher) {
    fun on(id: String) = ui.isEnabled(id)
    var linkDialog by remember { mutableStateOf(false) }

    GroupBox("Pages") {
        RBtn("Cover page", Icons.Default.Article, on("insert.coverPage")) { d.id("insert.coverPage") }
        RBtn("Blank page", Icons.Default.NoteAdd, on("insert.blankPage")) { d.id("insert.blankPage") }
        RBtn("Page break", Icons.Default.InsertPageBreak, on("insert.pageBreak")) { d.id("insert.pageBreak") }
    }
    GroupBox("Tables") {
        TableGridBtn(on("insert.table"), onDialog = { d.vm.openPanel("insertTable") }) { r, c -> d.id("insert.table", mapOf("rows" to r, "cols" to c)) }
        MenuBtn(
            "More", Icons.Default.MoreHoriz,
            listOf(
                MenuEntry("Quick tables…", on("table.quick")) { d.id("table.quick") },
                MenuEntry("Convert text to table", on("table.fromText")) { d.id("table.fromText") },
                MenuEntry("Spreadsheet", on("insert.spreadsheet")) { d.id("insert.spreadsheet") },
            ),
        )
        // Draw Table needs a pen stroke on the page (use the Draw tab's table tools), so it is not offered here.
        MoreMenu("Insert", "Tables", setOf("insert.table", "table.quick", "table.fromText", "insert.spreadsheet", "table.draw"), ui, d)
    }
    // Illustrations: every item opens something that works on a phone. Each picker collects its own values
    // (the engine only asks for a dialog when run bare, which used to show nothing or a block of JSON).
    GroupBox("Illustrations") {
        SplitBtn(
            "Pictures", Icons.Default.Image, on("insert.picture"),
            listOf(MenuEntry("From this device…", on("insert.picture")) { d.id("insert.picture") }, MenuEntry("Drawing canvas", on("insert.canvas")) { d.id("insert.canvas") }),
        ) { d.id("insert.picture") }
        ShapeGalleryBtn(on("insert.shape")) { k -> d.id("insert.shape", mapOf("kind" to k)) }
        RBtn("Icons", Icons.Default.EmojiSymbols, on("insert.icon")) { d.vm.openPanel("icons") }
        RBtn("SmartArt", Icons.Default.AccountTree, on("insert.smartArt")) { d.vm.openPanel("smartArt") }
        RBtn("Chart", Icons.Default.BarChart, on("insert.chart")) { d.vm.openPanel("chart") }
        MoreMenu("Insert", "Illustrations", setOf("insert.picture", "insert.shape", "insert.icon", "insert.smartArt", "insert.chart", "insert.canvas", "insert.onlinePicture", "insert.model3d", "insert.screenshot"), ui, d)
    }
    GroupBox("Links") {
        RBtn("Link", Icons.Default.Link, on("insert.link")) { linkDialog = true }
        MoreMenu("Insert", "Links", setOf("insert.link"), ui, d)
    }
    GroupBox("Header & Footer") {
        MenuBtn("Header", null, listOf("Blank" to "blank", "Blank (three columns)" to "blankThree", "Title" to "title").map { (n, k) -> MenuEntry(n) { d.id("insert.header", mapOf("preset" to k)) } }, on("insert.header"), cmd = ui.ribbon?.byId?.get("insert.header"))
        MenuBtn("Footer", null, listOf("Blank" to "blank", "Blank (three columns)" to "blankThree", "Page number" to "pageNumber").map { (n, k) -> MenuEntry(n) { d.id("insert.footer", mapOf("preset" to k)) } }, on("insert.footer"), cmd = ui.ribbon?.byId?.get("insert.footer"))
        MenuBtn(
            "Page number", Icons.Default.Numbers,
            listOf("Top of page" to "top", "Bottom of page" to "bottom", "Current position" to "current").flatMap { (pn, pos) ->
                listOf("left", "center", "right").map { al -> MenuEntry("$pn · $al") { d.id("insert.pageNumber", mapOf("position" to pos, "align" to al)) } }
            },
            on("insert.pageNumber"),
        )
        MoreMenu("Insert", "Header & Footer", setOf("insert.header", "insert.footer", "insert.pageNumber"), ui, d)
    }
    GroupBox("Text") {
        RBtn("Text box", null, on("insert.textBox"), cmd = ui.ribbon?.byId?.get("insert.textBox")) { d.id("insert.textBox") }
        RBtn("Date & time", null, on("insert.dateTime"), cmd = ui.ribbon?.byId?.get("insert.dateTime")) { d.id("insert.dateTime") }
        MoreMenu("Insert", "Text", setOf("insert.textBox", "insert.dateTime"), ui, d)
    }
    GroupBox("Symbols") {
        SymbolGalleryBtn(on("insert.symbol"), SYMBOLS) { s -> d.id("insert.symbol", mapOf("char" to s)) }
        RBtn("Equation", null, on("insert.equation"), cmd = ui.ribbon?.byId?.get("insert.equation")) { d.id("insert.equation") }
        MoreMenu("Insert", "Symbols", setOf("insert.symbol", "insert.equation"), ui, d)
    }

    if (linkDialog) LinkDialog(onDismiss = { linkDialog = false }) { url, text ->
        linkDialog = false
        d.id("insert.link", buildMap<String, Any?> { put("url", url); if (text.isNotBlank()) put("text", text) })
    }
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onOk: (String, String) -> Unit) {
    var url by remember { mutableStateOf("https://") }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Insert link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Address") })
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Text to display (optional)") })
            }
        },
        confirmButton = { TextButton({ onOk(url.trim(), text.trim()) }, enabled = url.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun LayoutGroups(ui: EditorUiState, d: Dispatcher) {
    fun on(id: String) = ui.isEnabled(id)
    GroupBox("Page Setup") {
        MenuBtn("Margins", null, MARGINS.map { (n, k) -> MenuEntry(n) { d.id("layout.margins", mapOf("preset" to k)) } }, on("layout.margins"), cmd = ui.ribbon?.byId?.get("layout.margins"))
        MenuBtn("Orientation", null, listOf("Portrait" to "portrait", "Landscape" to "landscape").map { (n, k) -> MenuEntry(n) { d.id("layout.orientation", mapOf("value" to k)) } }, on("layout.orientation"), cmd = ui.ribbon?.byId?.get("layout.orientation"))
        MenuBtn("Size", null, SIZES_PAGE.map { n -> MenuEntry(n) { d.id("layout.size", mapOf("name" to n)) } }, on("layout.size"), cmd = ui.ribbon?.byId?.get("layout.size"))
        MenuBtn(
            "Columns", Icons.Default.ViewColumn,
            listOf(MenuEntry("One") { d.id("layout.columns", mapOf("count" to 1)) }, MenuEntry("Two") { d.id("layout.columns", mapOf("count" to 2)) }, MenuEntry("Three") { d.id("layout.columns", mapOf("count" to 3)) },
                MenuEntry("Left") { d.id("layout.columns", mapOf("count" to 2, "preset" to "left")) }, MenuEntry("Right") { d.id("layout.columns", mapOf("count" to 2, "preset" to "right")) }),
            on("layout.columns"),
        )
        MenuBtn("Breaks", null, BREAKS.map { (n, k) -> MenuEntry(n) { d.id("layout.break", mapOf("kind" to k)) } }, on("layout.break"), cmd = ui.ribbon?.byId?.get("layout.break"))
        MenuBtn(
            "Line numbers", Icons.Default.FormatListNumbered,
            listOf("None" to "none", "Continuous" to "continuous", "Restart each page" to "restartPage", "Restart each section" to "restartSection")
                .map { (n, k) -> MenuEntry(n, on("layout.lineNumbers")) { d.id("layout.lineNumbers", mapOf("value" to k)) } },
            on("layout.lineNumbers"),
        )
        MenuBtn(
            "Hyphenation", Icons.Default.TextFormat,
            listOf(MenuEntry("None", on("layout.hyphenation")) { d.id("layout.hyphenation", mapOf("value" to false)) }, MenuEntry("Automatic", on("layout.hyphenation")) { d.id("layout.hyphenation", mapOf("value" to true)) }),
            on("layout.hyphenation"),
        )
        RBtn("Page setup…", Icons.Default.Settings, on("layout.pageSetup")) { d.vm.openPanel("pageSetup") }
        MoreMenu("Layout", "Page Setup", setOf("layout.margins", "layout.orientation", "layout.size", "layout.columns", "layout.break", "layout.lineNumbers", "layout.hyphenation", "layout.pageSetup"), ui, d)
    }
    GroupBox("Paragraph") {
        MenuBtn("Indent left", Icons.Default.FormatIndentIncrease, INDENTS.map { (n, pt) -> MenuEntry(n) { d.id("para.indents", mapOf("left" to pt)) } }, on("para.indents"))
        MenuBtn("Indent right", Icons.Default.FormatIndentDecrease, INDENTS.map { (n, pt) -> MenuEntry(n) { d.id("para.indents", mapOf("right" to pt)) } }, on("para.indents"))
        MenuBtn("Before", Icons.Default.VerticalAlignTop, GAPS.map { g -> MenuEntry("$g pt") { d.id("para.spacing", mapOf("before" to g)) } }, on("para.spacing"))
        MenuBtn("After", Icons.Default.VerticalAlignBottom, GAPS.map { g -> MenuEntry("$g pt") { d.id("para.spacing", mapOf("after" to g)) } }, on("para.spacing"))
        MenuBtn("Line spacing", Icons.Default.FormatLineSpacing, lineSpacingEntries(d), on("para.lineSpacing"))
        RBtn("Paragraph…", Icons.Default.Settings, on("para.dialog")) { d.vm.openPanel("paragraph") }
        MoreMenu("Layout", "Paragraph", setOf("para.indents", "para.spacing", "para.lineSpacing", "para.dialog"), ui, d)
    }
    // Arrange: the same object commands the Picture/Shape tabs use, in one place, as menus with the choices spelled out.
    GroupBox("Arrange") {
        MenuBtn("Position", Icons.Default.PictureInPicture, POSITIONS.map { (n, k) -> MenuEntry(n, on("arrange.position")) { d.id("arrange.position", mapOf("preset" to k)) } }, on("arrange.position"))
        MenuBtn("Wrap text", Icons.Default.WrapText, wrapEntries(d), on("arrange.wrap"))
        RBtn("Bring forward", Icons.Default.FlipToFront, on("arrange.bringForward")) { d.id("arrange.bringForward") }
        RBtn("Send backward", Icons.Default.FlipToBack, on("arrange.sendBackward")) { d.id("arrange.sendBackward") }
        MenuBtn("Align", Icons.Default.AlignHorizontalLeft, listOf("Left" to "left", "Center" to "center", "Right" to "right").map { (n, k) -> MenuEntry(n, on("arrange.align")) { d.id("arrange.align", mapOf("value" to k)) } }, on("arrange.align"))
        RBtn("Group", Icons.Default.Group, on("arrange.group")) { d.id("arrange.group") }
        MenuBtn("Rotate", Icons.Default.RotateRight, rotateEntries(d), on("arrange.rotate"))
        RBtn("Selection pane", Icons.Default.Layers, on("arrange.selectionPane")) { d.id("arrange.selectionPane") }
        MoreMenu("Layout", "Arrange", setOf("arrange.position", "arrange.wrap", "arrange.bringForward", "arrange.sendBackward", "arrange.align", "arrange.group", "arrange.rotate", "arrange.selectionPane"), ui, d)
    }
}

private val WATERMARKS = listOf("CONFIDENTIAL", "DRAFT", "DO NOT COPY", "SAMPLE", "URGENT", "ASAP")

/** Design › Page Background: page colour, page borders and watermark, in the pickers Word uses. */
@Composable
internal fun DesignGroups(ui: EditorUiState, d: Dispatcher) {
    var watermark by remember { mutableStateOf(false) }
    fun on(id: String) = ui.isEnabled(id)
    GroupBox("Document Formatting") {
        MenuBtn("Themes", Icons.Default.Palette, THEME_NAMES.map { n -> MenuEntry(n, on("design.theme")) { d.id("design.theme", mapOf("name" to n)) } }, on("design.theme"))
        MenuBtn("Colors", Icons.Default.ColorLens, THEME_NAMES.map { n -> MenuEntry(n, on("design.themeColors")) { d.id("design.themeColors", mapOf("name" to n)) } }, on("design.themeColors"))
        MenuBtn(
            "Fonts", Icons.Default.FontDownload,
            FONT_PAIRS.map { (n, v) -> MenuEntry(n, on("design.themeFonts")) { d.id("design.themeFonts", buildMap<String, Any?> { put("heading", v.substringBefore('|')); put("body", v.substringAfter('|')) }) } },
            on("design.themeFonts"),
        )
        MenuBtn("Style set", Icons.Default.Style, STYLE_SETS.map { (n, k) -> MenuEntry(n, on("design.styleSet")) { d.id("design.styleSet", mapOf("name" to k)) } }, on("design.styleSet"))
        MenuBtn("Paragraph spacing", Icons.Default.FormatLineSpacing, PARA_SPACINGS.map { (n, k) -> MenuEntry(n, on("design.paragraphSpacing")) { d.id("design.paragraphSpacing", mapOf("value" to k)) } }, on("design.paragraphSpacing"))
        RBtn("Set as default", Icons.Default.CheckCircle, on("design.setDefault")) { d.id("design.setDefault") }
        MoreMenu("Design", "Document Formatting", setOf("design.theme", "design.themeColors", "design.themeFonts", "design.styleSet", "design.paragraphSpacing", "design.setDefault"), ui, d)
    }
    GroupBox("Page Background") {
        MenuBtn(
            "Watermark", null,
            WATERMARKS.map { w -> MenuEntry(w) { d.id("design.watermark", mapOf("text" to w, "diagonal" to true, "color" to "C0C0C0")) } } +
                listOf(MenuEntry("Custom watermark…") { watermark = true }, MenuEntry("Remove watermark") { d.id("design.watermark", mapOf("remove" to true)) }),
            ui.isEnabled("design.watermark"), cmd = ui.ribbon?.byId?.get("design.watermark"),
        )
        ColorMenuBtn("Page Color", ui) { c -> d.id("design.pageColor", mapOf("color" to if (c == "auto") org.json.JSONObject.NULL else c)) }
        RBtn("Page borders", Icons.Default.BorderAll, ui.isEnabled("design.pageBorders")) { d.vm.openPanel("pageBorders") }
    }
    if (watermark) TextPromptDialog("Watermark", "Text", "CONFIDENTIAL", { watermark = false }) {
        watermark = false; d.id("design.watermark", mapOf("text" to it, "diagonal" to true, "color" to "C0C0C0"))
    }
}

/** References: contents, notes, captions and the index, each command as a button with a plain action. */
@Composable
internal fun ReferencesGroups(ui: EditorUiState, d: Dispatcher) {
    fun on(id: String) = ui.isEnabled(id)
    GroupBox("Table of Contents") {
        RBtn("Table of contents", Icons.Default.Toc, on("references.toc")) { d.id("references.toc") }
        MenuBtn(
            "Add text", Icons.Default.Add,
            listOf("Do not show in table" to 0, "Level 1" to 1, "Level 2" to 2, "Level 3" to 3).map { (n, l) -> MenuEntry(n, on("references.addText")) { d.id("references.addText", mapOf("level" to l)) } },
            on("references.addText"),
        )
        RBtn("Update table", Icons.Default.Sync, on("references.updateToc")) { d.id("references.updateToc") }
        RBtn("Remove", Icons.Default.Delete, on("references.removeToc")) { d.id("references.removeToc") }
    }
    GroupBox("Footnotes") {
        RBtn("Insert footnote", Icons.Default.Notes, on("references.footnote")) { d.id("references.footnote") }
        RBtn("Insert endnote", Icons.Default.Notes, on("references.endnote")) { d.id("references.endnote") }
        RBtn("Next footnote", Icons.Default.ArrowDownward, on("references.nextFootnote")) { d.id("references.nextFootnote") }
        RBtn("Show notes", Icons.Default.Visibility, on("references.notes")) { d.id("references.notes") }
        MoreMenu("References", "Footnotes", setOf("references.footnote", "references.endnote", "references.nextFootnote", "references.notes"), ui, d)
    }
    GroupBox("Captions") {
        RBtn("Insert caption", Icons.Default.Title, on("references.caption")) { d.vm.openPanel("caption") }
        RBtn("Table of figures", Icons.Default.ListAlt, on("references.tableOfFigures")) { d.id("references.tableOfFigures") }
        RBtn("Update table", Icons.Default.Sync, on("references.updateFigures")) { d.id("references.updateFigures") }
        MoreMenu("References", "Captions", setOf("references.caption", "references.tableOfFigures", "references.updateFigures"), ui, d)
    }
    GroupBox("Index") {
        RBtn("Mark entry", Icons.Default.Bookmarks, on("references.markEntry")) { d.id("references.markEntry") }
        RBtn("Insert index", Icons.Default.ImportContacts, on("references.index")) { d.id("references.index") }
        RBtn("Update index", Icons.Default.Sync, on("references.updateIndex")) { d.id("references.updateIndex") }
        MoreMenu("References", "Index", setOf("references.markEntry", "references.index", "references.updateIndex"), ui, d)
    }
}
