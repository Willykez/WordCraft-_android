package org.wordcraft.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.wordcraft.android.editor.EditorUiState
import org.wordcraft.android.editor.EditorViewModel
import kotlin.math.roundToInt

/** Page, word count and zoom, like Word's status bar. */
@Composable
fun StatusBar(ui: EditorUiState, vm: EditorViewModel) {
    val st = ui.state
    Surface(color = WordBlue, contentColor = Color.White) {
        Row(Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Page ${(ui.caret?.page?.toInt() ?: 0) + 1} of ${ui.pages.size.coerceAtLeast(1)}", fontSize = 11.sp)
            Text("${st?.wordCount ?: 0u} words", fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(if (ui.showRuler) "Ruler ✓" else "Ruler", Modifier.clickable { vm.toggleRuler() }, fontSize = 11.sp)
            Text("${(ui.zoom * 100).roundToInt()}%", Modifier.clickable { vm.openPanel("zoom") }, fontSize = 11.sp)
        }
    }
}

/** The bottom quick-action bar: the things you reach for while typing, one thumb away. */
@Composable
fun QuickBar(ui: EditorUiState, d: Dispatcher, vm: EditorViewModel, keyboardShown: Boolean, onKeyboard: () -> Unit) {
    val st = ui.state
    @Composable
    fun q(id: String, icon: ImageVector, label: String, active: Boolean = false, enabled: Boolean = ui.isEnabled(id), onClick: () -> Unit = { d.id(id) }) =
        MiniToggle(icon, label, active, enabled, onClick)

    Surface(color = Color.White, shadowElevation = 12.dp, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            QuickIcon(if (keyboardShown) Icons.Default.KeyboardHide else Icons.Default.Keyboard, "Keyboard", false, true, onKeyboard)
            if (st?.hasSelection == true) {
                q("edit.cut", Icons.Default.ContentCut, "Cut")
                q("edit.copy", Icons.Default.ContentCopy, "Copy")
            }
            q("edit.paste", Icons.Default.ContentPaste, "Paste")
            VerticalDivider(Modifier.height(22.dp))
            QuickIcon(Icons.Default.Undo, "Undo", false, st?.canUndo == true) { vm.undo() }
            QuickIcon(Icons.Default.Redo, "Redo", false, st?.canRedo == true) { vm.redo() }
            VerticalDivider(Modifier.height(22.dp))
            q("format.bold", Icons.Default.FormatBold, "Bold", st?.bold == true)
            q("format.italic", Icons.Default.FormatItalic, "Italic", st?.italic == true)
            q("format.underline", Icons.Default.FormatUnderlined, "Underline", st?.underline == true)
            HighlightMenu(ui.isEnabled("format.highlight"), st?.highlight) { d.id("format.highlight", mapOf("color" to it)) }
            VerticalDivider(Modifier.height(22.dp))
            q("para.bullets", Icons.Default.FormatListBulleted, "Bullets")
            q("para.numbering", Icons.Default.FormatListNumbered, "Numbering")
            q("para.alignLeft", Icons.Default.FormatAlignLeft, "Left", st?.alignment == "left")
            q("para.alignCenter", Icons.Default.FormatAlignCenter, "Center", st?.alignment == "center")
            VerticalDivider(Modifier.height(22.dp))
            q("insert.picture", Icons.Default.Image, "Picture")
            q("insert.table", Icons.Default.TableChart, "Table")
            q("review.newComment", Icons.Default.Comment, "Comment", enabled = true, onClick = { vm.openPanel("comments") })
            QuickIcon(Icons.Default.Search, "Find", "find" in ui.panels, true) { vm.togglePanel("find") }
        }
    }
}

@Composable
private fun QuickIcon(icon: ImageVector, label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) =
    MiniToggle(icon, label, active, enabled, onClick)

/** Word's mini toolbar: formatting at hand whenever text is selected. */
@Composable
fun MiniToolbar(ui: EditorUiState, d: Dispatcher, modifier: Modifier = Modifier) {
    val st = ui.state ?: return
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = Color.White, shadowElevation = 10.dp, tonalElevation = 2.dp) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Combo(st.font ?: "Calibri", 104, FONTS.map { f -> MenuEntry(f) { d.id("format.font", mapOf("name" to f)) } })
            Spacer(Modifier.width(4.dp))
            Combo(st.size?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() } ?: "11", 48, SIZES.map { s -> MenuEntry("$s") { d.id("format.size", mapOf("size" to s)) } })
            Tool(ui, d, "format.bold", "Bold", Icons.Default.FormatBold, st.bold)
            Tool(ui, d, "format.italic", "Italic", Icons.Default.FormatItalic, st.italic)
            Tool(ui, d, "format.underline", "Underline", Icons.Default.FormatUnderlined, st.underline)
            HighlightMenu(ui.isEnabled("format.highlight"), st.highlight) { d.id("format.highlight", mapOf("color" to it)) }
            ColorMenu("Font color", Icons.Default.FormatColorText, ui.isEnabled("format.color"), st.color ?: "auto", Color.Red) { d.id("format.color", mapOf("color" to it)) }
            Tool(ui, d, "para.bullets", "Bullets", Icons.Default.FormatListBulleted)
            Tool(ui, d, "format.clear", "Clear formatting", Icons.Default.FormatClear)
        }
    }
}

/** Tap a squiggled word: the problem, its suggestions, and Ignore / Add to dictionary. */
@Composable
fun ProofBar(ui: EditorUiState, vm: EditorViewModel, modifier: Modifier = Modifier) {
    val issue = ui.issue ?: return
    val suggestions = remember(issue) { issue.optJSONArray("suggestions")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty() }
    val grammar = issue.optString("kind") == "grammar"
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = Color.White, shadowElevation = 10.dp, tonalElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (grammar) "Grammar: " else "Spelling: ") + issue.optString("message").ifEmpty { "\"${issue.optString("text")}\"" },
                    Modifier.weight(1f), fontSize = 12.sp, color = Color(0xFFC00000),
                )
                IconButton(vm::dismissIssue, Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Dismiss", Modifier.size(16.dp)) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (suggestions.isEmpty()) Text("No suggestions", fontSize = 12.sp, color = Color.Gray)
                suggestions.forEach { sg -> AssistChip({ vm.fixIssue("review.applySuggestion", mapOf("text" to sg)) }, { Text(sg) }) }
                VerticalDivider(Modifier.height(20.dp))
                TextButton({ vm.fixIssue("review.ignoreAll") }) { Text("Ignore all", fontSize = 12.sp) }
                if (!grammar) TextButton({ vm.fixIssue("review.addToDictionary") }) { Text("Add to dictionary", fontSize = 12.sp) }
            }
        }
    }
}
