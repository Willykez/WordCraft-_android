package org.wordcraft.android.editor

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

private const val SENTINEL = "​"

/**
 * An invisible text field that owns the soft keyboard. The engine, not Android, owns the text, so
 * every edit is turned into engine commands and the field is reset to a single zero-width marker:
 * text typed after the marker is an insertion, a missing marker is a Backspace.
 *
 * Limitation (v1): because the field is reset after every change there is no IME composition, so
 * predictive text and multi-keystroke input methods (CJK) commit per keystroke. Replacing this with
 * a `PlatformTextInputModifierNode` backed by a real `InputConnection` is the next step for them.
 */
@Composable
fun TypingSink(vm: EditorViewModel, focus: FocusRequester, onShortcut: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(SENTINEL, TextRange(1))) }
    BasicTextField(
        value = value,
        onValueChange = { nv ->
            val t = nv.text
            when {
                t.isEmpty() -> vm.backspace()
                else -> {
                    val inserted = if (t.startsWith(SENTINEL)) t.substring(1) else t.replace(SENTINEL, "")
                    if (inserted.isNotEmpty()) vm.typeText(inserted)
                }
            }
            value = TextFieldValue(SENTINEL, TextRange(1))
        },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
        modifier = Modifier
            .size(1.dp)
            .alpha(0f)
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val extend = mapOf("extend" to e.isShiftPressed)
                when {
                    e.isCtrlPressed -> when (e.key) {
                        Key.B -> vm.run("format.bold")
                        Key.I -> vm.run("format.italic")
                        Key.U -> vm.run("format.underline")
                        Key.Z -> if (e.isShiftPressed) vm.redo() else vm.undo()
                        Key.Y -> vm.redo()
                        Key.A -> vm.run("select.all")
                        Key.C -> onShortcut("copy")
                        Key.X -> onShortcut("cut")
                        Key.V -> onShortcut("paste")
                        Key.S -> onShortcut("save")
                        Key.F -> onShortcut("find")
                        else -> return@onPreviewKeyEvent false
                    }
                    e.key == Key.Enter || e.key == Key.NumPadEnter -> vm.run("text.newParagraph")
                    e.key == Key.Backspace -> vm.run(if (e.isCtrlPressed) "text.deleteWordBack" else "text.backspace")
                    e.key == Key.Delete -> vm.run("text.delete")
                    e.key == Key.Tab -> vm.run(if (e.isShiftPressed) "text.backTab" else "text.tab")
                    e.key == Key.DirectionLeft -> vm.run("caret.left", extend)
                    e.key == Key.DirectionRight -> vm.run("caret.right", extend)
                    e.key == Key.DirectionUp -> vm.run("caret.up", extend)
                    e.key == Key.DirectionDown -> vm.run("caret.down", extend)
                    e.key == Key.MoveHome -> vm.run("caret.home", extend)
                    e.key == Key.MoveEnd -> vm.run("caret.end", extend)
                    e.key == Key.PageUp -> vm.run("caret.pageUp", extend)
                    e.key == Key.PageDown -> vm.run("caret.pageDown", extend)
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    )
}
