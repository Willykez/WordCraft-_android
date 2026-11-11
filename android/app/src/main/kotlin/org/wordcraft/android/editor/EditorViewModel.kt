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
import org.json.JSONArray
import org.json.JSONObject
import org.wordcraft.android.WordcraftApp
import org.wordcraft.android.data.DocumentStore
import org.wordcraft.android.data.RecentDoc
import org.wordcraft.android.ui.Cmd
import org.wordcraft.android.ui.RibbonModel
import org.wordcraft.bridge.CaretRect
import org.wordcraft.bridge.EditorState
import org.wordcraft.bridge.MobileSession
import org.wordcraft.bridge.PageInfo
import org.wordcraft.bridge.SelRect
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/** Everything the editor UI draws. The Rust engine owns the document; this is only a view of it. */
data class EditorUiState(
    val open: Boolean = false,
    val pages: List<PageInfo> = emptyList(),
    val state: EditorState? = null,
    val caret: CaretRect? = null,
    val selection: List<SelRect> = emptyList(),
    val tab: String = "Home",
    val ribbonExpanded: Boolean = true,
    val zoom: Float = 1f,
    val message: String? = null,
    val needsPassword: Boolean = false,
    /** Every engine command, grouped like the Word ribbon. */
    val ribbon: RibbonModel? = null,
    /** Commands (among those the ribbon is showing) that can run at the caret right now. */
    val enabled: Set<String> = emptySet(),
    /** The commands `enabled` was computed for; anything else is assumed available. */
    val watched: Set<String> = emptySet(),
    /** Names of the quick styles in the gallery. */
    val styles: List<String> = emptyList(),
    /** Open floating panels, back to front. */
    val panels: List<String> = emptyList(),
    val backstage: Boolean = false,
    /** A command waiting for its inputs. */
    val dialogCmd: Cmd? = null,
    /** Title and text of a command result worth showing. */
    val result: Pair<String, String>? = null,
) {
    fun isEnabled(id: String) = id !in watched || id in enabled
}

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val store = DocumentStore(app)
    private val _recents = MutableStateFlow(store.recents())
    val recents: StateFlow<List<RecentDoc>> = _recents
    private fun reloadRecents() { _recents.value = store.recents() }
    fun pinRecent(uri: String) { store.togglePin(uri); reloadRecents() }
    fun forgetRecent(uri: String) { store.forget(uri); reloadRecents() }
    fun clearRecents() { store.clear(); reloadRecents() }

    /** All engine calls that change state run on one thread, so typed characters keep their order. */
    private val engineThread: CoroutineDispatcher = Executors.newSingleThreadExecutor { Thread(it, "engine") }.asCoroutineDispatcher()

    private var session: MobileSession? = null
    private var sourceUri: Uri? = null
    private var sourceName: String = "Untitled.docx"
    private var ribbon: RibbonModel? = null
    private var watched: List<String> = emptyList()

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
            store.remember(uri, name, "Opened"); reloadRecents()
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
        _ui.update { EditorUiState(open = true, tab = it.tab, ribbonExpanded = it.ribbonExpanded, zoom = 1f) }
        if (ribbon == null) ribbon = runCatching { RibbonModel.parse(s.commandsJson()) }.getOrNull()
        watched = ribbon?.watchedIds(_ui.value.tab).orEmpty()
        refresh()
        loadStyles()
    }

    /** Save in place when the file came from a writable URI; otherwise the UI asks for a location. */
    fun save(onNeedsLocation: () -> Unit) {
        val uri = sourceUri ?: return onNeedsLocation()
        launchEngine {
            try {
                store.write(uri, session!!.save(sourceName)); refresh()
                store.remember(uri, sourceName, "Saved"); reloadRecents()
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
            if (!copyOnly) sourceUri = uri.also { sourceName = name }
            store.remember(uri, name, if (copyOnly) "Exported" else "Saved"); reloadRecents()
            refresh(); say(if (copyOnly) "Exported $name" else "Saved $name")
        } catch (e: Exception) { say("Save failed: ${e.message}") }
    }

    fun suggestedName(ext: String) = sourceName.substringBeforeLast('.') + "." + ext

    // ---- commands ----------------------------------------------------------------------------

    /** Run an engine command; a disabled command or bad parameter becomes a short message, never a crash. */
    fun run(command: String, params: Map<String, Any?> = emptyMap(), then: ((String) -> Unit)? = null) =
        runJson(command, if (params.isEmpty()) "" else JSONObject(params).toString(), then = then)

    fun runJson(command: String, json: String, refreshAfter: Boolean = true, then: ((String) -> Unit)? = null) = launchEngine {
        val s = session ?: return@launchEngine
        try {
            val out = s.execute(command, json)
            if (refreshAfter) refresh()
            if (command.startsWith("styles.")) loadStyles()
            then?.let { withContext(Dispatchers.Main) { it(out) } }
        } catch (e: Exception) { refresh(); say(e.message ?: "Command failed") }
    }

    /** Read-only call for a panel: no refresh, result delivered on the main thread. */
    fun query(command: String, json: String = "", onResult: (String) -> Unit) = runJson(command, json, refreshAfter = false, then = onResult)

    fun clipboardText(onResult: (String) -> Unit) = launchEngine {
        val t = session?.clipboardText().orEmpty()
        withContext(Dispatchers.Main) { onResult(t) }
    }

    /** A ribbon command: ask for its inputs first when it has required ones. */
    fun exec(cmd: Cmd) {
        if (cmd.needsInput) { _ui.update { it.copy(dialogCmd = cmd) }; return }
        runJson(cmd.id, "") { out -> showResult(cmd, out) }
    }

    fun submit(cmd: Cmd, json: String) {
        dismissDialog()
        runJson(cmd.id, json) { out -> showResult(cmd, out) }
    }

    private fun showResult(cmd: Cmd, out: String) {
        val quiet = cmd.mutates || out.length < 3 || cmd.id.startsWith("view.") || cmd.id.startsWith("caret.") ||
            cmd.id.startsWith("select.") || cmd.id.endsWith("Pane")
        if (!quiet) _ui.update { it.copy(result = cmd.label to prettyJson(out)) }
    }

    private fun prettyJson(t: String): String = runCatching { JSONObject(t).toString(2) }.recoverCatching { JSONArray(t).toString(2) }.getOrDefault(t)

    private fun loadStyles() = launchEngine {
        val out = runCatching { session?.execute("styles.list", "") }.getOrNull() ?: return@launchEngine
        val names = runCatching {
            val a = JSONArray(out)
            (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optBoolean("quick") }.map { it.getString("name") }
        }.getOrDefault(emptyList())
        _ui.update { it.copy(styles = names) }
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

    /** Tapping the open tab again collapses the ribbon, as in Word. */
    fun setTab(t: String) {
        _ui.update { it.copy(tab = t, ribbonExpanded = if (it.tab == t) !it.ribbonExpanded else true) }
        watched = ribbon?.watchedIds(t).orEmpty()
        launchEngine { refresh() }
    }
    fun toggleRibbon() = _ui.update { it.copy(ribbonExpanded = !it.ribbonExpanded) }
    fun setZoom(z: Float) = _ui.update { it.copy(zoom = z.coerceIn(0.5f, 4f)) }
    fun openPanel(id: String) = _ui.update { it.copy(panels = it.panels.filter { p -> p != id } + id) }
    fun closePanel(id: String) = _ui.update { it.copy(panels = it.panels.filter { p -> p != id }) }
    fun togglePanel(id: String) = if (id in _ui.value.panels) closePanel(id) else openPanel(id)
    fun showBackstage(show: Boolean) = _ui.update { it.copy(backstage = show) }
    fun dismissDialog() = _ui.update { it.copy(dialogCmd = null) }
    fun dismissResult() = _ui.update { it.copy(result = null) }
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

    /** Every engine call goes through here: a failure becomes a message, never a crash. */
    private fun launchEngine(block: suspend () -> Unit) {
        viewModelScope.launch(engineThread) {
            try {
                block()
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Throwable) {
                runCatching { refresh() }
                say(e.message ?: "Something went wrong")
            }
        }
    }

    private fun refresh() {
        val s = session ?: return
        val st = s.state()
        val en = if (watched.isEmpty()) emptySet() else s.enabledAmong(watched).toSet()
        _ui.update { it.copy(pages = s.pages(), state = st, caret = s.caret(), selection = s.selectionRects(), ribbon = ribbon, enabled = en, watched = watched.toSet()) }
    }
}
