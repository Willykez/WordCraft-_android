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
            ColorMenu("Highlight", Icons.Default.FormatColorFill, ui.isEnabled("format.highlight")) { d.id("format.highlight", mapOf("color" to it)) }
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
