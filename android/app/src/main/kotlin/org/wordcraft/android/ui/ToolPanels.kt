package org.wordcraft.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.wordcraft.android.editor.EditorUiState
import org.wordcraft.android.editor.EditorViewModel
import org.wordcraft.bridge.HeadingItem
import org.wordcraft.bridge.SearchHit

// ---------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------

private val PILL_ON = Color(0xFFDCE8FA)
private val PILL_OFF = Color(0xFFF2F4F7)

@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) PILL_ON else PILL_OFF)
            .border(if (selected) 1.5.dp else 0.dp, if (selected) WordBlue else Color.Transparent, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, fontSize = 13.sp, color = if (selected) WordBlue else Color(0xFF344054), fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PillRow(items: List<Pair<String, String>>, selected: String?, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, value) -> Pill(label, selected == value) { onPick(value) } }
    }
}

private val SHADES = listOf("FFFFFF", "F2F2F2", "D9D9D9", "FFF2CC", "FCE4D6", "E2EFDA", "DDEBF7", "E4DFEC", "FFFF00", "BDD7EE", "C6E0B4")
private val LINE_COLORS = listOf("000000", "595959", "A6A6A6", "C00000", "ED7D31", "FFC000", "548235", "00B0F0", "185ABD", "7030A0")

private fun col(hex: String) = Color(0xFF000000 or hex.toLong(16))

@Composable
fun SwatchRow(colors: List<String>, selected: String?, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { c ->
            Box(
                Modifier.size(30.dp).background(col(c), CircleShape)
                    .border(if (selected == c) 3.dp else 1.dp, if (selected == c) WordBlue else Color(0xFFBBBBBB), CircleShape).clickable { onPick(c) },
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF667085))
        content()
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) =
    Button(onClick, Modifier.fillMaxWidth(), enabled = enabled, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = WordBlue)) { Text(label) }

/** "pageNumbers" → "Page numbers". */
private fun humanize(name: String): String =
    name.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }.let { it[0] + it.drop(1).lowercase() }

// ---------------------------------------------------------------------------------------------
// Generic form (any command with inputs) as a floating panel
// ---------------------------------------------------------------------------------------------

@Composable
internal fun FormContent(cmd: Cmd, vm: EditorViewModel) {
    val values = remember(cmd.id) { mutableStateMapOf<String, String>() }
    val missing = cmd.fields.any { !it.optional && values[it.name].isNullOrBlank() && it.kind != FieldKind.BOOL }
    Text(cmd.location, fontSize = 11.sp, color = Color.Gray)
    Spacer(Modifier.height(8.dp))
    cmd.fields.forEach { f ->
        val label = humanize(f.name) + if (f.optional) "" else " *"
        val isColor = f.name.contains("colo", ignoreCase = true) && f.options.isEmpty()
        when {
            f.kind == FieldKind.BOOL -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), fontSize = 14.sp)
                Switch(values[f.name] == "true", { values[f.name] = it.toString() })
            }
            f.options.isNotEmpty() -> Section(label) { PillRow(f.options.map { humanize(it) to it }, values[f.name]) { values[f.name] = it } }
            isColor -> Section(label) {
                SwatchRow(LINE_COLORS + SHADES.drop(8), values[f.name]) { values[f.name] = it }
                OutlinedTextField(
                    values[f.name].orEmpty(), { values[f.name] = it.removePrefix("#").take(6) }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Hex colour, e.g. 185ABD") },
                )
            }
            else -> OutlinedTextField(
                values[f.name].orEmpty(), { values[f.name] = it }, Modifier.fillMaxWidth().padding(bottom = 6.dp), label = { Text(label) },
                singleLine = f.kind != FieldKind.JSON,
                keyboardOptions = KeyboardOptions(keyboardType = if (f.kind == FieldKind.NUMBER) KeyboardType.Decimal else KeyboardType.Text),
                supportingText = if (f.hint.isNotEmpty()) ({ Text(f.hint, fontSize = 10.sp) }) else null,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    PrimaryButton("Apply", !missing) { vm.submit(cmd, buildJson(cmd, values)) }
}

// ---------------------------------------------------------------------------------------------
// Borders and shading (paragraphs)
// ---------------------------------------------------------------------------------------------

private val PARA_WHERE = listOf("Bottom" to "bottom", "Top" to "top", "Left" to "left", "Right" to "right", "All sides" to "all", "Outside" to "outside", "Line" to "horizontalLine", "None" to "none")

@Composable
internal fun BordersContent(d: Dispatcher) {
    var kind by remember { mutableStateOf("bottom") }
    var style by remember { mutableStateOf("single") }
    var width by remember { mutableFloatStateOf(1f) }
    var color by remember { mutableStateOf("000000") }
    Text("For the selected paragraphs", fontSize = 11.sp, color = Color.Gray)
    Spacer(Modifier.height(6.dp))
    BorderPreview(kind.let { if (it == "horizontalLine") "bottom" else it }, style, width, col(color))
    Spacer(Modifier.height(10.dp))
    Section("Where") { PillRow(PARA_WHERE, kind) { kind = it } }
    Section("Line style") { PillRow(BORDER_STYLES.map { (n, v) -> (if (v == "single") "Solid" else n) to v }, style) { style = it } }
    Section("Thickness") { PillRow(BORDER_WIDTHS.map { "$it pt" to it.toString() }, width.toString()) { width = it.toFloat() } }
    Section("Colour") { SwatchRow(LINE_COLORS, color) { color = it } }
    PrimaryButton("Apply border") { d.id("para.borders", mapOf("kind" to kind, "style" to style, "width" to width, "color" to color)) }
    Spacer(Modifier.height(14.dp))
    Section("Shading") {
        SwatchRow(SHADES, null) { d.id("para.shading", mapOf("color" to it)) }
        TextButton({ d.id("para.shading", mapOf("color" to JSONObject.NULL)) }) { Text("No shading") }
    }
    OutlinedButton({ d.vm.closePanel("borders") }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Done") }
}

// ---------------------------------------------------------------------------------------------
// Page borders
// ---------------------------------------------------------------------------------------------

@Composable
internal fun PageBordersContent(d: Dispatcher) {
    var kind by remember { mutableStateOf("outside") }
    var style by remember { mutableStateOf("single") }
    var width by remember { mutableFloatStateOf(1.5f) }
    var color by remember { mutableStateOf("000000") }
    var applyTo by remember { mutableStateOf("document") }
    BorderPreview(kind, style, width, col(color))
    Spacer(Modifier.height(10.dp))
    Section("Setting") { PillRow(listOf("None" to "none", "Box" to "outside", "Top" to "top", "Bottom" to "bottom", "Left" to "left", "Right" to "right"), kind) { kind = it } }
    Section("Style") { PillRow(BORDER_STYLES, style) { style = it } }
    Section("Width") { PillRow(BORDER_WIDTHS.map { "$it pt" to it.toString() }, width.toString()) { width = it.toFloat() } }
    Section("Colour") { SwatchRow(LINE_COLORS, color) { color = it } }
    Section("Apply to") { PillRow(listOf("Whole document" to "document", "This section" to "section"), applyTo) { applyTo = it } }
    PrimaryButton("Apply page border") {
        val line = mapOf("style" to style, "width" to width, "color" to color)
        d.id(
            "design.pageBorders",
            when (kind) {
                "none" -> mapOf("kind" to "none", "applyTo" to applyTo)
                "outside" -> mapOf("kind" to "box", "style" to style, "width" to width, "color" to color, "applyTo" to applyTo)
                else -> mapOf("sides" to mapOf(kind to line), "applyTo" to applyTo)
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Format table
// ---------------------------------------------------------------------------------------------

private val TABLE_WHERE = listOf("All" to "all", "Outside" to "outside", "Inside" to "inside", "Top" to "top", "Bottom" to "bottom", "Left" to "left", "Right" to "right", "None" to "none")

@Composable
private fun TableStyleTile(name: String, onClick: () -> Unit) {
    val accent = when {
        name.contains("Blue", true) -> Color(0xFF4472C4); name.contains("Orange", true) -> Color(0xFFED7D31)
        name.contains("Green", true) -> Color(0xFF70AD47); name.contains("Purple", true) -> Color(0xFF7030A0)
        name.contains("Gold", true) -> Color(0xFFFFC000); name.contains("Gray", true) || name.contains("Grey", true) -> Color(0xFF7F7F7F)
        else -> Color(0xFF444444)
    }
    val filled = !name.startsWith("Plain") && !name.startsWith("Table Grid")
    Column(Modifier.width(70.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFD0D5DD), RoundedCornerShape(8.dp)).clickable(onClick = onClick)) {
        Canvas(Modifier.fillMaxWidth().height(44.dp).background(Color.White)) {
            val cw = size.width / 4f; val rh = size.height / 4f
            for (r in 0 until 4) for (c in 0 until 4) {
                val fill = when { r == 0 && filled -> accent; r % 2 == 1 && filled -> accent.copy(alpha = 0.14f); else -> Color.Transparent }
                drawRect(fill, Offset(c * cw, r * rh), androidx.compose.ui.geometry.Size(cw, rh))
                drawRect(accent.copy(alpha = 0.55f), Offset(c * cw, r * rh), androidx.compose.ui.geometry.Size(cw, rh), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            }
        }
        Text(name.replace("Grid Table ", "Grid ").replace("List Table ", "List "), Modifier.background(Color(0xFFF2F4F7)).fillMaxWidth().padding(3.dp), fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun TableFormatContent(ui: EditorUiState, d: Dispatcher) {
    var kind by remember { mutableStateOf("all") }
    var width by remember { mutableFloatStateOf(1f) }
    var color by remember { mutableStateOf("000000") }
    Text("Changes apply to the selected cells, or the whole table", fontSize = 11.sp, color = Color.Gray)
    Spacer(Modifier.height(8.dp))
    Section("Rows and columns") {
        PillRow(
            listOf("Row above" to "table.insertRowAbove", "Row below" to "table.insertRowBelow", "Column left" to "table.insertColumnLeft", "Column right" to "table.insertColumnRight",
                "Delete row" to "table.deleteRow", "Delete column" to "table.deleteColumn", "Merge cells" to "table.merge"),
            null,
        ) { d.id(it) }
    }
    Section("Borders") {
        PillRow(TABLE_WHERE, kind) { kind = it }
        PillRow(BORDER_WIDTHS.map { "$it pt" to it.toString() }, width.toString()) { width = it.toFloat() }
        SwatchRow(LINE_COLORS, color) { color = it }
        PrimaryButton("Apply borders") { d.id("table.borders", mapOf("kind" to kind, "width" to width, "color" to color)) }
    }
    Section("Shading") {
        SwatchRow(SHADES, null) { d.id("table.shading", mapOf("color" to it)) }
        TextButton({ d.id("table.shading", mapOf("color" to JSONObject.NULL)) }) { Text("No shading") }
    }
    Section("Cell alignment") {
        val spots = listOf("topLeft", "topCenter", "topRight", "centerLeft", "center", "centerRight", "bottomLeft", "bottomCenter", "bottomRight")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            spots.chunked(3).forEach { rowSpots ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rowSpots.forEach { v ->
                        val bias = when { v.endsWith("Left") -> Alignment.CenterStart; v.endsWith("Right") -> Alignment.CenterEnd; else -> Alignment.Center }
                        val vert = when { v.startsWith("top") -> Alignment.TopCenter; v.startsWith("bottom") -> Alignment.BottomCenter; else -> Alignment.Center }
                        Box(Modifier.size(width = 62.dp, height = 40.dp).clip(RoundedCornerShape(8.dp)).background(PILL_OFF).clickable { d.id("table.cellAlign", mapOf("value" to v)) }.padding(6.dp)) {
                            Box(
                                Modifier.align(if (v.startsWith("top")) (if (bias == Alignment.CenterStart) Alignment.TopStart else if (bias == Alignment.CenterEnd) Alignment.TopEnd else vert)
                                else if (v.startsWith("bottom")) (if (bias == Alignment.CenterStart) Alignment.BottomStart else if (bias == Alignment.CenterEnd) Alignment.BottomEnd else vert) else bias)
                                    .size(width = 18.dp, height = 3.dp).background(Color(0xFF344054), RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                }
            }
        }
    }
    Section("Size") {
        PillRow(
            listOf("AutoFit contents" to "contents", "AutoFit window" to "window", "Fixed width" to "fixed"), null,
        ) { d.id("table.autofit", mapOf("mode" to it)) }
        PillRow(listOf("Distribute rows" to "table.distributeRows", "Distribute columns" to "table.distributeColumns"), null) { d.id(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ui.ribbon?.byId?.get("table.columnWidth")?.let { c -> OutlinedButton({ d(c) }, shape = RoundedCornerShape(12.dp)) { Text("Column width…") } }
            ui.ribbon?.byId?.get("table.rowHeight")?.let { c -> OutlinedButton({ d(c) }, shape = RoundedCornerShape(12.dp)) { Text("Row height…") } }
        }
    }
    Section("Table styles") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.tableStyles.ifEmpty { listOf("Table Grid", "Plain Table 1", "Grid Table 1 Light", "Grid Table 4 – Blue", "Grid Table 4 – Orange", "Grid Table 4 – Green", "Grid Table 4 – Purple", "List Table 3 – Blue") }
                .forEach { n -> TableStyleTile(n) { d.id("table.style", mapOf("style" to n)) } }
        }
        PillRow(
            listOf("Header row" to "headerRow", "Total row" to "totalRow", "Banded rows" to "bandedRows", "First column" to "firstColumn", "Last column" to "lastColumn", "Banded columns" to "bandedColumns"),
            null,
        ) { d.id("table.look", mapOf(it to true)) }
    }
    OutlinedButton({ d.vm.closePanel("tableFormat") }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Done") }
}

// ---------------------------------------------------------------------------------------------
// Table of contents
// ---------------------------------------------------------------------------------------------

private data class TocPreset(val label: String, val title: String, val params: Map<String, Any>)

private val TOC_PRESETS = listOf(
    TocPreset("Automatic Table 1", "Contents", mapOf("title" to "Contents", "levels" to 3, "pageNumbers" to true, "rightAlign" to true, "tabLeader" to "dot", "hyperlinks" to true)),
    TocPreset("Automatic Table 2", "Table of Contents", mapOf("title" to "Table of Contents", "levels" to 3, "pageNumbers" to true, "rightAlign" to true, "tabLeader" to "dot", "hyperlinks" to true)),
    TocPreset("Manual Table", "Table of Contents", mapOf("title" to "Table of Contents", "levels" to 3, "pageNumbers" to true, "rightAlign" to true, "tabLeader" to "hyphen", "hyperlinks" to false)),
)

@Composable
private fun TocCard(p: TocPreset, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFD0D5DD), RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Canvas(Modifier.fillMaxWidth().height(96.dp).background(Color.White)) {
            val ink = Color(0xFF344054)
            val m = 14f
            drawRect(ink, Offset(m, 12f), androidx.compose.ui.geometry.Size(size.width * 0.34f, 8f))
            val rows = listOf(0f to "1", 14f to "1.1", 14f to "1.2", 0f to "2", 14f to "2.1")
            rows.forEachIndexed { i, (indent, _) ->
                val y = 34f + i * 11f
                drawRect(ink.copy(alpha = 0.75f), Offset(m + indent, y), androidx.compose.ui.geometry.Size(size.width * 0.28f, 4.5f))
                val x0 = m + indent + size.width * 0.28f + 6f; val x1 = size.width - m - 18f
                var x = x0
                while (x < x1) { drawCircle(ink.copy(alpha = 0.5f), 1.1f, Offset(x, y + 4f)); x += 6f }
                drawRect(ink.copy(alpha = 0.75f), Offset(size.width - m - 12f, y), androidx.compose.ui.geometry.Size(12f, 4.5f))
            }
        }
        Text(p.label, Modifier.fillMaxWidth().background(Color(0xFFF2F4F7)).padding(8.dp), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun TocContent(d: Dispatcher) {
    var custom by remember { mutableStateOf(false) }
    var levels by remember { mutableIntStateOf(3) }
    var title by remember { mutableStateOf("Table of Contents") }
    var pageNumbers by remember { mutableStateOf(true) }
    var rightAlign by remember { mutableStateOf(true) }
    var hyperlinks by remember { mutableStateOf(true) }
    var leader by remember { mutableStateOf("dot") }
    Text("Built from your Heading styles", fontSize = 11.sp, color = Color.Gray)
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TOC_PRESETS.forEach { p -> TocCard(p) { d.id("references.toc", p.params) } }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (custom) PILL_ON else PILL_OFF).clickable { custom = !custom }.padding(14.dp),
        ) { Text("Custom Table of Contents…", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (custom) WordBlue else Color(0xFF344054)) }
    }
    if (custom) {
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Title") })
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Levels", Modifier.weight(1f), fontSize = 14.sp)
            IconButton({ if (levels > 1) levels-- }) { Icon(Icons.Default.Remove, "Fewer levels") }
            Text("$levels", fontWeight = FontWeight.SemiBold)
            IconButton({ if (levels < 9) levels++ }) { Icon(Icons.Default.Add, "More levels") }
        }
        listOf(
            Triple("Show page numbers", pageNumbers, { v: Boolean -> pageNumbers = v }),
            Triple("Right-align page numbers", rightAlign, { v: Boolean -> rightAlign = v }),
            Triple("Use hyperlinks", hyperlinks, { v: Boolean -> hyperlinks = v }),
        )
            .forEach { (label, on, set) -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), fontSize = 14.sp); Switch(on, set) } }
        Section("Tab leader") { PillRow(listOf("Dot" to "dot", "Hyphen" to "hyphen", "Underscore" to "underscore", "None" to "none"), leader) { leader = it } }
        PrimaryButton("Insert") {
            d.id("references.toc", mapOf("title" to title, "levels" to levels, "pageNumbers" to pageNumbers, "rightAlign" to rightAlign, "tabLeader" to leader, "hyperlinks" to hyperlinks))
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton({ d.id("references.updateToc") }, shape = RoundedCornerShape(12.dp)) { Text("Update table") }
        OutlinedButton({ d.id("references.removeToc") }, shape = RoundedCornerShape(12.dp)) { Text("Remove") }
    }
}

// ---------------------------------------------------------------------------------------------
// Navigation: Headings / Pages / Results
// ---------------------------------------------------------------------------------------------

@Composable
internal fun NavigationContent(ui: EditorUiState, vm: EditorViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Headings", "Pages", "Results").forEachIndexed { i, n -> Pill(n, tab == i) { tab = i } }
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    when (tab) {
        0 -> HeadingsTab(ui, vm)
        1 -> PagesTab(ui, vm)
        else -> ResultsTab(vm)
    }
}

@Composable
private fun HeadingsTab(ui: EditorUiState, vm: EditorViewModel) {
    var heads by remember { mutableStateOf(listOf<HeadingItem>()) }
    val rev = ui.state?.revision
    LaunchedEffect(rev) { vm.loadHeadings { heads = it } }
    if (heads.isEmpty()) Text("No headings yet. Apply a Heading style to build the outline.", fontSize = 13.sp, color = Color.Gray)
    heads.forEach { h ->
        val lvl = h.level.toInt()
        Text(
            h.text, Modifier.fillMaxWidth().clickable { vm.jumpTo(h.pos) }.padding(start = ((lvl - 1) * 14).dp, top = 7.dp, bottom = 7.dp),
            fontSize = if (lvl == 1) 15.sp else if (lvl == 2) 14.sp else 13.sp, fontWeight = if (lvl == 1) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PagesTab(ui: EditorUiState, vm: EditorViewModel) {
    val n = ui.pages.size
    val rev = ui.state?.revision
    val current = ui.caret?.page?.toInt() ?: 0
    if (n == 0) Text("No pages.", fontSize = 13.sp, color = Color.Gray)
    (0 until n.coerceAtMost(80)).chunked(2).forEach { rowIdx ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rowIdx.forEach { i ->
                val page = ui.pages[i]
                val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, i, rev) { value = vm.render(i, 0.2f)?.asImageBitmap() }
                Column(Modifier.weight(1f).clickable { vm.goToPage(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio((page.widthPt.toFloat() / page.heightPt.toFloat()).coerceIn(0.4f, 2.5f)).background(Color.White)
                            .border(if (i == current) 3.dp else 1.dp, if (i == current) WordBlue else Color(0xFFD0D5DD), RoundedCornerShape(3.dp)),
                    ) { bmp?.let { Image(it, "Page ${i + 1}", Modifier.fillMaxSize()) } }
                    Text("${i + 1}", fontSize = 11.sp, color = if (i == current) WordBlue else Color.Gray, modifier = Modifier.padding(top = 3.dp))
                }
            }
            if (rowIdx.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ResultsTab(vm: EditorViewModel) {
    var q by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var whole by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf(listOf<SearchHit>()) }
    LaunchedEffect(q, matchCase, whole) {
        if (q.length < 2) { hits = emptyList(); return@LaunchedEffect }
        delay(250)
        vm.searchText(q, matchCase, whole) { hits = it }
    }
    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search the document") }, leadingIcon = { Icon(Icons.Default.Search, null) })
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("Match case", matchCase) { matchCase = !matchCase }
        Pill("Whole word", whole) { whole = !whole }
    }
    if (q.length >= 2) Text("${hits.size}${if (hits.size >= 300) "+" else ""} result${if (hits.size == 1) "" else "s"}", fontSize = 12.sp, color = Color.Gray)
    hits.forEach { h ->
        val s = h.matchStart.toInt(); val l = h.matchLen.toInt()
        val text = buildAnnotatedString {
            append(h.snippet.take(s))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = Color(0xFFFFF3A8))) { append(h.snippet.drop(s).take(l)) }
            append(h.snippet.drop(s + l))
        }
        Text(text, Modifier.fillMaxWidth().clickable { vm.jumpTo(h.start, h.end) }.padding(vertical = 7.dp), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        HorizontalDivider(color = Color(0xFFEAECF0))
    }
}
