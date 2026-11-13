package org.wordcraft.android.editor

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

private const val SENTINEL = "\u200B"

/** Zero-width padding before the cursor: Backspace can eat these without the field ever being emptied. */
private const val PAD_LEN = 160
private const val MAX_LEN = 1600
private val PAD = SENTINEL.repeat(PAD_LEN)

private fun fresh() = TextFieldValue(PAD, TextRange(PAD_LEN))

/**
 * An invisible text field that owns the soft keyboard. The engine, not Android, owns the document,
 * so every change to the field is turned into engine commands by comparing it with its previous
 * text: removed characters are Backspaces, new characters are typing.
 *
 * The field is deliberately NOT reset after each keystroke. Resetting it restarts the keyboard's
 * input session, which cancels Caps Lock and the shift state (the first letter came out upper-case,
 * the rest lower-case). It is only reset when the document caret is moved by touch (`resetTick`),
 * or when the buffer is nearly used up.
 */
@Composable
fun TypingSink(vm: EditorViewModel, focus: FocusRequester, resetTick: Int = 0, onShortcut: (String) -> Unit) {
    var value by remember { mutableStateOf(fresh()) }
    LaunchedEffect(resetTick) { value = fresh() }
    BasicTextField(
        value = value,
        onValueChange = { nv ->
            val old = value.text
            val new = nv.text
            // Common prefix and suffix; what lies between them was deleted (old) and inserted (new).
            var p = 0
            val maxP = minOf(old.length, new.length)
            while (p < maxP && old[p] == new[p]) p++
            var q = 0
            val maxQ = minOf(old.length, new.length) - p
            while (q < maxQ && old[old.length - 1 - q] == new[new.length - 1 - q]) q++
            val deleted = old.length - p - q
            val inserted = new.substring(p, new.length - q).replace(SENTINEL, "")
            repeat(deleted) { vm.backspace() }
            if (inserted.isNotEmpty()) vm.typeText(inserted)
            // Keep the field as the keyboard left it; start over only when the padding has run out or it grew large.
            val padLeft = new.takeWhile { it.toString() == SENTINEL }.length
            value = if (new.length > MAX_LEN || padLeft < 4) fresh() else nv
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
