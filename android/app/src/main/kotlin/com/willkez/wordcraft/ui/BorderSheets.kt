package com.willkez.wordcraft.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import com.willkez.wordcraft.editor.EditorUiState
import kotlin.math.abs
import kotlin.math.hypot

// ---------------------------------------------------------------------------------------------
// Drawing: one line in any of Word's border styles, used by the live previews
// ---------------------------------------------------------------------------------------------

/** A line from `a` to `b` in a Word border style; `widthPt` scales the stroke. */
internal fun DrawScope.borderLine(a: Offset, b: Offset, style: String, widthPt: Double, color: Color) {
    val stroke = (widthPt.toFloat() * 1.7f).coerceAtLeast(1.2f)
    val horizontal = abs(b.x - a.x) >= abs(b.y - a.y)
    fun off(d: Float) = if (horizontal) Offset(0f, d) else Offset(d, 0f)
    when (style) {
        "double" -> {
            val g = stroke * 1.1f
            drawLine(color, a + off(-g), b + off(-g), stroke * 0.7f)
            drawLine(color, a + off(g), b + off(g), stroke * 0.7f)
        }
        "triple" -> {
            val g = stroke * 1.5f
            for (k in -1..1) drawLine(color, a + off(k * g), b + off(k * g), stroke * 0.5f)
        }
        "wave" -> {
            val len = hypot(b.x - a.x, b.y - a.y)
            val n = (len / 7f).toInt().coerceAtLeast(2)
            val path = Path().apply {
                moveTo(a.x, a.y)
                for (i in 1..n) {
                    val t = i / n.toFloat()
                    val amp = if (i % 2 == 0) -stroke * 1.2f else stroke * 1.2f
                    val p = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t) + off(amp)
                    lineTo(p.x, p.y)
                }
            }
            drawPath(path, color, style = Stroke(width = stroke * 0.7f))
        }
        else -> {
            val w = if (style == "thick") stroke * 2f else stroke
            val dash = when (style) {
                "dashed" -> PathEffect.dashPathEffect(floatArrayOf(w * 4f, w * 2.5f))
                "dotted" -> PathEffect.dashPathEffect(floatArrayOf(w, w * 1.8f))
                "dotDash" -> PathEffect.dashPathEffect(floatArrayOf(w * 4f, w * 1.5f, w, w * 1.5f))
                else -> null
            }
            drawLine(color, a, b, w, pathEffect = dash)
        }
    }
}

private val PreviewFrame = RoundedCornerShape(10.dp)

/** A paragraph with the chosen border drawn round it (`kind` as the engine names it). */
@Composable
internal fun ParaBorderPreview(kind: String, style: String, width: Double, color: Color, shade: Color? = null) {
    Canvas(Modifier.fillMaxWidth().height(88.dp).clip(PreviewFrame).background(Color(0xFFFAFAFA)).border(1.dp, SheetLine, PreviewFrame)) {
        val l = 26.dp.toPx(); val r = size.width - l; val t = 18.dp.toPx(); val b = size.height - t
        if (shade != null) drawRect(shade, Offset(l, t), Size(r - l, b - t))
        for (i in 0..2) {
            val w = (r - l - 24.dp.toPx()) * if (i == 2) 0.55f else 1f
            drawRoundRect(Color(0xFFD0D5DD), Offset(l + 12.dp.toPx(), t + 10.dp.toPx() + i * 11.dp.toPx()), Size(w, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
        }
        val top = Offset(l, t) to Offset(r, t); val bottom = Offset(l, b) to Offset(r, b)
        val left = Offset(l, t) to Offset(l, b); val right = Offset(r, t) to Offset(r, b)
        val lines = when (kind) {
            "top" -> listOf(top); "bottom", "horizontalLine" -> listOf(bottom)
            "left" -> listOf(left); "right" -> listOf(right)
            "all", "outside" -> listOf(top, bottom, left, right)
            else -> emptyList()
        }
        lines.forEach { (p, q) -> borderLine(p, q, style, width, color) }
    }
}

/** A small table with the chosen borders (`kind` as the engine names it). */
@Composable
internal fun TableBorderPreview(kind: String, width: Double, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(96.dp).clip(PreviewFrame).background(Color(0xFFFAFAFA)).border(1.dp, SheetLine, PreviewFrame)) {
        val l = 34.dp.toPx(); val r = size.width - l; val t = 14.dp.toPx(); val b = size.height - t
        val cols = 3; val rows = 3
        val faint = Color(0xFFE4E7EC)
        // The grid as it would be without borders, so the chosen lines stand out.
        for (i in 0..cols) { val x = l + (r - l) * i / cols; drawLine(faint, Offset(x, t), Offset(x, b), 1f) }
        for (j in 0..rows) { val y = t + (b - t) * j / rows; drawLine(faint, Offset(l, y), Offset(r, y), 1f) }
        val outerTop = kind in setOf("all", "outside", "top"); val outerBottom = kind in setOf("all", "outside", "bottom")
        val outerLeft = kind in setOf("all", "outside", "left"); val outerRight = kind in setOf("all", "outside", "right")
        val inside = kind == "all" || kind == "inside"
        if (outerTop) borderLine(Offset(l, t), Offset(r, t), "single", width, color)
        if (outerBottom) borderLine(Offset(l, b), Offset(r, b), "single", width, color)
        if (outerLeft) borderLine(Offset(l, t), Offset(l, b), "single", width, color)
        if (outerRight) borderLine(Offset(r, t), Offset(r, b), "single", width, color)
        if (inside) {
            for (i in 1 until cols) { val x = l + (r - l) * i / cols; borderLine(Offset(x, t), Offset(x, b), "single", width, color) }
            for (j in 1 until rows) { val y = t + (b - t) * j / rows; borderLine(Offset(l, y), Offset(r, y), "single", width, color) }
        }
    }
}

/** The page with its border drawn on, so a choice of sides, style and colour is visible before it is applied. */
@Composable
private fun PageBorderPreview(sides: Set<String>, style: String, width: Double, color: Color) {
    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.width(108.dp).height(144.dp).clip(RoundedCornerShape(4.dp)).background(Color.White).border(1.dp, Color(0xFFCBD0D8), RoundedCornerShape(4.dp))) {
            val m = 12.dp.toPx(); val l = m; val r = size.width - m; val t = m; val b = size.height - m
            for (i in 0..7) {
                val w = (r - l - 20.dp.toPx()) * if (i % 4 == 3) 0.5f else 1f
                drawRoundRect(Color(0xFFE4E7EC), Offset(l + 10.dp.toPx(), t + 14.dp.toPx() + i * 12.dp.toPx()), Size(w, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
            }
            if ("top" in sides) borderLine(Offset(l, t), Offset(r, t), style, width, color)
            if ("bottom" in sides) borderLine(Offset(l, b), Offset(r, b), style, width, color)
            if ("left" in sides) borderLine(Offset(l, t), Offset(l, b), style, width, color)
            if ("right" in sides) borderLine(Offset(r, t), Offset(r, b), style, width, color)
        }
    }
}

private fun widthText(w: Double) = "$w pt"

// ---------------------------------------------------------------------------------------------
// Borders and shading (paragraphs)
// ---------------------------------------------------------------------------------------------

private val PARA_WHERE = listOf("bottom" to "Bottom", "top" to "Top", "left" to "Left", "right" to "Right", "all" to "All sides", "outside" to "Outside", "horizontalLine" to "Line", "none" to "None")
internal val LINE_STYLES = listOf("single" to "Solid", "double" to "Double", "dotted" to "Dotted", "dashed" to "Dashed", "thick" to "Thick", "triple" to "Triple", "dotDash" to "Dot-dash", "wave" to "Wave")

/** Paragraph borders in every line style Word has, with paragraph shading. */
@Composable
fun ParaBorderSheet(d: Dispatcher, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf("bottom") }
    var style by remember { mutableStateOf("single") }
    var width by remember { mutableStateOf(1.0) }
    var color by remember { mutableStateOf("000000") }
    var shade by remember { mutableStateOf<String?>(null) }
    AppSheet(onDismiss) {
        SheetTitle("Borders and shading", "For the selected paragraphs")
        SheetScroll {
            ParaBorderPreview(kind, style, width, parseHex(color) ?: Color.Black, parseHex(shade))
            SectionLabel("Where")
            PillRow { PARA_WHERE.forEach { (k, n) -> Pill(n, kind == k) { kind = k } } }
            SectionLabel("Line style")
            PillRow { LINE_STYLES.forEach { (k, n) -> Pill(n, style == k) { style = k } } }
            SectionLabel("Thickness")
            PillRow { LINE_WIDTHS.forEach { w -> Pill(widthText(w), width == w) { width = w } } }
            SectionLabel("Colour")
            SwatchRow(color, LINE_COLORS) { color = it }
            PrimaryButton("Apply border") {
                d.id("para.borders", if (kind == "none") mapOf("kind" to "none") else mapOf("kind" to kind, "style" to style, "width" to width, "color" to color))
            }
            SectionLabel("Shading")
            SwatchRow(shade, SHADE_COLORS) { shade = it; d.id("para.shading", mapOf("color" to it)) }
            QuietButton("No shading") { shade = null; d.id("para.shading", mapOf("color" to JSONObject.NULL)) }
        }
        DoneButton(onClick = onDismiss)
    }
}

// ---------------------------------------------------------------------------------------------
// Format table
// ---------------------------------------------------------------------------------------------

private val TABLE_WHERE = listOf("all" to "All", "outside" to "Outside", "inside" to "Inside", "top" to "Top", "bottom" to "Bottom", "left" to "Left", "right" to "Right", "none" to "None")
private val ALIGN_GRID = listOf(
    listOf("topLeft", "topCenter", "topRight"),
    listOf("centerLeft", "center", "centerRight"),
    listOf("bottomLeft", "bottomCenter", "bottomRight"),
)
private val FALLBACK_STYLES = listOf("Table Grid", "Plain Table 1", "Grid Table 1 Light", "Grid Table 4 – Blue", "Grid Table 4 – Orange", "Grid Table 4 – Green", "Grid Table 4 – Purple", "List Table 3 – Blue")

/** What colour a table style's header row has, from its name (Office's accent colours too). */
private fun tableStyleAccent(name: String): Color? {
    val n = name.lowercase()
    return when {
        "blue" in n || "accent 1" in n || "accent 5" in n -> Color(0xFF4472C4)
        "orange" in n || "accent 2" in n -> Color(0xFFED7D31)
        "green" in n || "accent 6" in n -> Color(0xFF70AD47)
        "purple" in n -> Color(0xFF7030A0)
        "gold" in n || "yellow" in n || "accent 4" in n -> Color(0xFFFFC000)
        "red" in n -> Color(0xFFC00000)
        "gray" in n || "grey" in n || "accent 3" in n -> Color(0xFFA5A5A5)
        else -> null
    }
}

/** Everything about how a table looks: borders, shading, cell alignment, size and fit, styles. */
@Composable
fun TableFormatSheet(ui: EditorUiState, d: Dispatcher, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf("all") }
    var width by remember { mutableStateOf(1.0) }
    var color by remember { mutableStateOf("000000") }
    var shade by remember { mutableStateOf<String?>(null) }
    var header by remember { mutableStateOf(true) }
    var banded by remember { mutableStateOf(true) }
    var firstCol by remember { mutableStateOf(false) }
    var lastCol by remember { mutableStateOf(false) }
    var total by remember { mutableStateOf(false) }
    var bandedCols by remember { mutableStateOf(false) }
    var colWidth by remember { mutableStateOf("") }
    var rowHeight by remember { mutableStateOf("") }
    val styles = remember(ui.tableStyles) { ui.tableStyles.ifEmpty { FALLBACK_STYLES }.take(48) }
    var styleChosen by remember { mutableStateOf<String?>(null) }

    AppSheet(onDismiss) {
        SheetTitle("Format table", "Changes apply to the selected cells, or the whole table")
        SheetScroll {
            TableBorderPreview(if (kind == "none") "none" else kind, width, parseHex(color) ?: Color.Black)
            SectionLabel("Borders")
            PillRow { TABLE_WHERE.forEach { (k, n) -> Pill(n, kind == k) { kind = k } } }
            SectionLabel("Thickness")
            PillRow { LINE_WIDTHS.forEach { w -> Pill(widthText(w), width == w) { width = w } } }
            SectionLabel("Colour")
            SwatchRow(color, LINE_COLORS) { color = it }
            PrimaryButton("Apply borders") {
                d.id("table.borders", if (kind == "none") mapOf("kind" to "none") else mapOf("kind" to kind, "width" to width, "color" to color))
            }

            SectionLabel("Shading")
            SwatchRow(shade, SHADE_COLORS) { shade = it; d.id("table.shading", mapOf("color" to it)) }
            QuietButton("No shading") { shade = null; d.id("table.shading", mapOf("color" to JSONObject.NULL)) }

            SectionLabel("Cell alignment")
            ALIGN_GRID.forEach { row ->
                Row { row.forEach { a -> AlignTile(a) { d.id("table.cellAlign", mapOf("value" to a)) } } }
            }

            SectionLabel("Size and fit")
            PillRow {
                Pill("AutoFit contents", false) { d.id("table.autofit", mapOf("mode" to "contents")) }
                Pill("Fit window", false) { d.id("table.autofit", mapOf("mode" to "window")) }
                Pill("Fixed width", false) { d.id("table.autofit", mapOf("mode" to "fixed")) }
            }
            PillRow {
                Pill("Equal columns", false) { d.id("table.distributeColumns") }
                Pill("Equal rows", false) { d.id("table.distributeRows") }
            }
            NumberRow("Column width (pt)", colWidth, { colWidth = it }) { v -> d.id("table.columnWidth", mapOf("width" to v)) }
            NumberRow("Row height (pt)", rowHeight, { rowHeight = it }) { v -> d.id("table.rowHeight", mapOf("height" to v)) }

            SectionLabel("Table style")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                items(styles) { name ->
                    StyleSwatch(name, styleChosen == name) { styleChosen = name; d.id("table.style", mapOf("style" to name)) }
                }
            }
            SectionLabel("Style options")
            PillRow {
                Pill("Header row", header) { header = !header; d.id("table.look", mapOf("headerRow" to header)) }
                Pill("Banded rows", banded) { banded = !banded; d.id("table.look", mapOf("bandedRows" to banded)) }
                Pill("First column", firstCol) { firstCol = !firstCol; d.id("table.look", mapOf("firstColumn" to firstCol)) }
            }
            PillRow {
                Pill("Last column", lastCol) { lastCol = !lastCol; d.id("table.look", mapOf("lastColumn" to lastCol)) }
                Pill("Total row", total) { total = !total; d.id("table.look", mapOf("totalRow" to total)) }
                Pill("Banded columns", bandedCols) { bandedCols = !bandedCols; d.id("table.look", mapOf("bandedColumns" to bandedCols)) }
            }
        }
        DoneButton(onClick = onDismiss)
    }
}

/** A labelled number box with its own Set button, for exact column widths and row heights. */
@Composable
private fun NumberRow(label: String, value: String, onChange: (String) -> Unit, onSet: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value, onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) }, singleLine = true,
            label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        QuietButton("Set", value.toDoubleOrNull() != null) { value.toDoubleOrNull()?.let(onSet) }
    }
}

/** A 3×3 grid cell showing where the text will sit. */
@Composable
private fun AlignTile(a: String, onClick: () -> Unit) {
    val h = when { a.endsWith("Left") -> 0; a.endsWith("Right") -> 2; else -> 1 }
    val v = when { a.startsWith("top") -> 0; a.startsWith("bottom") -> 2; else -> 1 }
    Box(
        Modifier.padding(end = 8.dp, bottom = 8.dp).size(width = 76.dp, height = 52.dp)
            .clip(RoundedCornerShape(10.dp)).background(PillIdle).clickable(onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 10.dp.toPx(); val barH = 3.dp.toPx(); val gap = 4.dp.toPx()
            val fractions = listOf(0.75f, 0.45f)
            val inner = size.width - 2 * pad
            val total = fractions.size * barH + (fractions.size - 1) * gap
            val y0 = when (v) { 0 -> pad * 0.8f; 1 -> (size.height - total) / 2f; else -> size.height - pad * 0.8f - total }
            fractions.forEachIndexed { i, f ->
                val w = inner * f
                val x = when (h) { 0 -> pad; 1 -> (size.width - w) / 2f; else -> size.width - pad - w }
                drawRoundRect(SheetInk, Offset(x, y0 + i * (barH + gap)), Size(w, barH), CornerRadius(barH / 2f))
            }
        }
    }
}

/** A table style as a tiny table, tinted like its header row. */
@Composable
private fun StyleSwatch(name: String, selected: Boolean, onClick: () -> Unit) {
    val accent = tableStyleAccent(name)
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.width(78.dp).clip(shape).background(PanelBg)
            .border(if (selected) 2.dp else 1.dp, if (selected) WordBlue else SheetLine, shape)
            .clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            val rows = 4; val cols = 3
            val rh = size.height / rows; val cw = size.width / cols
            val grid = Color(0xFFB8BEC8)
            for (r in 0 until rows) {
                if (r == 0 && accent != null) drawRect(accent, Offset(0f, 0f), Size(size.width, rh))
                else if (r % 2 == 0 && accent != null) drawRect(accent.copy(alpha = 0.16f), Offset(0f, r * rh), Size(size.width, rh))
            }
            if (!name.contains("plain", ignoreCase = true)) {
                for (r in 0..rows) drawLine(grid, Offset(0f, r * rh), Offset(size.width, r * rh), 1f)
                for (c in 0..cols) drawLine(grid, Offset(c * cw, 0f), Offset(c * cw, size.height), 1f)
            }
        }
        Text(name.replace("Table", "").trim(), Modifier.padding(top = 4.dp), fontSize = 9.sp, maxLines = 2, color = SheetMuted)
    }
}

// ---------------------------------------------------------------------------------------------
// Page borders
// ---------------------------------------------------------------------------------------------

private val PAGE_SIDES = listOf("top" to "Top", "bottom" to "Bottom", "left" to "Left", "right" to "Right")

/** Page borders: a box or any sides, line style, width and colour, for this section or the whole document. */
@Composable
fun PageBordersSheet(d: Dispatcher, onDismiss: () -> Unit) {
    var sides by remember { mutableStateOf(setOf("top", "bottom", "left", "right")) }
    var style by remember { mutableStateOf("single") }
    var width by remember { mutableStateOf(1.5) }
    var color by remember { mutableStateOf("000000") }
    var applyTo by remember { mutableStateOf("document") }
    AppSheet(onDismiss) {
        SheetTitle("Page borders", "Design")
        SheetScroll {
            PageBorderPreview(sides, style, width, parseHex(color) ?: Color.Black)
            SectionLabel("Setting")
            PillRow {
                Pill("None", sides.isEmpty()) { sides = emptySet() }
                Pill("Box", sides.size == 4) { sides = PAGE_SIDES.map { it.first }.toSet() }
            }
            PillRow {
                PAGE_SIDES.forEach { (k, n) -> Pill(n, k in sides) { sides = if (k in sides) sides - k else sides + k } }
            }
            SectionLabel("Style")
            PillRow { LINE_STYLES.forEach { (k, n) -> Pill(n, style == k) { style = k } } }
            SectionLabel("Width")
            PillRow { LINE_WIDTHS.forEach { w -> Pill(widthText(w), width == w) { width = w } } }
            SectionLabel("Colour")
            SwatchRow(color, PAGE_BORDER_COLORS) { color = it }
            HexField(color) { color = it }
            SectionLabel("Apply to")
            PillRow {
                Pill("Whole document", applyTo == "document") { applyTo = "document" }
                Pill("This section", applyTo == "section") { applyTo = "section" }
            }
        }
        PrimaryButton("Apply") {
            val c = if (color.length == 6) color else "000000"
            val line = mapOf("style" to style, "width" to width, "color" to c)
            d.id(
                "design.pageBorders",
                when {
                    sides.isEmpty() -> mapOf("kind" to "none", "applyTo" to applyTo)
                    sides.size == 4 -> mapOf("kind" to "box", "style" to style, "width" to width, "color" to c, "applyTo" to applyTo)
                    else -> mapOf("sides" to sides.associateWith { line }, "applyTo" to applyTo)
                },
            )
            onDismiss()
        }
    }
}
