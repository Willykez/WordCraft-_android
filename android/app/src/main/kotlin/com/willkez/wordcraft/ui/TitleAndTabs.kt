package com.willkez.wordcraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willkez.wordcraft.editor.EditorUiState
import com.willkez.wordcraft.editor.EditorViewModel

/** Word's title bar: close, Quick Access Toolbar, document name, Tell Me, panels, ribbon toggle. */
@Composable
fun TitleBar(ui: EditorUiState, vm: EditorViewModel, d: Dispatcher, actions: EditorActions) {
    val st = ui.state
    var panelsMenu by remember { mutableStateOf(false) }
    Surface(color = Color.Transparent, contentColor = Color.White) {
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(WordBlue, WordBlueDark)))
                .statusBarsPadding().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(actions::close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close document") }
            IconButton(actions::save, Modifier.size(40.dp)) { Icon(Icons.Default.Save, "Save", Modifier.size(22.dp)) }
            IconButton(vm::undo, Modifier.size(40.dp), enabled = st?.canUndo == true) { Icon(Icons.Default.Undo, "Undo", Modifier.size(22.dp)) }
            IconButton(vm::redo, Modifier.size(40.dp), enabled = st?.canRedo == true) { Icon(Icons.Default.Redo, "Redo", Modifier.size(22.dp)) }
            Text(
                (st?.fileName ?: "Document") + if (st?.dirty == true) " •" else "",
                Modifier.weight(1f).padding(horizontal = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp, fontWeight = FontWeight.Medium,
            )
            IconButton({ vm.openPanel("search") }, Modifier.size(40.dp)) { Icon(Icons.Default.Search, "Tell me what you want to do", Modifier.size(22.dp)) }
            Box {
                IconButton({ panelsMenu = true }, Modifier.size(40.dp)) { Icon(Icons.Default.Dashboard, "Panels", Modifier.size(22.dp)) }
                DropdownMenu(panelsMenu, { panelsMenu = false }) {
                    PANELS.filter { it.first != "form" }.forEach { (id, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            leadingIcon = { if (id in ui.panels) Icon(Icons.Default.Check, null) },
                            onClick = { panelsMenu = false; vm.togglePanel(id) },
                        )
                    }
                }
            }
            IconButton(vm::toggleRibbon, Modifier.size(40.dp)) {
                Icon(if (ui.ribbonExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Collapse or expand the ribbon", Modifier.size(24.dp))
            }
        }
    }
}

val PANELS = listOf(
    "find" to "Find & Replace", "nav" to "Navigation", "styles" to "Styles", "borders" to "Borders and shading", "tableFormat" to "Format table", "pageBorders" to "Page borders", "toc" to "Table of contents", "format" to "Font",
    "review" to "Review", "comments" to "Comments", "clipboard" to "Clipboard", "info" to "Document info", "zoom" to "Zoom", "search" to "Tell me",
)

/** File + ribbon tabs; contextual tabs (table, picture, …) join in orange while they apply. */
@Composable
fun RibbonTabs(ui: EditorUiState, tab: String, contextTabs: List<String>, vm: EditorViewModel) {
    val model = ui.ribbon
    val tabs = model?.tabs ?: listOf("Home")
    // One plain strip like the desktop app: text tabs, the current one underlined. No chips.
    Column(Modifier.fillMaxWidth().background(RibbonBg)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            TabLabel("File", selected = false, color = WordBlue, bold = true) { vm.showBackstage(true) }
            tabs.forEach { t -> TabLabel(t, selected = tab == t && ui.ribbonExpanded, color = tabColor(t)) { vm.setTab(t) } }
            contextTabs.forEach { t -> TabLabel(t, selected = tab == t && ui.ribbonExpanded, color = tabColor(t), contextual = true) { vm.setTab(t) } }
        }
        HorizontalDivider(color = RibbonLine)
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean, color: Color, bold: Boolean = false, contextual: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.height(36.dp).clickable(onClick = onClick).padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
        Text(
            text, fontSize = 13.sp, maxLines = 1,
            color = when { selected -> color; contextual -> color; bold -> color; else -> RibbonInk },
            fontWeight = if (selected || bold) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (selected) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(color))
    }
}
