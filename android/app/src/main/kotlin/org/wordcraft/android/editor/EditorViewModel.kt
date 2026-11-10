package org.wordcraft.android.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.wordcraft.android.WordcraftApp
import org.wordcraft.android.data.DocumentStore
import org.wordcraft.bridge.CaretRect
import org.wordcraft.bridge.EditorState
import org.wordcraft.bridge.MobileSession
import org.wordcraft.bridge.PageInfo
import org.wordcraft.bridge.SelRect
import java.nio.ByteBuffer
import java.util.concurrent.Executors

enum class ToolbarTab { HOME, INSERT, LAYOUT, REVIEW, REFERENCES, VIEW }

/** Everything the editor UI draws. The Rust engine owns the document; this is only a view of it. */
data class EditorUiState(
    val open: Boolean = false,
    val pages: List<PageInfo> = emptyList(),
    val state: EditorState? = null,
    val caret: CaretRect? = null,
    val selection: List<SelRect> = emptyList(),
    val tab: ToolbarTab = ToolbarTab.HOME,
    val toolbarExpanded: Boolean = true,
    val zoom: Float = 1f,
    val message: String? = null,
    val needsPassword: Boolean = false,
    val findOpen: Boolean = false,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val store = DocumentStore(app)
    val recents get() = store.recents()

    /** All engine calls that change state run on one thread, so typed characters keep their order. */
    private val engineThread: CoroutineDispatcher = Executors.newSingleThreadExecutor { Thread(it, "engine") }.asCoroutineDispatcher()

    private var session: MobileSession? = null
    private var sourceUri: Uri? = null
    private var sourceName: String = "Untitled.docx"

    private val _ui = MutableStateFlow(EditorUiState())
    val ui: StateFlow<EditorUiState> = _ui

    private val pageCache = object : LruCache<String, Bitmap>(6) {}

    // ---- documents ---------------------------------------------------------------------------

    fun newDocument(template: String = "blank") = launchEngine {
        WordcraftApp.fontsReady?.join()
        install(if (template == "blank") MobileSession.newBlank() else MobileSession.fromTemplate(template), null, "Untitled.docx")
    }

    private var pendingOpen: Pair<Uri, ByteArray>? = null
    val pendingPasswordUri: Uri? get() = pendingOpen?.first

    fun openDocument(uri: Uri, password: String? = null) = launchEngine {
        try {
            WordcraftApp.fontsReady?.join()
            val name = store.displayName(uri)
            val bytes = pendingOpen?.takeIf { it.first == uri }?.second ?: store.read(uri)
            val s = MobileSession.open(name, bytes, password)
            pendingOpen = null
            store.remember(uri, name)
            install(s, uri, name)
        } catch (e: org.wordcraft.bridge.BridgeException.PasswordRequired) {
            pendingOpen = uri to store.read(uri)
            _ui.update { it.copy(needsPassword = true) }
        } catch (e: Exception) {
            say("Could not open: ${e.message}")
        }
    }

    fun closeDocument() {
        saveIfDirty()
        session = null; sourceUri = null; pageCache.evictAll()
        _ui.value = EditorUiState()
    }

    private fun install(s: MobileSession, uri: Uri?, name: String) {
        session = s; sourceUri = uri; sourceName = name; pageCache.evictAll()
        _ui.update { EditorUiState(open = true, tab = it.tab, zoom = 1f) }
        refresh()
    }

    /** Save in place when the file came from a writable URI; otherwise the UI asks for a location. */
    fun save(onNeedsLocation: () -> Unit) {
        val uri = sourceUri ?: return onNeedsLocation()
        launchEngine {
            try {
                store.write(uri, session!!.save(sourceName)); refresh()
                say("Saved")
            } catch (e: Exception) { say("Save failed: ${e.message}") }
        }
    }

    /** Called when the activity stops, so leaving the app never loses typing. */
    fun saveIfDirty() {
        val s = session ?: return
        val uri = sourceUri ?: return
        runCatching { if (s.state().dirty) store.write(uri, s.save(sourceName)) }
    }

    fun saveAs(uri: Uri, asFormat: String? = null, copyOnly: Boolean = false) = launchEngine {
        try {
            val name = store.displayName(uri).let { n -> if (asFormat != null && !n.endsWith(".$asFormat", true)) "$n.$asFormat" else n }
            val s = session ?: return@launchEngine
            val bytes = if (copyOnly) s.export(name) else s.save(name)
            store.write(uri, bytes)
            if (!copyOnly) { sourceUri = uri; sourceName = name; store.remember(uri, name) }
            refresh(); say(if (copyOnly) "Exported $name" else "Saved $name")
        } catch (e: Exception) { say("Save failed: ${e.message}") }
    }

    fun suggestedName(ext: String) = sourceName.substringBeforeLast('.') + "." + ext

    // ---- commands ----------------------------------------------------------------------------

    /** Run an engine command; a disabled command or bad parameter becomes a short message, never a crash. */
    fun run(command: String, params: Map<String, Any?> = emptyMap(), then: ((String) -> Unit)? = null) = launchEngine {
        val s = session ?: return@launchEngine
        try {
            val out = s.execute(command, if (params.isEmpty()) "" else JSONObject(params).toString())
            refresh()
            then?.let { withContext(Dispatchers.Main) { it(out) } }
        } catch (e: Exception) { refresh(); say(e.message ?: "Command failed") }
    }

    fun typeText(text: String) {
        // Split on line breaks: each one is a real paragraph break (Enter), the rest is plain typing.
        text.split('\n').forEachIndexed { i, part ->
            if (i > 0) run("text.newParagraph")
            if (part.isNotEmpty()) run("text.insert", mapOf("text" to part))
        }
    }

    fun backspace(count: Int = 1) = repeat(count) { run("text.backspace") }
    fun undo() = launchEngine { session?.undo(); refresh() }
    fun redo() = launchEngine { session?.redo(); refresh() }

    fun copy(cut: Boolean, toClipboard: (String) -> Unit) =
        run(if (cut) "edit.cut" else "edit.copy") { _ -> launchEngine { session?.clipboardText()?.let { t -> withContext(Dispatchers.Main) { toClipboard(t) } } } }

    fun paste(systemText: String) = launchEngine {
        try { session?.paste(systemText); refresh() } catch (e: Exception) { say(e.message ?: "Paste failed") }
    }

    fun insertPicture(bytes: ByteArray) = launchEngine {
        try {
            // 4.5 inches wide is a comfortable default on A4/Letter with normal margins.
            session?.insertPicture(bytes, 324f, null); refresh()
        } catch (e: Exception) { say("Could not insert picture: ${e.message}") }
    }

    // ---- pointer input (page points) ---------------------------------------------------------

    fun tap(page: Int, x: Float, y: Float) = launchEngine { session?.tap(page.toUInt(), x, y, false); refresh() }
    fun selectWord(page: Int, x: Float, y: Float) = launchEngine { session?.selectWordAt(page.toUInt(), x, y); refresh() }
    fun selectParagraph(page: Int, x: Float, y: Float) = launchEngine { session?.selectParagraphAt(page.toUInt(), x, y); refresh() }
    fun selectBetween(ap: Int, ax: Float, ay: Float, bp: Int, bx: Float, by: Float) = launchEngine {
        runCatching { session?.selectBetween(ap.toUInt(), ax, ay, bp.toUInt(), bx, by) }
        refresh()
    }

    // ---- UI state ----------------------------------------------------------------------------

    fun setTab(t: ToolbarTab) = _ui.update { it.copy(tab = t, toolbarExpanded = if (it.tab == t) !it.toolbarExpanded else true) }
    fun setZoom(z: Float) = _ui.update { it.copy(zoom = z.coerceIn(0.5f, 4f)) }
    fun setFind(open: Boolean) = _ui.update { it.copy(findOpen = open) }
    fun dismissMessage() = _ui.update { it.copy(message = null) }
    fun dismissPassword() = _ui.update { it.copy(needsPassword = false) }
    fun say(msg: String) = _ui.update { it.copy(message = msg) }

    // ---- rendering ---------------------------------------------------------------------------

    /** Rasterise a page off the UI thread; results are cached per (revision, page, scale). */
    suspend fun render(page: Int, scale: Float): Bitmap? = withContext(Dispatchers.Default) {
        val s = session ?: return@withContext null
        val rev = _ui.value.state?.revision ?: 0uL
        val key = "$rev:$page:${(scale * 100).toInt()}"
        pageCache.get(key)?.let { return@withContext it }
        runCatching {
            val img = s.renderPage(page.toUInt(), scale)
            val bmp = Bitmap.createBitmap(img.width.toInt(), img.height.toInt(), Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(ByteBuffer.wrap(img.rgba))
            pageCache.put(key, bmp); bmp
        }.getOrNull()
    }

    // ---- internals ---------------------------------------------------------------------------

    private fun launchEngine(block: suspend () -> Unit) { viewModelScope.launch(engineThread) { block() } }

    private fun refresh() {
        val s = session ?: return
        val st = s.state()
        _ui.update { it.copy(pages = s.pages(), state = st, caret = s.caret(), selection = s.selectionRects()) }
    }
}
