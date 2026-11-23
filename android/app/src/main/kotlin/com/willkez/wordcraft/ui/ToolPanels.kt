package com.willkez.wordcraft.ui

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
import com.willkez.wordcraft.editor.EditorUiState
import com.willkez.wordcraft.editor.EditorViewModel
import com.willkez.wordcraft.bridge.HeadingItem
import com.willkez.wordcraft.bridge.SearchHit

// ---------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------

private val PILL_ON: Color get() = if (AppTheme.dark) Color(0xFF33425F) else Color(0xFFDCE8FA)
private val PILL_OFF: Color get() = if (AppTheme.dark) Color(0xFF38383E) else Color(0xFFF2F4F7)

@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) PILL_ON else PILL_OFF)
            .border(if (selected) 1.5.dp else 0.dp, if (selected) WordBlue else Color.Transparent, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, fontSize = 13.sp, color = if (selected) (if (AppTheme.dark) Color(0xFF9DBBFF) else WordBlue) else SheetInk, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PillRow(items: List<Pair<String, String>>, selected: String?, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, value) -> Pill(label, selected == value) { onPick(value) } }
    }
}

private val SHADES = listOf("FFFFFF", "F2F2F2", "D9D9D9", "FFF2CC", "FCE4D6", "E2EFDA", "DDEBF7", "E4DFEC", "FFFF00", "BDD7EE", "C6E0B4")
private val PANEL_LINE_COLORS = listOf("000000", "595959", "A6A6A6", "C00000", "ED7D31", "FFC000", "548235", "00B0F0", "185ABD", "7030A0")

private fun col(hex: String) = Color(0xFF000000 or hex.toLong(16))

@Composable
private fun PanelSwatchRow(colors: List<String>, selected: String?, onPick: (String) -> Unit) {
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
private fun PanelPrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) =
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
                PanelSwatchRow(PANEL_LINE_COLORS + SHADES.drop(8), values[f.name]) { values[f.name] = it }
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
    PanelPrimaryButton("Apply", !missing) { vm.submit(cmd, buildJson(cmd, values)) }
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
