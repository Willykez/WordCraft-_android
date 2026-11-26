package com.willkez.wordcraft.ui

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
import com.willkez.wordcraft.editor.EditorViewModel
import com.willkez.wordcraft.editor.PageCanvas
import com.willkez.wordcraft.editor.TypingSink

/**
 * Word-style editor: title bar with Quick Access, tabs, a collapsible ribbon, the pages with floating
 * panels over them, a status bar and a bottom quick-action bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(vm: EditorViewModel, actions: EditorActions) {
    val ui by vm.ui.collectAsState()
    val autoCap by vm.autoCapitalize.collectAsState()
    // On a short screen the keyboard leaves little room for the page: fold the ribbon away while it is open.
    val ribbonShown = ui.ribbonExpanded && !(WindowInsets.isImeVisible && androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 720)
    val d = remember(vm, actions) { Dispatcher(vm, actions) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var keyboardShown by remember { mutableStateOf(false) }
    // Bumped when the caret is moved by touch, so the keyboard's text buffer starts afresh there.
    var typingReset by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val st = ui.state

    val model = ui.ribbon
    val contextTabs = model?.contextTabs?.filter { t -> model.tabCmds(t).any { it.id in ui.enabled } }.orEmpty()
    val tab = if (ui.tab in (model?.tabs.orEmpty() + contextTabs)) ui.tab else "Home"

    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.dismissMessage() } }
    BackHandler(enabled = ui.backstage) { vm.showBackstage(false) }
    BackHandler(enabled = ui.drawTool != null && !ui.backstage) { vm.setDrawTool(null) }

    Box(Modifier.fillMaxSize().background(RibbonBg)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            TitleBar(ui, vm, d, actions)
            RibbonTabs(ui, tab, contextTabs, vm)
            AnimatedVisibility(ribbonShown) { RibbonBody(ui, tab, d) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PageCanvas(
                    vm = vm, pages = ui.pages, caret = ui.caret, selection = ui.selection,
                    revision = st?.revision ?: 0uL, zoom = ui.zoom, onZoom = vm::setZoom, marks = st?.showMarks == true, obj = ui.obj, tableGeos = ui.tableGeos, ruler = ui.ruler,
                    scrollToPage = ui.scrollToPage, scrollTick = ui.scrollTick,
                    draw = ui.drawTool?.let { com.willkez.wordcraft.editor.DrawSettings(it, ui.drawColor, ui.drawWidth, ui.stylusOnly) },
                    onTapped = { typingReset++; runCatching { focus.requestFocus() }; keyboard?.show(); keyboardShown = true },
                )
                TypingSink(vm, focus, typingReset, autoCap) { s ->
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
                    if (ui.drawTool != null) DrawBar(ui, vm)
                    else if (ui.obj != null) ObjectBar(ui, d, vm)
                    else {
                        ProofBar(ui, vm)
                        if (st?.hasSelection == true) MiniToolbar(ui, d)
                        if (st?.inTable == true) TableBar(ui, d)
                    }
                }
                PanelHost(ui, vm, d, Modifier.fillMaxSize())
                SheetHost(ui, vm, d)
                if (vm.ai.state.collectAsState().value.phase == com.willkez.wordcraft.ai.AiPhase.Writing) AiGlowEdge(Modifier.align(Alignment.BottomCenter))
                AiLiveBar(vm, Modifier.align(Alignment.BottomCenter))
                // While a header or footer is being edited, Word offers a way back to the body.
                if ("insert.closeHeader" in ui.enabled) {
                    FilledTonalButton(
                        { d.id("insert.closeHeader") }, Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = WordBlue, contentColor = androidx.compose.ui.graphics.Color.White),
                    ) { Text("Close header & footer") }
                }
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
    ui.result?.let { (title, text) -> ResultDialog(title, text, vm::dismissResult) }
}
