package org.wordcraft.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.wordcraft.android.editor.EditorUiState

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

@Composable
internal fun InsertGroups(ui: EditorUiState, d: Dispatcher) {
    fun on(id: String) = ui.isEnabled(id)
    var linkDialog by remember { mutableStateOf(false) }

    GroupBox("Pages") {
        RBtn("Cover page", null, on("insert.coverPage"), cmd = ui.ribbon?.byId?.get("insert.coverPage")) { d.id("insert.coverPage") }
        RBtn("Page break", Icons.Default.InsertPageBreak, on("insert.pageBreak")) { d.id("insert.pageBreak") }
        MoreMenu("Insert", "Pages", setOf("insert.coverPage", "insert.pageBreak"), ui, d)
    }
    GroupBox("Tables") {
        TableGridBtn(on("insert.table")) { r, c -> d.id("insert.table", mapOf("rows" to r, "cols" to c)) }
        MoreMenu("Insert", "Tables", setOf("insert.table"), ui, d)
    }
    GroupBox("Illustrations") {
        RBtn("Pictures", Icons.Default.Image, on("insert.picture")) { d.id("insert.picture") }
        MenuBtn("Shapes", null, SHAPES.map { (n, k) -> MenuEntry(n, on("insert.shape")) { d.id("insert.shape", mapOf("kind" to k)) } }, on("insert.shape"), cmd = ui.ribbon?.byId?.get("insert.shape"))
        MoreMenu("Insert", "Illustrations", setOf("insert.picture", "insert.shape"), ui, d)
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
        MenuBtn("Symbol", null, SYMBOLS.map { s -> MenuEntry(s) { d.id("insert.symbol", mapOf("char" to s)) } }, on("insert.symbol"), cmd = ui.ribbon?.byId?.get("insert.symbol"))
        RBtn("Equation", null, on("insert.equation"), cmd = ui.ribbon?.byId?.get("insert.equation")) { d.id("insert.equation") }
        MoreMenu("Insert", "Symbols", setOf("insert.symbol", "insert.equation"), ui, d)
    }

    if (linkDialog) LinkDialog(onDismiss = { linkDialog = false }) { url, text ->
        linkDialog = false
        d.id("insert.link", buildMap { put("url", url); if (text.isNotBlank()) put("text", text) })
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
        MoreMenu("Layout", "Page Setup", setOf("layout.margins", "layout.orientation", "layout.size", "layout.columns", "layout.break"), ui, d)
    }
    GroupBox("Paragraph") {
        MenuBtn("Before", null, GAPS.map { g -> MenuEntry("$g pt") { d.id("para.spacing", mapOf("before" to g)) } }, on("para.spacing"), cmd = ui.ribbon?.byId?.get("para.spacing"))
        MenuBtn("After", null, GAPS.map { g -> MenuEntry("$g pt") { d.id("para.spacing", mapOf("after" to g)) } }, on("para.spacing"), cmd = ui.ribbon?.byId?.get("para.spacing"))
        MenuBtn("Line spacing", Icons.Default.FormatLineSpacing, lineSpacingEntries(d), on("para.lineSpacing"))
        MoreMenu("Layout", "Paragraph", setOf("para.spacing", "para.lineSpacing"), ui, d)
    }
}
