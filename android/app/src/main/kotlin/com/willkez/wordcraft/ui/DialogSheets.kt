package com.willkez.wordcraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willkez.wordcraft.editor.EditorUiState
import com.willkez.wordcraft.editor.EditorViewModel
import org.json.JSONObject

/*
 * Sheets for the commands whose engine side only *asks* the front-end for a dialog (Go To, Page Setup,
 * Paragraph, Insert Chart, Icons, Options…). The engine reports those requests (see EditorViewModel's
 * request handling); each one opens the matching sheet here, which then runs the command with real values.
 */

@Composable
private fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, decimal: Boolean = true, placeholder: String? = null) {
    OutlinedTextField(
        value, { raw -> onChange(raw.filter { it.isDigit() || (decimal && (it == '.' || it == '-')) }.take(8)) },
        modifier, singleLine = true, label = { Text(label) }, placeholder = placeholder?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

private fun inchesToPt(s: String): Double? = s.toDoubleOrNull()?.let { it * 72.0 }

// ---------------------------------------------------------------------------------------------
// Go To
// ---------------------------------------------------------------------------------------------

@Composable
fun GoToSheet(ui: EditorUiState, d: Dispatcher, onDismiss: () -> Unit) {
    var page by remember { mutableStateOf("") }
    var para by remember { mutableStateOf("") }
    var bookmark by remember { mutableStateOf("") }
    val pages = ui.state?.pageCount?.toInt() ?: 1
    AppSheet(onDismiss) {
        SheetTitle("Go to", "Home › Editing")
        SheetScroll {
            SectionLabel("Page (1–$pages)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumField("Page number", page, { page = it }, Modifier.weight(1f), decimal = false)
                Button(
                    { page.toIntOrNull()?.let { d.id("edit.goto", mapOf("page" to it.coerceIn(1, pages))); onDismiss() } },
                    enabled = page.toIntOrNull() != null, colors = ButtonDefaults.buttonColors(containerColor = WordBlue),
                ) { Text("Go") }
            }
            PillRow {
                Pill("Previous page", false) { d.id("edit.goto", mapOf("pageBy" to -1)); onDismiss() }
                Pill("Next page", false) { d.id("edit.goto", mapOf("pageBy" to 1)); onDismiss() }
            }
            SectionLabel("Paragraph")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumField("Paragraph number", para, { para = it }, Modifier.weight(1f), decimal = false)
                Button(
                    { para.toIntOrNull()?.let { d.id("edit.goto", mapOf("paragraph" to it)); onDismiss() } },
                    enabled = para.toIntOrNull() != null, colors = ButtonDefaults.buttonColors(containerColor = WordBlue),
                ) { Text("Go") }
            }
            SectionLabel("Bookmark")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(bookmark, { bookmark = it }, Modifier.weight(1f), singleLine = true, label = { Text("Bookmark name") })
                Button(
                    { d.id("edit.goto", mapOf("bookmark" to bookmark.trim())); onDismiss() },
                    enabled = bookmark.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = WordBlue),
                ) { Text("Go") }
            }
        }
        DoneButton("Close", onDismiss)
    }
}

// ---------------------------------------------------------------------------------------------
// Page Setup
// ---------------------------------------------------------------------------------------------

@Composable
fun PageSetupSheet(d: Dispatcher, onDismiss: () -> Unit) {
    var size by remember { mutableStateOf<String?>(null) }
    var orient by remember { mutableStateOf<String?>(null) }
    var preset by remember { mutableStateOf<String?>(null) }
    var top by remember { mutableStateOf("") }
    var bottom by remember { mutableStateOf("") }
    var left by remember { mutableStateOf("") }
    var right by remember { mutableStateOf("") }
    var cols by remember { mutableStateOf<Int?>(null) }
    AppSheet(onDismiss) {
        SheetTitle("Page setup", "Layout › Page Setup")
        SheetScroll {
            SectionLabel("Paper size")
            PillRow { listOf("Letter", "Legal", "A4", "A3", "A5", "Executive").forEach { n -> Pill(n, size == n) { size = if (size == n) null else n } } }
            SectionLabel("Orientation")
            PillRow {
                Pill("Portrait", orient == "portrait") { orient = if (orient == "portrait") null else "portrait" }
                Pill("Landscape", orient == "landscape") { orient = if (orient == "landscape") null else "landscape" }
            }
            SectionLabel("Margins")
            PillRow {
                listOf("Normal" to "normal", "Narrow" to "narrow", "Moderate" to "moderate", "Wide" to "wide", "Mirrored" to "mirrored").forEach { (n, k) ->
                    Pill(n, preset == k) { preset = if (preset == k) null else k }
                }
            }
            Text("Or set your own, in inches (leave blank to keep):", fontSize = 12.sp, color = SheetMuted, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                NumField("Top", top, { top = it }, Modifier.weight(1f))
                NumField("Bottom", bottom, { bottom = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                NumField("Left", left, { left = it }, Modifier.weight(1f))
                NumField("Right", right, { right = it }, Modifier.weight(1f))
            }
            SectionLabel("Columns")
            PillRow { (1..4).forEach { n -> Pill("$n", cols == n) { cols = if (cols == n) null else n } } }
        }
        PrimaryButton("Apply") {
            size?.let { d.id("layout.size", mapOf("name" to it)) }
            orient?.let { d.id("layout.orientation", mapOf("value" to it)) }
            val custom = buildMap<String, Any?> {
                inchesToPt(top)?.let { put("top", it) }; inchesToPt(bottom)?.let { put("bottom", it) }
                inchesToPt(left)?.let { put("left", it) }; inchesToPt(right)?.let { put("right", it) }
            }
            if (custom.isNotEmpty()) d.id("layout.margins", custom) else preset?.let { d.id("layout.margins", mapOf("preset" to it)) }
            cols?.let { d.id("layout.columns", mapOf("count" to it)) }
            onDismiss()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Paragraph
// ---------------------------------------------------------------------------------------------

@Composable
fun ParagraphSheet(ui: EditorUiState, d: Dispatcher, onDismiss: () -> Unit) {
    var align by remember { mutableStateOf<String?>(null) }
    var left by remember { mutableStateOf("") }
    var right by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var before by remember { mutableStateOf("") }
    var after by remember { mutableStateOf("") }
    var line by remember { mutableStateOf<String?>(null) }
    var lineValue by remember { mutableStateOf("") }
    val current = ui.state?.alignment
    AppSheet(onDismiss) {
        SheetTitle("Paragraph", "Home › Paragraph")
        SheetScroll {
            SectionLabel("Alignment")
            PillRow {
                listOf("Left" to "para.alignLeft", "Centre" to "para.alignCenter", "Right" to "para.alignRight", "Justify" to "para.justify").forEach { (n, id) ->
                    val on = align == id || (align == null && current == when (id) { "para.alignLeft" -> "left"; "para.alignCenter" -> "center"; "para.alignRight" -> "right"; else -> "justify" })
                    Pill(n, on) { align = id }
                }
            }
            SectionLabel("Indentation, in inches")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Left", left, { left = it }, Modifier.weight(1f))
                NumField("Right", right, { right = it }, Modifier.weight(1f))
            }
            NumField("First line (negative = hanging)", first, { first = it }, Modifier.fillMaxWidth().padding(top = 6.dp))
            SectionLabel("Spacing, in points")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Before", before, { before = it }, Modifier.weight(1f))
                NumField("After", after, { after = it }, Modifier.weight(1f))
            }
            SectionLabel("Line spacing")
            PillRow {
                listOf("Single" to "1.0", "1.15" to "1.15", "1.5" to "1.5", "Double" to "2.0", "At least" to "atLeast", "Exactly" to "exactly").forEach { (n, k) ->
                    Pill(n, line == k) { line = if (line == k) null else k }
                }
            }
            if (line == "atLeast" || line == "exactly") NumField("Points", lineValue, { lineValue = it }, Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        PrimaryButton("Apply") {
            align?.let { d.id(it) }
            val indents = buildMap<String, Any?> {
                inchesToPt(left)?.let { put("left", it) }; inchesToPt(right)?.let { put("right", it) }; inchesToPt(first)?.let { put("firstLine", it) }
            }
            if (indents.isNotEmpty()) d.id("para.indents", indents)
            val spacing = buildMap<String, Any?> { before.toDoubleOrNull()?.let { put("before", it) }; after.toDoubleOrNull()?.let { put("after", it) } }
            if (spacing.isNotEmpty()) d.id("para.spacing", spacing)
            when (line) {
                null -> {}
                "atLeast", "exactly" -> lineValue.toDoubleOrNull()?.let { d.id("para.lineSpacing", mapOf(line!! to it)) }
                else -> d.id("para.lineSpacing", mapOf("value" to line!!.toDouble()))
            }
            onDismiss()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Insert table
// ---------------------------------------------------------------------------------------------

@Composable
fun InsertTableSheet(d: Dispatcher, onDismiss: () -> Unit) {
    var rows by remember { mutableIntStateOf(3) }
    var cols by remember { mutableIntStateOf(3) }
    AppSheet(onDismiss) {
        SheetTitle("Insert table", "Insert › Tables")
        Stepper(rows, 1..50, "Rows") { rows = it }
        Stepper(cols, 1..20, "Columns") { cols = it }
        // A little preview so the size is obvious before inserting.
        Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (r in 1..minOf(rows, 6)) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (c in 1..minOf(cols, 10)) Box(Modifier.size(22.dp, 14.dp).background(Color.White).border(1.dp, WordBlue))
            }
        }
        PrimaryButton("Insert $cols × $rows table") { d.id("insert.table", mapOf("rows" to rows, "cols" to cols)); onDismiss() }
    }
}

// ---------------------------------------------------------------------------------------------
// Chart
// ---------------------------------------------------------------------------------------------

private val CHART_TYPES = listOf(
    "Column" to "column", "Stacked column" to "columnStacked", "Bar" to "bar", "Stacked bar" to "barStacked", "Line" to "line",
    "Area" to "area", "Pie" to "pie", "Doughnut" to "doughnut", "Scatter" to "scatter",
)

/** First row: series names after a blank first cell; then a category and one number per series. */
private fun parseChartData(text: String): Map<String, Any?>? {
    val rows = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { l -> l.split(',', '\t', ';').map { it.trim() } }
    if (rows.size < 2 || rows[0].size < 2) return null
    val body = rows.drop(1)
    val series = rows[0].drop(1).mapIndexed { i, n -> mapOf("name" to n.ifBlank { "Series ${i + 1}" }, "values" to body.map { r -> r.getOrNull(i + 1)?.toDoubleOrNull() }) }
    return mapOf("categories" to body.map { it.firstOrNull().orEmpty() }, "series" to series)
}

@Composable
fun ChartSheet(d: Dispatcher, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf("column") }
    var title by remember { mutableStateOf("") }
    var legend by remember { mutableStateOf("right") }
    var labels by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf("Category,Series 1,Series 2\nQ1,4,2\nQ2,5,3\nQ3,3,4\nQ4,6,5") }
    val parsed = parseChartData(data)
    AppSheet(onDismiss) {
        SheetTitle("Insert chart", "Insert › Illustrations")
        SheetScroll {
            SectionLabel("Type")
            PillRow { CHART_TYPES.forEach { (n, k) -> Pill(n, type == k) { type = k } } }
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true, label = { Text("Chart title (optional)") })
            SectionLabel("Legend")
            PillRow { listOf("Right" to "right", "Bottom" to "bottom", "Top" to "top", "Left" to "left", "None" to "none").forEach { (n, k) -> Pill(n, legend == k) { legend = k } } }
            SwitchRow("Data labels", labels) { labels = it }
            SectionLabel("Data")
            OutlinedTextField(
                data, { data = it }, Modifier.fillMaxWidth(), minLines = 5, maxLines = 10,
                label = { Text("One row per line: category, then a number per series") },
                supportingText = { if (parsed == null) Text("Needs a header row and at least one data row", color = MaterialTheme.colorScheme.error) },
            )
        }
        PrimaryButton("Insert chart", enabled = parsed != null) {
            d.id(
                "insert.chart",
                buildMap<String, Any?> {
                    put("type", type); put("data", parsed); put("legend", legend); put("dataLabels", labels)
                    if (title.isNotBlank()) put("title", title.trim())
                },
            )
            onDismiss()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// SmartArt
// ---------------------------------------------------------------------------------------------

private val SMART_LAYOUTS = listOf(
    "List" to "basicList", "Process" to "process", "Cycle" to "cycle", "Hierarchy" to "hierarchy",
    "Pyramid" to "pyramid", "Radial" to "radial", "Matrix" to "matrix", "Venn" to "venn",
)

@Composable
fun SmartArtSheet(d: Dispatcher, onDismiss: () -> Unit) {
    var layout by remember { mutableStateOf("process") }
    var items by remember { mutableStateOf("") }
    AppSheet(onDismiss) {
        SheetTitle("Insert SmartArt", "Insert › Illustrations")
        SheetScroll {
            SectionLabel("Layout")
            PillRow { SMART_LAYOUTS.forEach { (n, k) -> Pill(n, layout == k) { layout = k } } }
            OutlinedTextField(
                items, { items = it }, Modifier.fillMaxWidth().padding(top = 12.dp), minLines = 4, maxLines = 8,
                label = { Text("Items, one per line (leave empty for sample items)") },
            )
        }
        PrimaryButton("Insert SmartArt") {
            val lines = items.lines().map { it.trim() }.filter { it.isNotEmpty() }
            d.id("insert.smartArt", buildMap<String, Any?> { put("layout", layout); if (lines.isNotEmpty()) put("items", lines) })
            onDismiss()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Icons
// ---------------------------------------------------------------------------------------------

private class IconHit(val id: String, val name: String, val category: String)

@Composable
fun IconsSheet(vm: EditorViewModel, d: Dispatcher, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<IconHit>>(emptyList()) }
    var picked by remember { mutableStateOf<IconHit?>(null) }
    var color by remember { mutableStateOf("000000") }
    var size by remember { mutableIntStateOf(72) }
    LaunchedEffect(query) {
        vm.query("insert.icon", JSONObject().put("query", query.trim()).toString()) { out ->
            hits = runCatching {
                val a = JSONObject(out).getJSONArray("icons")
                (0 until a.length()).map { i -> a.getJSONObject(i).let { IconHit(it.getString("id"), it.optString("name", it.getString("id")), it.optString("category")) } }
            }.getOrDefault(emptyList())
        }
    }
    AppSheet(onDismiss) {
        SheetTitle("Insert icon", "Insert › Illustrations")
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search icons") })
        Text("${hits.size} icons", fontSize = 11.sp, color = SheetMuted, modifier = Modifier.padding(top = 4.dp))
        SheetScroll(heightFraction = 0.55f) {
            hits.take(120).forEach { h ->
                val on = picked?.id == h.id
                Row(
                    Modifier.fillMaxWidth().clickable { picked = h }.background(if (on) Color(0xFFE3ECFA) else Color.Transparent).padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(h.name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = SheetInk)
                    Text(h.category, fontSize = 11.sp, color = SheetMuted)
                }
            }
            if (hits.isEmpty()) Text("No icons match", Modifier.padding(12.dp), color = SheetMuted)
        }
        SectionLabel("Colour")
        SwatchRow(color, listOf("000000", "595959", "C00000", "ED7D31", "FFC000", "548235", "00B0F0", "185ABD", "7030A0", "FF00FF"), { color = it })
        SectionLabel("Size")
        PillRow { listOf(36, 72, 108, 144).forEach { s -> Pill("$s pt", size == s) { size = s } } }
        PrimaryButton(if (picked != null) "Insert “${picked!!.name}”" else "Choose an icon", enabled = picked != null) {
            picked?.let { d.id("insert.icon", mapOf("id" to it.id, "color" to color, "size" to size)) }
            onDismiss()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Options
// ---------------------------------------------------------------------------------------------

@Composable
fun OptionsSheet(d: Dispatcher, onDismiss: () -> Unit) {
    var author by remember { mutableStateOf("") }
    var initials by remember { mutableStateOf("") }
    var units by remember { mutableStateOf<String?>(null) }
    AppSheet(onDismiss) {
        SheetTitle("Options", "File › Options")
        SheetScroll {
            SectionLabel("Author")
            OutlinedTextField(author, { author = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name on comments and tracked changes") })
            OutlinedTextField(initials, { initials = it.take(6) }, Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true, label = { Text("Initials") })
            SectionLabel("Measurement units")
            PillRow {
                listOf("Inches" to "inches", "Centimetres" to "centimeters", "Millimetres" to "millimeters", "Points" to "points", "Picas" to "picas", "Automatic" to "auto").forEach { (n, k) ->
                    Pill(n, units == k) { units = if (units == k) null else k }
                }
            }
        }
        PrimaryButton("Save", enabled = author.isNotBlank() || initials.isNotBlank() || units != null) {
            d.id("file.options", buildMap<String, Any?> {
                if (author.isNotBlank()) put("author", author.trim())
                if (initials.isNotBlank()) put("initials", initials.trim())
                units?.let { put("units", it) }
            })
            onDismiss()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Caption
// ---------------------------------------------------------------------------------------------

@Composable
fun CaptionSheet(d: Dispatcher, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("Figure") }
    var text by remember { mutableStateOf("") }
    var below by remember { mutableStateOf(true) }
    AppSheet(onDismiss) {
        SheetTitle("Insert caption", "References › Captions")
        SectionLabel("Label")
        PillRow { listOf("Figure", "Table", "Equation").forEach { n -> Pill(n, label == n) { label = n } } }
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true, label = { Text("Caption text (optional)") })
        SectionLabel("Position")
        PillRow {
            Pill("Below", below) { below = true }
            Pill("Above", !below) { below = false }
        }
        PrimaryButton("Insert caption") {
            d.id("references.caption", buildMap<String, Any?> { put("label", label); put("position", if (below) "below" else "above"); if (text.isNotBlank()) put("text", text.trim()) })
            onDismiss()
        }
    }
}
