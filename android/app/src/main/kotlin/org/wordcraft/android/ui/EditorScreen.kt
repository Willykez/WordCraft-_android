package org.wordcraft.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import org.wordcraft.android.editor.EditorViewModel
import org.wordcraft.android.editor.PageCanvas
import org.wordcraft.android.editor.TypingSink

/**
 * Word-style editor: title bar with Quick Access, tabs, a collapsible ribbon, the pages with floating
 * panels over them, a status bar and a bottom quick-action bar.
 */
@Composable
fun EditorScreen(vm: EditorViewModel, actions: EditorActions) {
    val ui by vm.ui.collectAsState()
    val d = remember(vm, actions) { Dispatcher(vm, actions) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var keyboardShown by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val st = ui.state

    val model = ui.ribbon
    val contextTabs = model?.contextTabs?.filter { t -> model.tabCmds(t).any { it.id in ui.enabled } }.orEmpty()
    val tab = if (ui.tab in (model?.tabs.orEmpty() + contextTabs)) ui.tab else "Home"

    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() } }
    BackHandler(enabled = ui.backstage) { vm.showBackstage(false) }

    Box(Modifier.fillMaxSize().background(RibbonBg)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            TitleBar(ui, vm, d, actions)
            RibbonTabs(ui, tab, contextTabs, vm)
            AnimatedVisibility(ui.ribbonExpanded) { RibbonBody(ui, tab, d) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PageCanvas(
                    vm = vm, pages = ui.pages, caret = ui.caret, selection = ui.selection,
                    revision = st?.revision ?: 0uL, zoom = ui.zoom, onZoom = vm::setZoom, marks = st?.showMarks == true, obj = ui.obj,
                    onTapped = { runCatching { focus.requestFocus() }; keyboard?.show(); keyboardShown = true },
                )
                TypingSink(vm, focus) { s ->
                    when (s) {
                        "copy" -> actions.copy(false); "cut" -> actions.copy(true); "paste" -> actions.paste()
                        "save" -> actions.save(); "find" -> vm.openPanel("find")
                    }
                }
                // Context bars: a picture/shape, text, and the table under the caret each get their own tools.
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = 8.dp, start = 8.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (ui.obj != null) ObjectBar(ui, d, vm)
                    else {
                        if (st?.hasSelection == true) MiniToolbar(ui, d)
                        if (st?.inTable == true) TableBar(ui, d)
                    }
                }
                PanelHost(ui, vm, d, Modifier.fillMaxSize())
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
            StatusBar(ui, vm)
            QuickBar(ui, d, vm, keyboardShown) {
                if (keyboardShown) { keyboard?.hide(); keyboardShown = false }
                else { runCatching { focus.requestFocus() }; keyboard?.show(); keyboardShown = true }
            }
        }

        if (ui.backstage) Backstage(ui, vm, d, actions)
    }

    if (ui.closePrompt) {
        AlertDialog(
            onDismissRequest = vm::cancelClose,
            title = { Text("Save changes?") },
            text = { Text("\"${st?.fileName ?: "This document"}\" has changes that aren't saved.") },
            confirmButton = { TextButton({ vm.saveThenClose { actions.saveCopy("docx") } }) { Text("Save") } },
            dismissButton = {
                Row {
                    TextButton(vm::discardAndClose) { Text("Don't save") }
                    TextButton(vm::cancelClose) { Text("Cancel") }
                }
            },
        )
    }
    ui.dialogCmd?.let { c -> ParamDialog(c, onDismiss = vm::dismissDialog, onSubmit = { json -> vm.submit(c, json) }) }
    ui.result?.let { (title, text) -> ResultDialog(title, text, vm::dismissResult) }
}
