package com.wordcraft.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatIndentDecrease
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.Subscript
import androidx.compose.material.icons.filled.Superscript
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

/** Icons for the commands people use most; every other command is a text button. */
fun commandIcon(id: String): ImageVector? = when (id) {
    "format.bold" -> Icons.Default.FormatBold
    "format.italic" -> Icons.Default.FormatItalic
    "format.underline" -> Icons.Default.FormatUnderlined
    "format.strikethrough" -> Icons.Default.StrikethroughS
    "format.subscript" -> Icons.Default.Subscript
    "format.superscript" -> Icons.Default.Superscript
    "format.color" -> Icons.Default.FormatColorText
    "format.highlight" -> Icons.Default.FormatColorFill
    "format.clear" -> Icons.Default.FormatClear
    "para.bullets" -> Icons.Default.FormatListBulleted
    "para.numbering" -> Icons.Default.FormatListNumbered
    "para.indent" -> Icons.Default.FormatIndentIncrease
    "para.outdent" -> Icons.Default.FormatIndentDecrease
    "para.alignLeft" -> Icons.Default.FormatAlignLeft
    "para.alignCenter" -> Icons.Default.FormatAlignCenter
    "para.alignRight" -> Icons.Default.FormatAlignRight
    "para.justify" -> Icons.Default.FormatAlignJustify
    "para.lineSpacing" -> Icons.Default.FormatLineSpacing
    "edit.cut" -> Icons.Default.ContentCut
    "edit.copy" -> Icons.Default.ContentCopy
    "edit.paste" -> Icons.Default.ContentPaste
    "edit.find" -> Icons.Default.Search
    "edit.replace" -> Icons.Default.FindReplace
    "edit.undo" -> Icons.Default.Undo
    "edit.redo" -> Icons.Default.Redo
    "insert.picture" -> Icons.Default.ImageIcon
    "insert.table" -> Icons.Default.TableChart
    "insert.link" -> Icons.Default.Link
    "file.save" -> Icons.Default.Save
    "file.open" -> Icons.Default.FolderOpen
    else -> null
}

/** Compact Word-style title bar: save, undo, redo, document name, command search, overflow menu. */
@Composable
fun WordTitleBar(
    title: String,
    saved: Boolean,
    onSave: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSearch: () -> Unit,
    onMenuItem: (String) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(ChromeGrey).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onSave) { Icon(Icons.Default.Save, "Save") }
        IconButton(onClick = onUndo) { Icon(Icons.Default.Undo, "Undo") }
        IconButton(onClick = onRedo) { Icon(Icons.Default.Redo, "Redo") }
        Text(
            "$title — ${if (saved) "Saved" else "Not saved"}",
            Modifier.weight(1f).padding(horizontal = 6.dp),
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search commands") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf("New" to "file.new", "Open…" to "file.open", "Save as DOCX…" to "file.saveAs",
                    "Export PDF…" to "file.exportPdf", "Fit page to screen" to "view.pageWidth").forEach { (label, id) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; onMenuItem(id) })
                }
            }
        }
    }
}

/**
 * Bottom quick-action bar: panel toggles on the left, the most-used editing actions after them.
 * It stays above the keyboard.
 */
@Composable
fun QuickBar(
    navOpen: Boolean,
    stylesOpen: Boolean,
    formatOpen: Boolean,
    onToggleNav: () -> Unit,
    onToggleStyles: () -> Unit,
    onToggleFormat: () -> Unit,
    onAction: (String, String) -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        HorizontalDivider(color = Color(0xFFE1E1E1))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelToggle(Icons.Default.Menu, "Navigation", navOpen, onToggleNav)
            PanelToggle(Icons.Default.Title, "Styles", stylesOpen, onToggleStyles)
            PanelToggle(Icons.Default.Palette, "Format", formatOpen, onToggleFormat)
            Box(Modifier.width(1.dp).height(28.dp).background(Color(0xFFD0D0D0)))
            listOf(
                "format.bold" to "{}", "format.italic" to "{}", "format.underline" to "{}",
                "para.bullets" to "{}", "para.numbering" to "{}",
                "para.alignLeft" to "{}", "para.alignCenter" to "{}", "para.alignRight" to "{}",
                "para.indent" to "{}", "para.outdent" to "{}",
                "insert.picture" to "{}", "insert.table" to "{\"rows\":3,\"cols\":3}", "insert.link" to "{}"
            ).forEach { (id, params) ->
                IconButton(onClick = { onAction(id, params) }, Modifier.size(46.dp)) {
                    Icon(commandIcon(id) ?: Icons.Default.Menu, id, Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun PanelToggle(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 2.dp).size(42.dp)
            .background(if (active) Color(0xFFDCE8FA) else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = if (active) WordBlue else Color(0xFF242424))
    }
}

/**
 * A card that floats over the document. Drag the title bar to move it anywhere inside
 * ([boundsWidthPx] x [boundsHeightPx]); the arrow minimises it to just the title bar.
 */
@Composable
fun FloatingPanel(
    title: String,
    boundsWidthPx: Float,
    boundsHeightPx: Float,
    initialOffset: Offset,
    onClose: () -> Unit,
    bodyHeight: androidx.compose.ui.unit.Dp = 320.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val widthDp = 270.dp
    val widthPx = with(density) { widthDp.toPx() }
    val headerPx = with(density) { 44.dp.toPx() }
    var offset by remember { mutableStateOf(initialOffset) }
    var minimized by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .width(widthDp),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(ChromeGrey)
                .pointerInput(boundsWidthPx, boundsHeightPx) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val maxX = (boundsWidthPx - widthPx).coerceAtLeast(0f)
                        val maxY = (boundsHeightPx - headerPx).coerceAtLeast(0f)
                        offset = Offset(
                            (offset.x + drag.x).coerceIn(0f, maxX),
                            (offset.y + drag.y).coerceIn(0f, maxY)
                        )
                    }
                }
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⠿  $title", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { minimized = !minimized }, Modifier.size(40.dp)) {
                Icon(if (minimized) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, "Minimise")
            }
            IconButton(onClick = onClose, Modifier.size(40.dp)) { Icon(Icons.Default.Close, "Close") }
        }
        if (!minimized) {
            Column(Modifier.fillMaxWidth().height(bodyHeight).padding(8.dp), content = content)
        }
    }
}

private data class Heading(val level: Int, val text: String, val pos: String)

/** Navigation: Headings (jump to a heading), Pages (thumbnails), Results (find). */
@Composable
fun NavigationPanelContent(state: EditorState) {
    var tab by remember { mutableStateOf("Headings") }
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Headings", "Pages", "Results").forEach { name ->
            Box(
                Modifier
                    .background(if (tab == name) Color(0xFFDCE8FA) else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { tab = name }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(name, fontSize = 13.sp, color = if (tab == name) WordBlue else Color(0xFF242424))
            }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    when (tab) {
        "Headings" -> {
            var headings by remember { mutableStateOf<List<Heading>>(emptyList()) }
            LaunchedEffect(state.revision) {
                headings = withContext(Dispatchers.IO) {
                    runCatching {
                        val arr = JSONObject(NativeBridge.nativeHeadings()).optJSONArray("headings")
                        (0 until (arr?.length() ?: 0)).map { i ->
                            val o = arr!!.getJSONObject(i)
                            Heading(o.getInt("level"), o.getString("text"), o.getJSONObject("pos").toString())
                        }
                    }.getOrDefault(emptyList())
                }
            }
            if (headings.isEmpty()) {
                Text("No headings yet. Apply Heading 1–3 from the Styles panel.", fontSize = 12.sp, color = Color(0xFF616161))
            }
            LazyColumn {
                items(headings) { h ->
                    Text(
                        h.text,
                        Modifier.fillMaxWidth().clickable { scope.launch { state.goTo(h.pos) } }
                            .padding(start = ((h.level - 1).coerceIn(0, 5) * 14).dp, top = 8.dp, bottom = 8.dp),
                        fontSize = if (h.level == 1) 15.sp else 13.sp,
                        fontWeight = if (h.level == 1) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2
                    )
                }
            }
        }
        "Pages" -> {
            LazyColumn(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(state.pages) { index, _ ->
                    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(index, state.revision) {
                        state.renderPage(index, 0.22f)?.let { thumb = it.asImageBitmap() }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth(0.6f).border(1.dp, Color(0xFFCCCCCC)).background(Color.White)
                                .clickable { state.scrollTo(index) }
                        ) {
                            thumb?.let { Image(it, "Page ${index + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
                        }
                        Text("${index + 1}", fontSize = 12.sp, color = Color(0xFF616161))
                    }
                }
            }
        }
        else -> {
            var query by remember { mutableStateOf("") }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Search document") }, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    scope.launch {
                        state.command("edit.find", JSONObject().put("text", query).toString(), edits = false)
                        state.focus?.let { state.scrollTo(it.page, (it.top - 40f).coerceAtLeast(0f)) }
                    }
                }) { Text("Find") }
                TextButton(onClick = {
                    scope.launch {
                        state.command("edit.findPrevious", JSONObject().put("text", query).toString(), edits = false)
                        state.focus?.let { state.scrollTo(it.page, (it.top - 40f).coerceAtLeast(0f)) }
                    }
                }) { Text("Previous") }
                TextButton(onClick = {
                    scope.launch {
                        state.command("edit.findNext", JSONObject().put("text", query).toString(), edits = false)
                        state.focus?.let { state.scrollTo(it.page, (it.top - 40f).coerceAtLeast(0f)) }
                    }
                }) { Text("Next") }
            }
            if (state.status.isNotBlank()) Text(state.status, fontSize = 12.sp, color = Color(0xFF475467), maxLines = 4)
        }
    }
}

/** Paragraph styles. Each button applies the style to the paragraph at the caret. */
@Composable
fun StylesPanelContent(onRun: (String, String) -> Unit) {
    val styles = listOf(
        Triple("Normal", "para.normal", "{}"),
        Triple("Title", "para.style", "{\"style\":\"Title\"}"),
        Triple("Subtitle", "para.style", "{\"style\":\"Subtitle\"}"),
        Triple("Heading 1", "para.heading1", "{}"),
        Triple("Heading 2", "para.heading2", "{}"),
        Triple("Heading 3", "para.heading3", "{}"),
        Triple("Quote", "para.style", "{\"style\":\"Quote\"}"),
        Triple("List Paragraph", "para.style", "{\"style\":\"ListParagraph\"}")
    )
    LazyColumn {
        items(styles) { (label, id, params) ->
            val (size, weight, color) = when (label) {
                "Title" -> Triple(24.sp, FontWeight.Normal, Color(0xFF000000))
                "Subtitle" -> Triple(15.sp, FontWeight.Normal, Color(0xFF595959))
                "Heading 1" -> Triple(19.sp, FontWeight.Normal, Color(0xFF0F4761))
                "Heading 2" -> Triple(16.sp, FontWeight.Normal, Color(0xFF0F4761))
                "Heading 3" -> Triple(14.sp, FontWeight.Medium, Color(0xFF0F4761))
                "Quote" -> Triple(14.sp, FontWeight.Normal, Color(0xFF404040))
                else -> Triple(14.sp, FontWeight.Normal, Color(0xFF000000))
            }
            Text(
                label,
                Modifier.fillMaxWidth().clickable { onRun(id, params) }.padding(horizontal = 10.dp, vertical = 11.dp),
                fontSize = size, fontWeight = weight, color = color
            )
            HorizontalDivider(color = Color(0xFFEEEEEE))
        }
    }
}

/** Font size, colour and highlight at one tap. */
@Composable
fun FormatPanelContent(onRun: (String, String) -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text("Font size", fontSize = 12.sp, color = Color(0xFF616161))
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onRun("format.shrinkFont", "{}") }) { Text("A−") }
            TextButton(onClick = { onRun("format.growFont", "{}") }) { Text("A+") }
            listOf(10, 12, 14, 18, 24, 36).forEach { size ->
                TextButton(onClick = { onRun("format.size", "{\"size\":$size}") }) { Text("$size") }
            }
        }
        Text("Text colour", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = Color(0xFF616161))
        Swatches(listOf("000000", "C00000", "ED7D31", "548235", "185ABD", "7030A0", "7F7F7F")) { onRun("format.color", "{\"color\":\"$it\"}") }
        TextButton(onClick = { onRun("format.color", "{\"color\":\"auto\"}") }) { Text("Automatic colour") }
        Text("Highlight", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = Color(0xFF616161))
        Swatches(listOf("FFFF00", "00FF00", "00FFFF", "FF00FF", "FFC000")) { onRun("format.highlight", "{\"color\":\"$it\"}") }
        TextButton(onClick = { onRun("format.clear", "{}") }) { Text("Clear all formatting") }
    }
}

@Composable
private fun Swatches(hex: List<String>, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        hex.forEach { h ->
            Box(
                Modifier.size(34.dp)
                    .background(Color(("FF$h").toLong(16)), CircleShape)
                    .border(1.dp, Color(0xFFBBBBBB), CircleShape)
                    .clickable { onPick(h) }
            )
        }
    }
}
