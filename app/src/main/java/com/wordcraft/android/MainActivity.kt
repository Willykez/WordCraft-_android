package com.wordcraft.android

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WordCraftApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordCraftApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val state = remember { EditorState() }
    var title by remember { mutableStateOf("Untitled document") }
    val tabs = remember { RibbonModel.load() }
    val allCommands = remember { RibbonModel.loadAll() }
    var selectedTab by remember { mutableStateOf("Home") }
    var pendingParams by remember { mutableStateOf<RibbonItem?>(null) }
    var showPalette by remember { mutableStateOf(false) }

    fun cmd(id: String, params: String = "{}", edits: Boolean = true) {
        scope.launch { state.command(id, params, edits) }
    }

    suspend fun readBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read the selected file")
    }

    suspend fun saveTo(uri: Uri, fileName: String) {
        val bytes = withContext(Dispatchers.IO) { NativeBridge.nativeSaveDocument(fileName) }
            ?: error("WordCraft could not export $fileName")
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not open the destination")
        }
        state.dirty = false
        state.status = "Saved $fileName"
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                val bytes = readBytes(uri)
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: (uri.lastPathSegment ?: "document.docx")
                state.status = withContext(Dispatchers.IO) { NativeBridge.nativeOpenDocument(name, bytes) }
                title = name
                state.dirty = false
                state.refresh()
            }.onFailure { state.status = "Open failed: ${it.message}" }
        }
    }

    val saveDocx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching { saveTo(uri, "${title.substringBeforeLast('.', title)}.docx") }
                .onFailure { state.status = "DOCX save failed: ${it.message}" }
        }
    }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching { saveTo(uri, "${title.substringBeforeLast('.', title)}.pdf") }
                .onFailure { state.status = "PDF export failed: ${it.message}" }
        }
    }

    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                val data = Base64.encodeToString(readBytes(uri), Base64.NO_WRAP)
                // Width in points; the engine scales the height and keeps the picture inline.
                val params = JSONObject().put("data", data).put("width", 300).toString()
                state.command("insert.picture", params)
            }.onFailure { state.status = "Picture insert failed: ${it.message}" }
        }
    }

    val density = LocalDensity.current
    var ribbonOpen by remember { mutableStateOf(true) }
    var showNav by remember { mutableStateOf(false) }
    var showStyles by remember { mutableStateOf(false) }
    var showFormat by remember { mutableStateOf(false) }

    // Platform-specific commands are handled here; everything else runs in the engine.
    fun invoke(item: RibbonItem) {
        val base = title.substringBeforeLast('.', title)
        when (item.id) {
            "file.open" -> openDocument.launch(arrayOf("*/*"))
            "file.save", "file.saveAs" -> saveDocx.launch("$base.docx")
            "file.exportPdf", "file.printPdf", "file.print" -> savePdf.launch("$base.pdf")
            "insert.picture" -> pickPicture.launch("image/*")
            "view.zoomIn" -> state.zoom = (state.zoom * 1.2f).coerceAtMost(2.5f)
            "view.zoomOut" -> state.zoom = (state.zoom / 1.2f).coerceAtLeast(0.6f)
            "view.pageWidth", "view.onePage", "view.multiplePages" -> state.zoom = 1f
            "view.navigationPane" -> showNav = !showNav
            "styles.pane" -> showStyles = !showStyles
            "file.new" -> {
                scope.launch {
                    if (state.command("file.new", edits = false)) {
                        title = "Untitled document"
                        state.dirty = false
                    }
                }
            }
            "edit.copy", "edit.cut" -> scope.launch {
                clipboard.setText(AnnotatedString(state.selectedText()))
                state.command(item.id, edits = item.id == "edit.cut")
            }
            "edit.paste" -> {
                val text = clipboard.getText()?.text.orEmpty()
                if (text.isNotEmpty()) scope.launch { state.typeText(text) } else cmd(item.id)
            }
            else -> if (RibbonModel.needsInput(item.params)) pendingParams = item else cmd(item.id)
        }
    }

    /** Run a command by id with ready-made params (quick bar, floating panels). */
    fun run(id: String, params: String) {
        val known = RibbonItem("", "", id, id, "{}", "")
        if (params == "{}") invoke(known) else cmd(id, params)
    }

    pendingParams?.let { item ->
        ParamDialog(
            item = item,
            onDismiss = { pendingParams = null },
            onRun = { json ->
                pendingParams = null
                cmd(item.id, json)
            }
        )
    }

    if (showPalette) {
        CommandPalette(
            all = allCommands,
            onDismiss = { showPalette = false },
            onPick = { item ->
                showPalette = false
                if (state.disabled.containsKey(item.id)) state.status = "${item.label}: ${state.disabled[item.id]}" else invoke(item)
            }
        )
    }

    Column(Modifier.fillMaxSize().background(ChromeGrey).statusBarsPadding().navigationBarsPadding().imePadding()) {
        WordTitleBar(
            title = title,
            saved = !state.dirty,
            onSave = { invoke(RibbonItem("", "", "Save", "file.save", "{}", "")) },
            onUndo = { cmd("edit.undo") },
            onRedo = { cmd("edit.redo") },
            onSearch = { showPalette = true },
            onMenuItem = { id -> invoke(RibbonItem("", "", id, id, "{}", "")) }
        )
        RibbonTabBar(
            tabs = tabs,
            selectedTab = selectedTab,
            expanded = ribbonOpen,
            onSelectTab = { name ->
                // Tapping the open tab again collapses the ribbon; tapping any tab while collapsed opens it.
                if (ribbonOpen && name == selectedTab) ribbonOpen = false else { selectedTab = name; ribbonOpen = true }
            },
            onToggle = { ribbonOpen = !ribbonOpen }
        )
        AnimatedVisibility(visible = ribbonOpen) {
            RibbonBody(state, tabs.firstOrNull { it.name == selectedTab } ?: tabs.firstOrNull(), onInvoke = { invoke(it) })
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val boundsW = with(density) { maxWidth.toPx() }
            val boundsH = with(density) { maxHeight.toPx() }
            DocumentCanvas(state, Modifier.fillMaxSize())

            if (showNav) {
                FloatingPanel("Navigation", boundsW, boundsH, Offset(boundsW * 0.04f, 24f), onClose = { showNav = false }, bodyHeight = 380.dp) {
                    NavigationPanelContent(state)
                }
            }
            if (showStyles) {
                FloatingPanel("Styles", boundsW, boundsH, Offset(boundsW * 0.30f, 90f), onClose = { showStyles = false }, bodyHeight = 330.dp) {
                    StylesPanelContent { id, params -> run(id, params) }
                }
            }
            if (showFormat) {
                FloatingPanel("Format", boundsW, boundsH, Offset(boundsW * 0.20f, 160f), onClose = { showFormat = false }, bodyHeight = 300.dp) {
                    FormatPanelContent { id, params -> run(id, params) }
                }
            }
        }

        if (state.status.isNotBlank() && !showNav) {
            Text(state.status, Modifier.fillMaxWidth().background(Color(0xFFFFF7E0)).padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 11.sp, color = Color(0xFF475467), maxLines = 3)
        }
        QuickBar(
            navOpen = showNav, stylesOpen = showStyles, formatOpen = showFormat,
            onToggleNav = { showNav = !showNav },
            onToggleStyles = { showStyles = !showStyles },
            onToggleFormat = { showFormat = !showFormat },
            onAction = { id, params -> run(id, params) }
        )
        Row(
            Modifier.fillMaxWidth().background(ChromeGrey).padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Page ${(state.focus?.page ?: state.visiblePage) + 1} of ${state.pages.size.coerceAtLeast(1)}", fontSize = 11.sp)
            Text("${(state.zoom * 100).toInt()}% · WordCraft 0.16.0", fontSize = 11.sp)
        }
    }
}
