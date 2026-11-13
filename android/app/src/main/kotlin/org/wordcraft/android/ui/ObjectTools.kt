package org.wordcraft.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import org.wordcraft.android.editor.EditorUiState
import org.wordcraft.android.editor.EditorViewModel

// ---------------------------------------------------------------------------------------------
// Option lists shared by the floating bars and the contextual ribbon tabs
// ---------------------------------------------------------------------------------------------

private val WRAPS = listOf("In line with text" to "inline", "Square" to "square", "Tight" to "tight", "Through" to "through", "Top and bottom" to "topAndBottom", "Behind text" to "behindText", "In front of text" to "inFrontOfText")
private val PIC_STYLES = listOf("Simple frame" to "simpleFrame", "Thick frame" to "thickFrame", "Rounded corners" to "rounded", "Soft edges" to "softEdge", "Shadow" to "shadow")
private val SIZE_PRESETS = listOf(25, 50, 75, 100, 150, 200)
private val FALLBACK_TABLE_STYLES = listOf("Table Grid", "Plain Table 1", "Grid Table 1 Light", "Grid Table 4 – Blue", "Grid Table 4 – Orange", "Grid Table 4 – Green", "Grid Table 4 – Purple", "List Table 3 – Blue")
private val FORMULAS = listOf("SUM(ABOVE)", "SUM(LEFT)", "AVERAGE(ABOVE)", "AVERAGE(LEFT)", "COUNT(ABOVE)", "MAX(ABOVE)", "MIN(ABOVE)", "PRODUCT(ABOVE)")
private val CELL_ALIGN = listOf("Top left" to "topLeft", "Top center" to "topCenter", "Top right" to "topRight", "Center left" to "centerLeft", "Center" to "center", "Center right" to "centerRight", "Bottom left" to "bottomLeft", "Bottom center" to "bottomCenter", "Bottom right" to "bottomRight")
private val LOOK = listOf("Header row" to "headerRow", "Total row" to "totalRow", "Banded rows" to "bandedRows", "First column" to "firstColumn", "Last column" to "lastColumn", "Banded columns" to "bandedColumns")

/** Border presets for tables and paragraphs. */
internal val BORDER_KINDS = listOf("None" to "none", "Box" to "outside", "All" to "all", "Inside" to "inside", "Top" to "top", "Bottom" to "bottom", "Left" to "left", "Right" to "right")
internal val BORDER_STYLES = listOf("Single" to "single", "Double" to "double", "Dashed" to "dashed", "Dotted" to "dotted", "Dot-dash" to "dotDash", "Thick" to "thick", "Wave" to "wave", "Triple" to "triple")
internal val BORDER_WIDTHS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2.25f, 3f, 4.5f, 6f)
private val BORDER_COLORS = listOf("000000", "808080", "C00000", "FF0000", "FFC000", "00B050", "0070C0", "7030A0", "2B579A")

private fun hex(h: String) = Color(0xFF000000 or h.toLong(16))

// ---------------------------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------------------------

/** Word's Borders and Shading: pick a setting, a line style (paragraphs), width and colour, with a live preview. */
@Composable
fun BordersDialog(forTable: Boolean, onDismiss: () -> Unit, onApply: (kind: String, style: String, width: Float, color: String) -> Unit) {
    var kind by remember { mutableStateOf("outside") }
    var style by remember { mutableStateOf("single") }
    var width by remember { mutableFloatStateOf(1f) }
    var color by remember { mutableStateOf("000000") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (forTable) "Table borders" else "Paragraph borders") },
        text = {
            Column(Modifier.verticalScrollIfNeeded(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BorderPreview(kind, style, width, hex(color))
                Label("Setting")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BORDER_KINDS.forEach { (n, k) -> FilterChip(kind == k, { kind = k }, { Text(n) }) }
                }
                if (!forTable) {
                    Label("Style")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BORDER_STYLES.forEach { (n, k) -> FilterChip(style == k, { style = k }, { Text(n) }) }
                    }
                }
                Label("Width")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BORDER_WIDTHS.forEach { w -> FilterChip(width == w, { width = w }, { Text("$w pt") }) }
                }
                Label("Color")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    BORDER_COLORS.forEach { c ->
                        Box(
                            Modifier.size(28.dp).background(hex(c), CircleShape)
                                .border(if (color == c) 3.dp else 1.dp, if (color == c) WordBlue else Color.Gray, CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton({ onApply(kind, style, width, color) }) { Text("Apply") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Modifier.verticalScrollIfNeeded(): Modifier = this.then(Modifier.verticalScroll(rememberScrollState()))

@Composable
private fun Label(t: String) = Text(t, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WordBlue)

/** A sample box showing roughly what the chosen border will look like. */
@Composable
private fun BorderPreview(kind: String, style: String, width: Float, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(84.dp).background(Color.White, RoundedCornerShape(6.dp)).border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(6.dp))) {
        val pad = 18.dp.toPx(); val l = pad; val t = pad * 0.8f; val r = size.width - pad; val b = size.height - pad * 0.8f
        val stroke = (width * 2.2f).coerceAtLeast(1.5f) * if (style == "thick") 2f else 1f
        val dash = when (style) {
            "dashed" -> PathEffect.dashPathEffect(floatArrayOf(stroke * 4, stroke * 2.5f))
            "dotted" -> PathEffect.dashPathEffect(floatArrayOf(stroke, stroke * 1.8f))
            "dotDash" -> PathEffect.dashPathEffect(floatArrayOf(stroke * 4, stroke * 1.5f, stroke, stroke * 1.5f))
            else -> null
        }
        fun side(a: Offset, c: Offset, dx: Float, dy: Float) {
            val n = when (style) { "double" -> 2; "triple" -> 3; else -> 1 }
            val s = if (n > 1) stroke * 0.6f else stroke
            for (i in 0 until n) {
                val k = (i - (n - 1) / 2f) * s * 2.2f
                drawLine(color, Offset(a.x + dx * k, a.y + dy * k), Offset(c.x + dx * k, c.y + dy * k), s, pathEffect = dash)
            }
        }
        val top = kind in setOf("outside", "all", "top"); val bottom = kind in setOf("outside", "all", "bottom")
        val left = kind in setOf("outside", "all", "left"); val right = kind in setOf("outside", "all", "right")
        if (top) side(Offset(l, t), Offset(r, t), 0f, 1f)
        if (bottom) side(Offset(l, b), Offset(r, b), 0f, 1f)
        if (left) side(Offset(l, t), Offset(l, b), 1f, 0f)
        if (right) side(Offset(r, t), Offset(r, b), 1f, 0f)
        if (kind == "inside" || kind == "all") drawLine(color.copy(alpha = 0.7f), Offset(l + 10, (t + b) / 2), Offset(r - 10, (t + b) / 2), stroke * 0.6f, pathEffect = dash)
        val ink = Color(0xFFCDD2DA)
        for (i in 0..2) drawLine(ink, Offset(l + 14, t + 14 + i * 14f), Offset(r - 14 - if (i == 2) 60 else 0, t + 14 + i * 14f), 5f)
    }
}

@Composable
fun TextPromptDialog(title: String, label: String, initial: String = "", onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(v, { v = it }, Modifier.fillMaxWidth(), label = { Text(label) }) },
        confirmButton = { TextButton({ onOk(v.trim()) }, enabled = v.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Crop: how much to trim from each side, as a share of the picture. */
@Composable
fun CropDialog(onDismiss: () -> Unit, onApply: (left: Float, top: Float, right: Float, bottom: Float) -> Unit) {
    var l by remember { mutableFloatStateOf(0f) }
    var t by remember { mutableFloatStateOf(0f) }
    var r by remember { mutableFloatStateOf(0f) }
    var b by remember { mutableFloatStateOf(0f) }
    @Composable
    fun row(name: String, v: Float, set: (Float) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.width(60.dp), fontSize = 13.sp)
            Slider(v, set, Modifier.weight(1f), valueRange = 0f..0.5f)
            Text("${(v * 100).toInt()}%", Modifier.width(40.dp), fontSize = 12.sp)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Crop picture") },
        text = { Column { row("Left", l) { l = it }; row("Top", t) { t = it }; row("Right", r) { r = it }; row("Bottom", b) { b = it } } },
        confirmButton = { TextButton({ onApply(l, t, r, b) }) { Text("Crop") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Picture / shape tools
// ---------------------------------------------------------------------------------------------

internal fun wrapEntries(d: Dispatcher) = WRAPS.map { (n, w) -> MenuEntry(n) { d.id("arrange.wrap", mapOf("wrap" to w)) } }
internal fun rotateEntries(d: Dispatcher) = listOf(
    MenuEntry("Rotate right 90°") { d.id("arrange.rotate", mapOf("direction" to "right")) },
    MenuEntry("Rotate left 90°") { d.id("arrange.rotate", mapOf("direction" to "left")) },
    MenuEntry("Flip horizontal") { d.id("arrange.rotate", mapOf("direction" to "flipHorizontal")) },
    MenuEntry("Flip vertical") { d.id("arrange.rotate", mapOf("direction" to "flipVertical")) },
)
internal fun sizeEntries(d: Dispatcher) =
    SIZE_PRESETS.map { p -> MenuEntry(if (p == 100) "Original size" else "$p% of original") { d.id("picture.size", mapOf("original" to true, "scale" to p)) } } +
        listOf(MenuEntry("Half the current size") { d.id("picture.size", mapOf("scale" to 50)) }, MenuEntry("Double the current size") { d.id("picture.size", mapOf("scale" to 200)) })
internal fun pictureStyleEntries(d: Dispatcher) = PIC_STYLES.map { (n, s) -> MenuEntry(n) { d.id("picture.style", mapOf("style" to s)) } }

/** The bar that appears over the page while a picture or shape is selected. */
@Composable
fun ObjectBar(ui: EditorUiState, d: Dispatcher, vm: EditorViewModel, modifier: Modifier = Modifier) {
    val obj = ui.obj ?: return
    var crop by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var borderW by remember { mutableIntStateOf(3) }
    var borderC by remember { mutableStateOf("000000") }
    fun on(id: String) = ui.isEnabled(id)
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = Color.White, shadowElevation = 10.dp, tonalElevation = 2.dp) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniMenu(Icons.Default.WrapText, "Wrap text", true, wrapEntries(d))
            MiniMenu(Icons.Default.RotateRight, "Rotate", true, rotateEntries(d))
            if (obj.picture) {
                MiniMenu(Icons.Default.AspectRatio, "Size", true, sizeEntries(d))
                MiniToggle(Icons.Default.Crop, "Crop", false) { crop = true }
                MiniMenu(Icons.Default.Style, "Picture style", true, pictureStyleEntries(d))
                ColorMenu("Picture border", Icons.Default.BorderColor, true, borderC, Color.Gray) { c -> borderC = if (c == "auto") "000000" else c; d.id("picture.border", mapOf("color" to borderC, "width" to borderW)) }
                MiniMenu(Icons.Default.LineWeight, "Border width", true, listOf(1, 2, 3, 5, 8, 12).map { w -> MenuEntry("$w px") { borderW = w; d.id("picture.border", mapOf("color" to borderC, "width" to w)) } })
            } else {
                ColorMenu("Shape fill", Icons.Default.FormatColorFill, true, null, Color(0xFF90CAF9)) { c -> d.id("shape.fill", mapOf("color" to c)) }
                ColorMenu("Shape outline", Icons.Default.BorderColor, true, null, Color.Gray) { c -> d.id("shape.outline", mapOf("color" to c)) }
            }
            MiniToggle(Icons.Default.FlipToFront, "Bring forward", false, on("arrange.bringForward")) { d.id("arrange.bringForward") }
            MiniToggle(Icons.Default.FlipToBack, "Send backward", false, on("arrange.sendBackward")) { d.id("arrange.sendBackward") }
            MiniToggle(Icons.Default.Accessibility, "Alt text", false) { alt = true }
            if (obj.picture) MiniToggle(Icons.Default.Restore, "Reset picture", false, on("picture.reset")) { d.id("picture.reset") }
            MiniToggle(Icons.Default.Delete, "Delete", false) { d.id("text.backspace") }
        }
    }
    if (crop) CropDialog({ crop = false }) { l, t, r, b -> crop = false; d.id("picture.crop", mapOf("left" to l, "top" to t, "right" to r, "bottom" to b)) }
    if (alt) TextPromptDialog("Alt text", "Describe this for screen readers", onDismiss = { alt = false }) { alt = false; d.id("object.altText", mapOf("text" to it)) }
}

// ---------------------------------------------------------------------------------------------
// Table tools
// ---------------------------------------------------------------------------------------------

internal fun tableInsertEntries(d: Dispatcher) = listOf(
    MenuEntry("Insert row above") { d.id("table.insertRowAbove") }, MenuEntry("Insert row below") { d.id("table.insertRowBelow") },
    MenuEntry("Insert column left") { d.id("table.insertColumnLeft") }, MenuEntry("Insert column right") { d.id("table.insertColumnRight") },
)
internal fun tableDeleteEntries(d: Dispatcher) = listOf(
    MenuEntry("Delete rows") { d.id("table.deleteRow") }, MenuEntry("Delete columns") { d.id("table.deleteColumn") },
    MenuEntry("Delete cells") { d.id("table.deleteCells") }, MenuEntry("Delete table") { d.id("table.deleteTable") },
)
internal fun tableSelectEntries(d: Dispatcher) = listOf(
    MenuEntry("Select cell") { d.id("table.selectCell") }, MenuEntry("Select row") { d.id("table.selectRow") },
    MenuEntry("Select column") { d.id("table.selectColumn") }, MenuEntry("Select table") { d.id("table.selectTable") },
)
internal fun tableAlignEntries(d: Dispatcher) = CELL_ALIGN.map { (n, v) -> MenuEntry(n) { d.id("table.cellAlign", mapOf("value" to v)) } }
internal fun tableAutofitEntries(d: Dispatcher) = listOf(
    MenuEntry("AutoFit to contents") { d.id("table.autofit", mapOf("mode" to "contents")) },
    MenuEntry("AutoFit to window") { d.id("table.autofit", mapOf("mode" to "window")) },
    MenuEntry("Fixed column width") { d.id("table.autofit", mapOf("mode" to "fixed")) },
    MenuEntry("Distribute columns evenly") { d.id("table.distributeColumns") },
    MenuEntry("Distribute rows evenly") { d.id("table.distributeRows") },
)
internal fun tableLookEntries(d: Dispatcher) = LOOK.flatMap { (n, k) ->
    listOf(MenuEntry("$n on") { d.id("table.look", mapOf(k to true)) }, MenuEntry("$n off") { d.id("table.look", mapOf(k to false)) })
}
internal fun tableStyleEntries(ui: EditorUiState, d: Dispatcher) =
    (ui.tableStyles.ifEmpty { FALLBACK_TABLE_STYLES }).map { n -> MenuEntry(n) { d.id("table.style", mapOf("style" to n)) } }
internal fun tableFormulaEntries(d: Dispatcher, custom: () -> Unit) =
    FORMULAS.map { f -> MenuEntry("=$f") { d.id("table.formula", mapOf("formula" to "=$f")) } } + MenuEntry("Custom formula…", onClick = custom)

/** Insert, delete, merge, size, borders, shading and formulas for the table under the caret. */
@Composable
fun TableBar(ui: EditorUiState, d: Dispatcher, modifier: Modifier = Modifier) {
    var borders by remember { mutableStateOf(false) }
    var formula by remember { mutableStateOf(false) }
    fun on(id: String) = ui.isEnabled(id)
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = Color.White, shadowElevation = 10.dp, tonalElevation = 2.dp) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniMenu(Icons.Default.AddBox, "Insert rows and columns", true, tableInsertEntries(d))
            MiniMenu(Icons.Default.Delete, "Delete", true, tableDeleteEntries(d))
            MiniToggle(Icons.Default.CallMerge, "Merge cells", false, on("table.merge")) { d.id("table.merge") }
            MiniToggle(Icons.Default.CallSplit, "Split cells", false, on("table.split")) { ui.ribbon?.byId?.get("table.split")?.let { d(it) } }
            MiniToggle(Icons.Default.BorderAll, "Borders", false) { borders = true }
            ColorMenu("Cell shading", Icons.Default.FormatColorFill, true, null, Color(0xFFB0BEC5)) { c -> d.id("table.shading", mapOf("color" to if (c == "auto") JSONObject.NULL else c)) }
            MiniMenu(Icons.Default.GridOn, "Cell alignment", true, tableAlignEntries(d))
            MiniMenu(Icons.Default.AspectRatio, "Size", true, tableAutofitEntries(d) + listOfNotNull(
                ui.ribbon?.byId?.get("table.columnWidth")?.let { c -> MenuEntry("Column width…") { d(c) } },
                ui.ribbon?.byId?.get("table.rowHeight")?.let { c -> MenuEntry("Row height…") { d(c) } },
            ))
            MiniMenu(Icons.Default.TableChart, "Table style", true, tableStyleEntries(ui, d) + tableLookEntries(d))
            MiniMenu(Icons.Default.Functions, "Formula", on("table.formula"), tableFormulaEntries(d) { formula = true })
            MiniToggle(Icons.Default.SortByAlpha, "Sort", false, on("table.sort")) { ui.ribbon?.byId?.get("table.sort")?.let { d(it) } }
        }
    }
    if (borders) BordersDialog(true, { borders = false }) { k, _, w, c -> borders = false; d.id("table.borders", mapOf("kind" to k, "width" to w, "color" to c)) }
    if (formula) TextPromptDialog("Formula", "e.g. =SUM(ABOVE)", "=SUM(ABOVE)", { formula = false }) { formula = false; d.id("table.formula", mapOf("formula" to it)) }
}

// ---------------------------------------------------------------------------------------------
// Contextual ribbon tabs
// ---------------------------------------------------------------------------------------------

internal val CONTEXT_CURATED: Map<String, Set<String>> = mapOf(
    "Table Design" to setOf("Table Styles", "Table Style Options", "Borders"),
    "Table Layout" to setOf("Rows & Columns", "Merge", "Cell Size", "Alignment", "Data", "Table"),
    "Picture Format" to setOf("Size", "Picture Styles", "Arrange", "Accessibility"),
)

@Composable
internal fun TableDesignGroups(ui: EditorUiState, d: Dispatcher) {
    var borders by remember { mutableStateOf(false) }
    GroupBox("Table Styles") {
        MenuBtn("Format as table", Icons.Default.TableChart, tableStyleEntries(ui, d))
        ColorMenuBtn("Shading", ui) { c -> d.id("table.shading", mapOf("color" to if (c == "auto") JSONObject.NULL else c)) }
        MoreMenu("Table Design", "Table Styles", setOf("table.style", "table.shading"), ui, d)
    }
    GroupBox("Style Options") { MenuBtn("Options", Icons.Default.CheckBox, tableLookEntries(d)) }
    GroupBox("Borders") {
        RBtn("Borders", Icons.Default.BorderAll) { borders = true }
        MoreMenu("Table Design", "Borders", setOf("table.borders"), ui, d)
    }
    if (borders) BordersDialog(true, { borders = false }) { k, _, w, c -> borders = false; d.id("table.borders", mapOf("kind" to k, "width" to w, "color" to c)) }
}

@Composable
internal fun TableLayoutGroups(ui: EditorUiState, d: Dispatcher) {
    var formula by remember { mutableStateOf(false) }
    GroupBox("Table") {
        MenuBtn("Select", Icons.Default.SelectAll, tableSelectEntries(d))
        RBtn("Gridlines", null, ui.isEnabled("table.viewGridlines"), cmd = ui.ribbon?.byId?.get("table.viewGridlines")) { d.id("table.viewGridlines") }
        MoreMenu("Table Layout", "Table", setOf("table.viewGridlines", "table.selectCell", "table.selectRow", "table.selectColumn", "table.selectTable"), ui, d)
    }
    GroupBox("Rows & Columns") {
        MenuBtn("Insert", Icons.Default.AddBox, tableInsertEntries(d))
        MenuBtn("Delete", Icons.Default.Delete, tableDeleteEntries(d))
    }
    GroupBox("Merge") {
        RBtn("Merge cells", Icons.Default.CallMerge, ui.isEnabled("table.merge")) { d.id("table.merge") }
        RBtn("Split cells", Icons.Default.CallSplit, ui.isEnabled("table.split")) { ui.ribbon?.byId?.get("table.split")?.let { d(it) } }
        RBtn("Split table", null, ui.isEnabled("table.splitTable"), cmd = ui.ribbon?.byId?.get("table.splitTable")) { d.id("table.splitTable") }
    }
    GroupBox("Cell Size") {
        MenuBtn("AutoFit", Icons.Default.AspectRatio, tableAutofitEntries(d))
        ui.ribbon?.byId?.get("table.columnWidth")?.let { c -> RBtn("Column width", null, ui.isEnabled(c.id), cmd = c) { d(c) } }
        ui.ribbon?.byId?.get("table.rowHeight")?.let { c -> RBtn("Row height", null, ui.isEnabled(c.id), cmd = c) { d(c) } }
    }
    GroupBox("Alignment") {
        MenuBtn("Align cells", Icons.Default.GridOn, tableAlignEntries(d))
        MoreMenu("Table Layout", "Alignment", setOf("table.cellAlign"), ui, d)
    }
    GroupBox("Data") {
        MenuBtn("Formula", Icons.Default.Functions, tableFormulaEntries(d) { formula = true }, ui.isEnabled("table.formula"))
        ui.ribbon?.byId?.get("table.sort")?.let { c -> RBtn("Sort", Icons.Default.SortByAlpha, ui.isEnabled(c.id)) { d(c) } }
        MoreMenu("Table Layout", "Data", setOf("table.formula", "table.sort"), ui, d)
    }
    if (formula) TextPromptDialog("Formula", "e.g. =SUM(ABOVE)", "=SUM(ABOVE)", { formula = false }) { formula = false; d.id("table.formula", mapOf("formula" to it)) }
}

@Composable
internal fun PictureFormatGroups(ui: EditorUiState, d: Dispatcher) {
    var crop by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var bw by remember { mutableIntStateOf(3) }
    var bc by remember { mutableStateOf("000000") }
    GroupBox("Size") {
        MenuBtn("Size", Icons.Default.AspectRatio, sizeEntries(d))
        RBtn("Crop", Icons.Default.Crop, ui.isEnabled("picture.crop")) { crop = true }
    }
    GroupBox("Picture Styles") {
        MenuBtn("Styles", Icons.Default.Style, pictureStyleEntries(d))
        ColorMenuBtn("Border", ui) { c -> bc = if (c == "auto") "000000" else c; d.id("picture.border", mapOf("color" to bc, "width" to bw)) }
        MenuBtn("Weight", Icons.Default.LineWeight, listOf(1, 2, 3, 5, 8, 12).map { w -> MenuEntry("$w px") { bw = w; d.id("picture.border", mapOf("color" to bc, "width" to w)) } })
    }
    GroupBox("Arrange") {
        MenuBtn("Wrap text", Icons.Default.WrapText, wrapEntries(d))
        MenuBtn("Rotate", Icons.Default.RotateRight, rotateEntries(d))
        RBtn("Bring forward", Icons.Default.FlipToFront, ui.isEnabled("arrange.bringForward")) { d.id("arrange.bringForward") }
        RBtn("Send backward", Icons.Default.FlipToBack, ui.isEnabled("arrange.sendBackward")) { d.id("arrange.sendBackward") }
    }
    GroupBox("Accessibility") { RBtn("Alt text", Icons.Default.Accessibility) { alt = true } }
    if (crop) CropDialog({ crop = false }) { l, t, r, b -> crop = false; d.id("picture.crop", mapOf("left" to l, "top" to t, "right" to r, "bottom" to b)) }
    if (alt) TextPromptDialog("Alt text", "Describe this for screen readers", onDismiss = { alt = false }) { alt = false; d.id("object.altText", mapOf("text" to it)) }
}

/** A ribbon-sized colour button (icon over label) for contextual tabs. */
@Composable
internal fun ColorMenuBtn(label: String, ui: EditorUiState, onPick: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
        ColorMenu(label, Icons.Default.FormatColorFill, true, null, Color(0xFFB0BEC5), onPick)
        Text(label, fontSize = 10.sp)
    }
}

/** Page Borders: a box or single sides, line style, width and colour, for this section or the whole document. */
@Composable
fun PageBordersDialog(onDismiss: () -> Unit, onApply: (kind: String, style: String, width: Float, color: String, applyTo: String) -> Unit) {
    var kind by remember { mutableStateOf("outside") }
    var style by remember { mutableStateOf("single") }
    var width by remember { mutableFloatStateOf(1.5f) }
    var color by remember { mutableStateOf("000000") }
    var applyTo by remember { mutableStateOf("document") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page borders") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BorderPreview(kind, style, width, hex(color))
                Label("Setting")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("None" to "none", "Box" to "outside", "Top" to "top", "Bottom" to "bottom", "Left" to "left", "Right" to "right").forEach { (n, k) ->
                        FilterChip(kind == k, { kind = k }, { Text(n) })
                    }
                }
                Label("Style")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BORDER_STYLES.forEach { (n, k) -> FilterChip(style == k, { style = k }, { Text(n) }) }
                }
                Label("Width")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BORDER_WIDTHS.forEach { w -> FilterChip(width == w, { width = w }, { Text("$w pt") }) }
                }
                Label("Color")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    BORDER_COLORS.forEach { c ->
                        Box(
                            Modifier.size(28.dp).background(hex(c), CircleShape)
                                .border(if (color == c) 3.dp else 1.dp, if (color == c) WordBlue else Color.Gray, CircleShape).clickable { color = c },
                        )
                    }
                }
                Label("Apply to")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(applyTo == "document", { applyTo = "document" }, { Text("Whole document") })
                    FilterChip(applyTo == "section", { applyTo = "section" }, { Text("This section") })
                }
            }
        },
        confirmButton = { TextButton({ onApply(kind, style, width, color, applyTo) }) { Text("Apply") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
