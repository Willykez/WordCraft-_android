package org.wordcraft.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import uniffi.wordcraft_android.*
import org.wordcraft.android.editor.EditorViewModel
import org.wordcraft.android.editor.PageCanvas
import org.wordcraft.android.editor.TypingSink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, actions: EditorActions) {
    val ui by vm.ui.collectAsState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    val st = ui.state

    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(actions::close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close") } },
                title = { Text((st?.fileName ?: "Document") + if (st?.dirty == true) " •" else "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(vm::undo, enabled = st?.canUndo == true) { Icon(Icons.Default.Undo, "Undo") }
                    IconButton(vm::redo, enabled = st?.canRedo == true) { Icon(Icons.Default.Redo, "Redo") }
                    IconButton(actions::save) { Icon(Icons.Default.Save, "Save") }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            listOf("Save as DOCX" to "docx", "Export as PDF" to "pdf", "Export as ODT" to "odt", "Export as plain text" to "txt", "Export as HTML" to "html")
                                .forEach { (n, f) -> DropdownMenuItem(text = { Text(n) }, onClick = { menu = false; actions.saveCopy(f) }) }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column {
                ContextualBar(ui, vm, actions)
                if (ui.findOpen) FindBar(vm)
                BottomToolbar(ui, vm, actions)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            PageCanvas(
                vm = vm, pages = ui.pages, caret = ui.caret, selection = ui.selection,
                revision = st?.revision ?: 0uL, zoom = ui.zoom, onZoom = vm::setZoom,
                onTapped = { runCatching { focus.requestFocus() }; keyboard?.show() },
            )
            TypingSink(vm, focus) { s ->
                when (s) {
                    "copy" -> actions.copy(false); "cut" -> actions.copy(true); "paste" -> actions.paste()
                    "save" -> actions.save(); "find" -> vm.setFind(true)
                }
            }
        }
    }
}
