package org.wordcraft.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import org.wordcraft.android.editor.EditorUiState
import org.wordcraft.android.editor.EditorViewModel
import kotlin.math.max
import kotlin.math.roundToInt

/** A panel the user can drag by its title bar, collapse, and close; it stays inside `bounds`. */
@Composable
fun FloatingPanel(
    title: String, bounds: IntSize, start: IntOffset, width: Dp, onClose: () -> Unit, onFocus: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var off by remember { mutableStateOf(start) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var collapsed by remember { mutableStateOf(false) }
    fun clamp(o: IntOffset) = IntOffset(o.x.coerceIn(0, max(0, bounds.width - size.width)), o.y.coerceIn(0, max(0, bounds.height - size.height)))
    Surface(
        Modifier.offset { clamp(off) }.width(width).onSizeChanged { size = it },
        shape = RoundedCornerShape(12.dp), shadowElevation = 16.dp, tonalElevation = 3.dp,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(WordBlue)
                    .pointerInput(bounds) {
                        detectDragGestures(onDragStart = { onFocus() }) { change, drag ->
                            change.consume()
                            off = clamp(IntOffset(off.x + drag.x.roundToInt(), off.y + drag.y.roundToInt()))
                        }
                    }
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.DragHandle, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                Text(title, Modifier.weight(1f).padding(start = 6.dp), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                IconButton({ collapsed = !collapsed }, Modifier.size(36.dp)) {
                    Icon(if (collapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, "Collapse", tint = Color.White)
                }
                IconButton(onClose, Modifier.size(36.dp)) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
            if (!collapsed) {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(12.dp), content = content)
            }
        }
    }
}

/** Lays the open panels over the page area, back to front. */
@Composable
fun PanelHost(ui: EditorUiState, vm: EditorViewModel, d: Dispatcher, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val bounds = IntSize(constraints.maxWidth, constraints.maxHeight)
        val w = (maxWidth - 24.dp).coerceAtMost(320.dp)
        ui.panels.forEachIndexed { i, id ->
            key(id) {
                val name = PANELS.firstOrNull { it.first == id }?.second ?: id
                val density = androidx.compose.ui.platform.LocalDensity.current
                val start = with(density) { IntOffset(12.dp.roundToPx() + i * 28.dp.roundToPx(), 12.dp.roundToPx() + i * 36.dp.roundToPx()) }
                FloatingPanel(name, bounds, start, w, onClose = { vm.closePanel(id) }, onFocus = { vm.openPanel(id) }) {
                    when (id) {
                        "find" -> FindContent(vm)
                        "nav" -> NavContent(ui, vm)
                        "styles" -> StylesContent(ui, d)
                        "format" -> FormatContent(ui, d)
                        "comments" -> CommentsContent(ui, vm)
                        "clipboard" -> ClipboardContent(vm, d)
                        "info" -> InfoContent(ui, vm)
                        "zoom" -> ZoomContent(ui, vm)
                        "search" -> SearchContent(ui, d)
                    }
                }
            }
        }
    }
}

@Composable
private fun FindContent(vm: EditorViewModel) {
    var find by remember { mutableStateOf("") }
    var rep by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var whole by remember { mutableStateOf(false) }
    var regex by remember { mutableStateOf(false) }
    var count by remember { mutableStateOf<Int?>(null) }
    fun opts() = mapOf("text" to find, "matchCase" to matchCase, "wholeWord" to whole, "regex" to regex)
    fun search() {
        if (find.isNotEmpty()) vm.run("edit.find", opts()) { out -> count = runCatching { JSONObject(out).getInt("count") }.getOrNull() }
    }
    OutlinedTextField(find, { find = it; search() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Find") },
        supportingText = { count?.let { Text("$it match${if (it == 1) "" else "es"}") } })
    OutlinedTextField(rep, { rep = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Replace with") })
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(matchCase, { matchCase = !matchCase; search() }, { Text("Match case") })
        FilterChip(whole, { whole = !whole; search() }, { Text("Whole word") })
        FilterChip(regex, { regex = !regex; search() }, { Text("Regex") })
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton({ vm.run("edit.findPrevious") }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Prev") }
        OutlinedButton({ vm.run("edit.findNext") }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Next") }
        Button({ vm.run("edit.replace", opts() + ("with" to rep)) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Replace") }
        Button({ vm.run("edit.replaceAll", opts() + ("with" to rep)) { search() } }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("All") }
    }
}

@Composable
private fun NavContent(ui: EditorUiState, vm: EditorViewModel) {
    var heads by remember { mutableStateOf(listOf<Pair<Int, String>>()) }
    val rev = ui.state?.revision
    LaunchedEffect(rev) {
        vm.query("file.inspect", "{\"text\":true}") { out ->
            heads = runCatching {
                val blocks = JSONObject(out).getJSONArray("blocks")
                (0 until blocks.length()).map { blocks.getJSONObject(it) }.mapNotNull { b ->
                    val style = b.optString("style")
                    val level = Regex("^Heading ?(\\d)").find(style)?.groupValues?.get(1)?.toIntOrNull() ?: if (style == "Title") 1 else return@mapNotNull null
                    val text = b.optString("text").trim()
                    if (text.isEmpty()) null else level to text
                }
            }.getOrDefault(emptyList())
        }
    }
    Text("${ui.pages.size} pages · ${ui.state?.wordCount ?: 0u} words", fontSize = 12.sp, color = Color.Gray)
    Spacer(Modifier.height(6.dp))
    if (heads.isEmpty()) Text("No headings yet. Apply a Heading style to build the outline.", fontSize = 13.sp)
    heads.forEach { (level, text) ->
        Text(
            text, Modifier.fillMaxWidth().clickable { vm.run("select.text", mapOf("text" to text)) }.padding(start = ((level - 1) * 14).dp, top = 6.dp, bottom = 6.dp),
            fontSize = 14.sp, maxLines = 2, fontWeight = if (level == 1) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun StylesContent(ui: EditorUiState, d: Dispatcher) {
    val names = ui.styles.ifEmpty { listOf("Normal", "Heading 1", "Heading 2", "Heading 3") }
    names.forEach { n ->
        val on = ui.state?.styleName == n
        Text(
            n, Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (on) WordBlue.copy(alpha = 0.15f) else Color.Transparent)
                .clickable { d.id("para.style", mapOf("style" to n)) }.padding(8.dp),
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        )
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton({ d.id("styles.create") }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("New style") }
        OutlinedButton({ d.id("styles.manage") }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Manage") }
    }
}

@Composable
private fun FormatContent(ui: EditorUiState, d: Dispatcher) {
    val st = ui.state
    PanelSection("Font") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FONTS.forEach { f -> FilterChip(st?.font == f, { d.id("format.font", mapOf("name" to f)) }, { Text(f) }) }
        }
    }
    PanelSection("Size") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SIZES.forEach { s -> FilterChip(st?.size == s.toFloat(), { d.id("format.size", mapOf("size" to s)) }, { Text("$s") }) }
        }
    }
    PanelSection("Color") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ColorMenu("Font color", Icons.Default.FormatColorText, true) { d.id("format.color", mapOf("color" to it)) }
            ColorMenu("Highlight", Icons.Default.FormatColorFill, true) { d.id("format.highlight", mapOf("color" to it)) }
        }
    }
    val effects = listOf(
        "format.allCaps" to "All caps", "format.smallCaps" to "Small caps", "format.doubleStrikethrough" to "Double strike",
        "format.hidden" to "Hidden", "format.shadow" to "Shadow", "format.outline" to "Outline", "format.emboss" to "Emboss", "format.engrave" to "Engrave",
    ).filter { ui.ribbon?.byId?.containsKey(it.first) != false }
    PanelSection("Effects") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            effects.forEach { (id, n) -> AssistChip({ d.id(id) }, { Text(n) }, enabled = ui.isEnabled(id)) }
        }
    }
}

@Composable
private fun CommentsContent(ui: EditorUiState, vm: EditorViewModel) {
    var items by remember { mutableStateOf(listOf<JSONObject>()) }
    var draft by remember { mutableStateOf("") }
    val rev = ui.state?.revision
    fun load() = vm.query("review.comments") { out ->
        items = runCatching { JSONArray(out).let { a -> (0 until a.length()).map { a.getJSONObject(it) } } }.getOrDefault(emptyList())
    }
    LaunchedEffect(rev) { load() }
    OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth(), label = { Text("New comment on selection") }, maxLines = 3)
    Button({ vm.run("review.newComment", mapOf("text" to draft)) { draft = ""; load() } }, Modifier.padding(vertical = 6.dp), enabled = draft.isNotBlank()) { Text("Add comment") }
    if (items.isEmpty()) Text("No comments.", fontSize = 13.sp, color = Color.Gray)
    items.forEach { c ->
        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Column(Modifier.padding(8.dp)) {
                Text(c.optString("author"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(c.optString("text"), fontSize = 13.sp)
                Row {
                    TextButton({ vm.run("review.resolveComment", mapOf("id" to c.optLong("id"))) { load() } }) { Text(if (c.optBoolean("resolved")) "Resolved" else "Resolve") }
                    TextButton({ vm.run("review.deleteComment", mapOf("id" to c.optLong("id"))) { load() } }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun ClipboardContent(vm: EditorViewModel, d: Dispatcher) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.clipboardText { text = it } }
    Text(text.ifEmpty { "Nothing copied inside WordCraft yet." }, fontSize = 13.sp, maxLines = 8)
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button({ d.id("edit.paste") }, enabled = text.isNotEmpty()) { Text("Paste") }
        OutlinedButton({ vm.clipboardText { text = it } }) { Text("Refresh") }
    }
}

@Composable
private fun InfoContent(ui: EditorUiState, vm: EditorViewModel) {
    var info by remember { mutableStateOf<JSONObject?>(null) }
    val rev = ui.state?.revision
    LaunchedEffect(rev) { vm.query("file.info") { out -> info = runCatching { JSONObject(out) }.getOrNull() } }
    val o = info ?: return
    listOf("pages", "words", "paragraphs", "sections", "comments", "trackChanges", "encrypted").forEach { k ->
        if (o.has(k)) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(k.replaceFirstChar { it.uppercase() }, Modifier.weight(1f), fontSize = 13.sp, color = Color.Gray)
            Text(o.opt(k).toString(), fontSize = 13.sp)
        }
    }
    o.optJSONObject("properties")?.let { p ->
        Spacer(Modifier.height(6.dp))
        p.keys().forEach { k -> if (!p.isNull(k) && p.optString(k).isNotBlank()) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(k, Modifier.weight(1f), fontSize = 13.sp, color = Color.Gray); Text(p.optString(k), fontSize = 13.sp)
        } }
    }
}

@Composable
private fun ZoomContent(ui: EditorUiState, vm: EditorViewModel) {
    Text("${(ui.zoom * 100).roundToInt()}%", fontWeight = FontWeight.SemiBold)
    Slider(ui.zoom, { vm.setZoom(it) }, valueRange = 0.5f..4f)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0.5f, 0.75f, 1f, 1.5f, 2f, 3f).forEach { z -> AssistChip({ vm.setZoom(z) }, { Text("${(z * 100).roundToInt()}%") }) }
    }
}

/** "Tell me what you want to do": search every engine command by name. */
@Composable
private fun SearchContent(ui: EditorUiState, d: Dispatcher) {
    var q by remember { mutableStateOf("") }
    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("What do you want to do?") }, leadingIcon = { Icon(Icons.Default.Search, null) })
    val hits = remember(q, ui.ribbon) {
        val t = q.trim().lowercase()
        if (t.length < 2) emptyList() else ui.ribbon?.all.orEmpty().filter { it.label.lowercase().contains(t) || it.id.lowercase().contains(t) }.take(30)
    }
    hits.forEach { c ->
        Column(Modifier.fillMaxWidth().clickable { d(c) }.padding(vertical = 6.dp)) {
            Text(c.label, fontSize = 14.sp)
            Text(c.location + if (c.shortcut.isNotEmpty()) "  ·  ${c.shortcut.substringBefore(" / ")}" else "", fontSize = 11.sp, color = Color.Gray)
        }
    }
}
