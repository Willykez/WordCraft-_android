package org.wordcraft.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope // <-- ADDED THIS
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.wordcraft.android.editor.EditorUiState
import org.wordcraft.android.editor.EditorViewModel
import org.wordcraft.android.editor.ToolbarTab
import uniffi.wordcraft_android.*

/** Things a toolbar button can ask of the host activity (file pickers, clipboard). */
interface EditorActions {
    fun save()
    fun saveCopy(format: String)
    fun pickPicture()
    fun copy(cut: Boolean)
    fun paste()
    fun close()
}

// Every touch target is at least 48dp x 48dp.
@Composable
fun ToolButton(icon: ImageVector, label: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Column(
        Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 48.dp).clip(RoundedCornerShape(10.dp)).background(bg)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, Modifier.size(22.dp), tint = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = .38f))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** A button that opens a menu of choices. */
@Composable
fun MenuButton(icon: ImageVector, label: String, active: Boolean = false, items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolButton(icon, label, active) { open = true }
        DropdownMenu(open, { open = false }) {
            items.forEach { (name, act) -> DropdownMenuItem(text = { Text(name) }, onClick = { open = false; act() }) }
        }
    }
}

private val Palette = listOf(
    "Black" to "000000", "Dark red" to "C00000", "Red" to "FF0000", "Orange" to "FFA500", "Yellow" to "FFFF00",
    "Green" to "00A651", "Teal" to "008080", "Blue" to "0070C0", "Navy" to "002060", "Purple" to "7030A0", "Grey" to "7F7F7F", "White" to "FFFFFF",
)

@Composable
fun ColorButton(icon: ImageVector, label: String, onPick: (String) -> Unit, extra: Pair<String, () -> Unit>? = null) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolButton(icon, label) { open = true }
        DropdownMenu(open, { open = false }) {
            extra?.let { (n, a) -> DropdownMenuItem(text = { Text(n) }, onClick = { open = false; a() }) }
            Palette.chunked(4).forEach { row ->
                Row(Modifier.padding(horizontal = 8.dp)) {
                    row.forEach { (name, hex) ->
                        Box(
                            Modifier.padding(4.dp).size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color(android.graphics.Color.parseColor("#$hex")))
                                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)).clickable { open = false; onPick(hex) },
                        )
                    }
                }
            }
        }
    }
}

// <-- FIXED: Row changed to RowScope -->
@Composable
private fun Group(content: @Composable RowScope.() -> Unit) = Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, content = content)

/** The collapsible tab bar at the bottom: thumb-reachable, one row of tools above one row of tabs. */
@OptIn(ExperimentalMaterial3Api::class) // <-- ADDED to fix SecondaryScrollableTabRow experimental error
@Composable
fun BottomToolbar(ui: EditorUiState, vm: EditorViewModel, actions: EditorActions) {
    var dialog by remember { mutableStateOf<String?>(null) }
    Surface(tonalElevation = 3.dp) {
        Column {
            if (ui.toolbarExpanded) {
                LazyRow(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    item {
                        when (ui.tab) {
                            ToolbarTab.HOME -> HomeTools(ui, vm) { dialog = it }
                            ToolbarTab.INSERT -> InsertTools(vm, actions) { dialog = it }
                            ToolbarTab.LAYOUT -> LayoutTools(vm)
                            ToolbarTab.REVIEW -> ReviewTools(ui, vm)
                            ToolbarTab.REFERENCES -> ReferenceTools(vm)
                            ToolbarTab.VIEW -> ViewTools(ui, vm)
                        }
                    }
                }
                HorizontalDivider()
            }
            SecondaryScrollableTabRow(selectedTabIndex = ui.tab.ordinal, edgePadding = 0.dp, divider = {}) {
                ToolbarTab.entries.forEach { t ->
                    Tab(selected = ui.tab == t, onClick = { vm.setTab(t) }, text = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
        }
    }
    when (dialog) {
        "table" -> TableGridDialog({ dialog = null }) { r, c -> vm.run("insert.table", mapOf("rows" to r, "cols" to c)) }
        "link" -> PromptDialog("Insert link", "Address (https://…)", { dialog = null }) { vm.run("insert.link", mapOf("url" to it)) }
        "font" -> PromptDialog("Font", "Font name", { dialog = null }) { vm.run("format.font", mapOf("name" to it)) }
        "size" -> PromptDialog("Font size", "Points", { dialog = null }) { s -> s.toFloatOrNull()?.let { vm.run("format.size", mapOf("size" to it)) } }
    }
}

@Composable
private fun HomeTools(ui: EditorUiState, vm: EditorViewModel, openDialog: (String) -> Unit) {
    val st = ui.state
    Row(verticalAlignment = Alignment.CenterVertically) {
        Group {
            MenuButton(Icons.Default.FontDownload, st?.font ?: "Font",
                items = listOf("Roboto", "Noto Serif", "Noto Sans", "Liberation Serif", "Liberation Sans").map { f -> f to { vm.run("format.font", mapOf("name" to f)) } } +
                    ("Other…" to { openDialog("font") }))
            
            // <-- FIXED: Added explicit casting to ensure modulo calculation doesn't throw a type error when linked to UniFFI -->
            MenuButton(Icons.Default.FormatSize, st?.size?.let { 
                val floatVal = (it as Number).toFloat() 
                if (floatVal % 1f == 0f) floatVal.toInt().toString() else floatVal.toString() 
            } ?: "Size",
                items = listOf(8, 9, 10, 11, 12, 14, 16, 18, 24, 36, 48, 72).map { s -> "$s pt" to { vm.run("format.size", mapOf("size" to s)) } } + ("Other…" to { openDialog("size") }))
            
            ToolButton(Icons.Default.Add, "Larger") { vm.run("format.growFont") }
            ToolButton(Icons.Default.Remove, "Smaller") { vm.run("format.shrinkFont") }
        }
        Group {
            ToolButton(Icons.Default.FormatBold, "Bold", st?.bold == true) { vm.run("format.bold") }
            ToolButton(Icons.Default.FormatItalic, "Italic", st?.italic == true) { vm.run("format.italic") }
            ToolButton(Icons.Default.FormatUnderlined, "Underline", st?.underline == true) { vm.run("format.underline") }
            ToolButton(Icons.Default.StrikethroughS, "Strike", st?.strike == true) { vm.run("format.strikethrough") }
            ToolButton(Icons.Default.Subscript, "Sub", st?.subscript == true) { vm.run("format.subscript") }
            ToolButton(Icons.Default.Superscript, "Super", st?.superscript == true) { vm.run("format.superscript") }
        }
        Group {
            ColorButton(Icons.Default.FormatColorText, "Colour", { vm.run("format.color", mapOf("color" to it)) }, "Automatic" to { vm.run("format.color", mapOf("color" to "auto")) })
            ColorButton(Icons.Default.FormatColorFill, "Highlight", { vm.run("format.highlight", mapOf("color" to it)) })
            ToolButton(Icons.Default.FormatClear, "Clear") { vm.run("format.clear") }
            ToolButton(Icons.Default.FormatPaint, "Painter") { vm.run("edit.formatPainter") }
        }
        Group {
            val al = st?.alignment ?: "left"
            ToolButton(Icons.Default.FormatAlignLeft, "Left", al == "left") { vm.run("para.alignLeft") }
            ToolButton(Icons.Default.FormatAlignCenter, "Centre", al == "center") { vm.run("para.alignCenter") }
            ToolButton(Icons.Default.FormatAlignRight, "Right", al == "right") { vm.run("para.alignRight") }
            ToolButton(Icons.Default.FormatAlignJustify, "Justify", al == "justify") { vm.run("para.justify") }
        }
        Group {
            ToolButton(Icons.Default.FormatListBulleted, "Bullets") { vm.run("para.bullets") }
            ToolButton(Icons.Default.FormatListNumbered, "Numbers") { vm.run("para.numbering") }
            ToolButton(Icons.Default.FormatIndentDecrease, "Outdent") { vm.run("para.outdent") }
            ToolButton(Icons.Default.FormatIndentIncrease, "Indent") { vm.run("para.indent") }
            MenuButton(Icons.Default.FormatLineSpacing, "Spacing", items = listOf("Single" to { vm.run("para.single") }, "Double" to { vm.run("para.double") }))
        }
        Group {
            MenuButton(Icons.Default.Style, st?.styleName ?: "Style",
                items = listOf("Normal", "Title", "Subtitle", "Heading 1", "Heading 2", "Heading 3", "Quote").map { n -> n to { vm.run("para.style", mapOf("style" to n)) } })
        }
    }
}

@Composable
private fun InsertTools(vm: EditorViewModel, actions: EditorActions, openDialog: (String) -> Unit) = Row {
    Group {
        ToolButton(Icons.Default.TableChart, "Table") { openDialog("table") }
        ToolButton(Icons.Default.Image, "Picture") { actions.pickPicture() }
        MenuButton(Icons.Default.Category, "Shapes",
            items = listOf("rectangle", "roundedRectangle", "ellipse", "triangle", "diamond", "star", "heart", "line", "arrow").map { k -> k.replaceFirstChar { it.uppercase() } to { vm.run("insert.shape", mapOf("kind" to k)) } })
        ToolButton(Icons.Default.Link, "Link") { openDialog("link") }
        ToolButton(Icons.Default.CropSquare, "Text box") { vm.run("insert.textBox") }
    }
    Group {
        ToolButton(Icons.Default.Numbers, "Page no.") { vm.run("insert.pageNumber", mapOf("position" to "bottom", "align" to "center")) }
        ToolButton(Icons.Default.VerticalAlignTop, "Header") { vm.run("insert.header", mapOf("preset" to "blank")) }
        ToolButton(Icons.Default.VerticalAlignBottom, "Footer") { vm.run("insert.footer", mapOf("preset" to "blank")) }
        ToolButton(Icons.Default.InsertPageBreak, "Break") { vm.run("insert.pageBreak") }
    }
}

@Composable
private fun LayoutTools(vm: EditorViewModel) = Row {
    Group {
        MenuButton(Icons.Default.Crop, "Margins", items = listOf("normal", "narrow", "moderate", "wide").map { p -> p.replaceFirstChar { it.uppercase() } to { vm.run("layout.margins", mapOf("preset" to p)) } })
        MenuButton(Icons.Default.ScreenRotation, "Orientation", items = listOf("Portrait" to { vm.run("layout.orientation", mapOf("value" to "portrait")) }, "Landscape" to { vm.run("layout.orientation", mapOf("value" to "landscape")) }))
        MenuButton(Icons.Default.Description, "Size", items = listOf("A4", "A5", "Letter", "Legal").map { n -> n to { vm.run("layout.size", mapOf("name" to n)) } })
        MenuButton(Icons.Default.ViewColumn, "Columns", items = listOf(1, 2, 3).map { n -> "$n column${if (n > 1) "s" else ""}" to { vm.run("layout.columns", mapOf("count" to n)) } })
        MenuButton(Icons.Default.InsertPageBreak, "Breaks", items = listOf("Page" to "page", "Column" to "column", "Next page section" to "nextPage", "Continuous section" to "continuous").map { (n, k) -> n to { vm.run("layout.break", mapOf("kind" to k)) } })
    }
}

@Composable
private fun ReviewTools(ui: EditorUiState, vm: EditorViewModel) = Row {
    Group {
        ToolButton(Icons.Default.SpellCheck, "Spelling") { vm.run("review.spelling") { vm.say(it.take(300)) } }
        ToolButton(Icons.Default.Numbers, "${ui.state?.wordCount ?: 0} words") { vm.say("${ui.state?.wordCount ?: 0} words, ${ui.pages.size} pages") }
        ToolButton(Icons.Default.Comment, "Comments") { vm.run("review.comments") { vm.say(it.take(300)) } }
        ToolButton(Icons.Default.EditNote, "Track") { vm.run("review.trackChanges") }
        ToolButton(Icons.Default.DoneAll, "Accept all") { vm.run("review.acceptAll") }
        ToolButton(Icons.Default.Close, "Reject all") { vm.run("review.rejectAll") }
    }
}

@Composable
private fun ReferenceTools(vm: EditorViewModel) = Row {
    Group {
        ToolButton(Icons.Default.Toc, "Contents") { vm.run("references.toc") }
        ToolButton(Icons.Default.Notes, "Footnote") { vm.run("references.footnote") }
        ToolButton(Icons.Default.Notes, "Endnote") { vm.run("references.endnote") }
        ToolButton(Icons.Default.FormatQuote, "Citation") { vm.run("references.citation") }
        ToolButton(Icons.Default.MenuBook, "Bibliography") { vm.run("references.bibliography") }
    }
}

@Composable
private fun ViewTools(ui: EditorUiState, vm: EditorViewModel) = Row {
    Group {
        ToolButton(Icons.Default.ZoomIn, "Zoom in") { vm.setZoom(ui.zoom * 1.25f) }
        ToolButton(Icons.Default.ZoomOut, "Zoom out") { vm.setZoom(ui.zoom / 1.25f) }
        ToolButton(Icons.Default.FitScreen, "Fit width") { vm.setZoom(1f) }
        ToolButton(Icons.Default.Search, "Find") { vm.setFind(true) }
        ToolButton(Icons.Default.Pin, "Marks") { vm.run("view.marks") }
    }
}

/** Appears above the tabs when it applies: a text selection, a table cell, or a picture. */
@Composable
fun ContextualBar(ui: EditorUiState, vm: EditorViewModel, actions: EditorActions) {
    val st = ui.state ?: return
    if (!st.hasSelection && !st.inTable && !st.pictureSelected) return
    Surface(tonalElevation = 6.dp, shadowElevation = 4.dp, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
        LazyRow(Modifier.fillMaxWidth()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (st.hasSelection) Group {
                        ToolButton(Icons.Default.ContentCut, "Cut") { actions.copy(true) }
                        ToolButton(Icons.Default.ContentCopy, "Copy") { actions.copy(false) }
                        ToolButton(Icons.Default.ContentPaste, "Paste") { actions.paste() }
                        ToolButton(Icons.Default.FormatBold, "Bold", st.bold) { vm.run("format.bold") }
                        ToolButton(Icons.Default.FormatItalic, "Italic", st.italic) { vm.run("format.italic") }
                        ColorButton(Icons.Default.FormatColorFill, "Highlight", { vm.run("format.highlight", mapOf("color" to it)) })
                    }
                    if (st.pictureSelected) Group {
                        ToolButton(Icons.Default.ZoomOut, "Smaller") { vm.run("picture.size", mapOf("scale" to 80)) }
                        ToolButton(Icons.Default.ZoomIn, "Larger") { vm.run("picture.size", mapOf("scale" to 125)) }
                        ToolButton(Icons.Default.Restore, "Original") { vm.run("picture.size", mapOf("original" to true)) }
                        ToolButton(Icons.Default.Delete, "Delete") { vm.run("text.delete") }
                    }
                    if (st.inTable) Group {
                        ToolButton(Icons.Default.TableRows, "Row below") { vm.run("table.insertRowBelow") }
                        ToolButton(Icons.Default.TableRows, "Row above") { vm.run("table.insertRowAbove") }
                        ToolButton(Icons.Default.ViewColumn, "Col right") { vm.run("table.insertColumnRight") }
                        ToolButton(Icons.Default.ViewColumn, "Col left") { vm.run("table.insertColumnLeft") }
                        ToolButton(Icons.Default.CallMerge, "Merge") { vm.run("table.merge") }
                        ToolButton(Icons.Default.CallSplit, "Split") { vm.run("table.split") }
                        MenuButton(Icons.Default.SelectAll, "Select", items = listOf("Cell" to "table.selectCell", "Row" to "table.selectRow", "Column" to "table.selectColumn", "Table" to "table.selectTable").map { (n, c) -> n to { vm.run(c) } })
                        MenuButton(Icons.Default.Delete, "Delete", items = listOf("Row" to "table.deleteRow", "Column" to "table.deleteColumn", "Table" to "table.deleteTable").map { (n, c) -> n to { vm.run(c) } })
                    }
                }
            }
        }
    }
}

@Composable
fun FindBar(vm: EditorViewModel) {
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    Surface(tonalElevation = 6.dp) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(find, { find = it; if (it.isNotEmpty()) vm.run("edit.find", mapOf("text" to it)) }, Modifier.weight(1f), singleLine = true, label = { Text("Find") })
                IconButton({ vm.run("edit.findPrevious") }) { Icon(Icons.Default.KeyboardArrowUp, "Previous") }
                IconButton({ vm.run("edit.findNext") }) { Icon(Icons.Default.KeyboardArrowDown, "Next") }
                IconButton({ vm.setFind(false) }) { Icon(Icons.Default.Close, "Close") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(replace, { replace = it }, Modifier.weight(1f), singleLine = true, label = { Text("Replace with") })
                TextButton({ vm.run("edit.replace", mapOf("text" to find, "with" to replace)) }) { Text("Replace") }
                TextButton({ vm.run("edit.replaceAll", mapOf("text" to find, "with" to replace)) }) { Text("All") }
            }
        }
    }
}

@Composable
fun TableGridDialog(onDismiss: () -> Unit, onPick: (rows: Int, cols: Int) -> Unit) {
    var rows by remember { mutableIntStateOf(3) }
    var cols by remember { mutableIntStateOf(3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert table: $rows × $cols") },
        text = {
            Column {
                Text("Tap a cell to choose the size.", style = MaterialTheme.typography.bodySmall)
                for (r in 1..8) Row {
                    for (c in 1..6) {
                        val on = r <= rows && c <= cols
                        Box(
                            Modifier.padding(2.dp).size(38.dp).background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = .6f) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { rows = r; cols = c },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton({ onDismiss(); onPick(rows, cols) }) { Text("Insert") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PromptDialog(title: String, label: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton({ onDismiss(); if (text.isNotBlank()) onOk(text.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
