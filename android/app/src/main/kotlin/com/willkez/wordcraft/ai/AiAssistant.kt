package com.willkez.wordcraft.ai

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Idle; Running/Review are the structured "list of changes" flow (fix the document, format headings) shown in the sheet;
 * Writing/Written are live writing on the page itself.
 */
enum class AiPhase { Idle, Running, Review, Writing, Written, Answering, Answered }

data class AiState(
    val phase: AiPhase = AiPhase.Idle,
    val streamed: String = "",
    val plan: AiPlan? = null,
    val error: String? = null,
    /** The answer stopped early (token limit or a dropped connection): offer "Continue". */
    val canContinue: Boolean = false,
    val title: String = "",
    /** Characters written to the page so far. */
    val written: Int = 0,
    /** Something the page could not take (a chart the engine refused, say); the rest was written anyway. */
    val warning: String? = null,
)

/** How live writing reaches the document: the view model runs these on the engine thread. */
interface LiveSink {
    /** Remember where the undo history stands, so the whole generation can be one Undo. */
    suspend fun begin(replaceSelection: Boolean, newParagraph: Boolean)
    /** Run engine commands. Returns a note about anything the engine refused (the rest still ran), or null. */
    suspend fun apply(commands: List<JSONObject>): String?
    suspend fun end()
}

/** Talks to Gemini for the AI features. Live writing goes straight onto the page; everything is one Undo. */
class AiAssistant(app: Application, private val scope: CoroutineScope) {
    private val prefs = app.getSharedPreferences("ai", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(AiState())
    val state: StateFlow<AiState> = _state

    private val _config = MutableStateFlow(loadConfig())
    /** The provider in use and its saved key, model and options. */
    val config: StateFlow<AiConfig> = _config

    /** What is saved for one provider (the settings screen shows it when you switch provider). */
    fun configFor(provider: String): AiConfig {
        val info = AiProviders.byId(provider)
        // The first version stored a single Gemini key and model; carry them over.
        val legacyKey = if (provider == "gemini") prefs.getString("key", "").orEmpty() else ""
        val legacyModel = if (provider == "gemini") prefs.getString("model", "").orEmpty() else ""
        val model = prefs.getString("p_${provider}_model", null) ?: legacyModel.ifBlank { info.defaultModel }
        return AiConfig(
            provider = provider,
            key = prefs.getString("p_${provider}_key", null) ?: legacyKey,
            model = AiProviders.migrate(provider, model),
            baseUrl = prefs.getString("p_${provider}_url", "").orEmpty(),
            autoFallback = prefs.getBoolean("fallback", true),
            shareDocument = prefs.getBoolean("share_document", true),
        )
    }

    private fun loadConfig(): AiConfig = configFor(prefs.getString("provider", "gemini").orEmpty().ifBlank { "gemini" })

    /** Whether a request can be made with the saved settings. */
    fun isReady(): Boolean = _config.value.ready

    private var job: Job? = null
    private var stopped = false
    private var produced = StringBuilder()
    private var resumeSystem = ""
    private var resumeUser: ((String) -> String)? = null

    fun saveConfig(c: AiConfig) {
        val info = c.info
        val clean = c.copy(key = c.key.trim(), model = c.model.trim().ifBlank { info.defaultModel }, baseUrl = c.baseUrl.trim())
        prefs.edit()
            .putString("provider", clean.provider)
            .putString("p_${clean.provider}_key", clean.key)
            .putString("p_${clean.provider}_model", clean.model)
            .putString("p_${clean.provider}_url", clean.baseUrl)
            .putBoolean("fallback", clean.autoFallback)
            .putBoolean("share_document", clean.shareDocument)
            .apply()
        _config.value = clean
    }

    /** The provider for the saved settings, or null when they are incomplete. */
    fun providerOrNull(): AiProvider? = _config.value.takeIf { it.ready }?.let(AiProviders::create)

    fun reset() { stop(); _state.value = AiState() }

    /** Stop button: ends the connection. Whatever was written stays on the page (Undo removes it). */
    fun stop() {
        stopped = true
        job?.cancel()
        job = null
        _state.update { if (it.phase == AiPhase.Running) AiState() else it }
    }

    private fun client(): AiProvider? {
        val c = _config.value
        if (!c.ready) { _state.value = AiState(error = "Set up an AI provider first (the gear icon): ${c.info.label} needs ${if (c.key.isBlank() && !c.info.keyOptional) "an API key" else "a model name and address"}."); return null }
        return AiProviders.create(c)
    }

    /** A quick action on the selection (or the end of the document for Continue writing). */
    fun runTask(task: TextTask, ctx: AiContext, sink: LiveSink) =
        live(task.label, AiPrompts.textSystem, { written -> if (written.isEmpty()) AiPrompts.textUser(task, "", ctx) else AiPrompts.resume(task, "", ctx, written) }, ctx, sink, replace = task.needsSelection && ctx.selection.isNotEmpty(), fresh = false)

    /** A free request. The answer may be text, lists, tables and charts, all written onto the page. */
    fun runAsk(instruction: String, ctx: AiContext, sink: LiveSink) =
        live("Writing…", AiPrompts.liveSystem, { written ->
            AiPrompts.liveUser(instruction, ctx) + if (written.isEmpty()) "" else "\n\n<already_written>\n$written\n</already_written>\nContinue exactly where <already_written> stops, without repeating anything."
        }, ctx, sink, replace = ctx.selection.isNotEmpty(), fresh = ctx.selection.isEmpty())

    /** Carry on after the answer stopped early. */
    fun continueLive(ctx: AiContext, sink: LiveSink) {
        val user = resumeUser ?: return
        start(_state.value.title, resumeSystem, user, ctx, sink, replaceSelection = false, fresh = false)
    }

    private fun live(title: String, system: String, user: (String) -> String, ctx: AiContext, sink: LiveSink, replace: Boolean, fresh: Boolean) {
        produced = StringBuilder()
        resumeSystem = system
        resumeUser = user
        start(title, system, user, ctx, sink, replaceSelection = replace, fresh = fresh)
    }

    private fun start(title: String, system: String, user: (String) -> String, ctx: AiContext, sink: LiveSink, replaceSelection: Boolean, fresh: Boolean) {
        val c = client() ?: return
        val prompt = user(produced.toString())
        stopped = false
        _state.value = AiState(phase = AiPhase.Writing, title = title, written = _state.value.written.takeIf { produced.isNotEmpty() } ?: 0)
        job?.cancel()
        job = scope.launch {
            val md = MdStream(fresh = fresh && produced.isEmpty())
            val buf = StringBuilder()
            var truncated = false
            var error: String? = null
            var last = System.currentTimeMillis()

            suspend fun push(commands: List<JSONObject>) {
                if (commands.isEmpty()) return
                sink.apply(commands)?.let { w -> _state.update { it.copy(warning = it.warning ?: w) } }
            }
            suspend fun flush() { if (buf.isNotEmpty()) { val t = buf.toString(); buf.setLength(0); push(md.feed(t)) }; last = System.currentTimeMillis() }

            try {
                sink.begin(replaceSelection, newParagraph = fresh && produced.isEmpty())
                c.stream(system, prompt, json = false).collect { chunk ->
                    produced.append(chunk.text)
                    buf.append(chunk.text)
                    _state.update { it.copy(written = it.written + chunk.text.length) }
                    if (chunk.finish != null && chunk.finish != "STOP") truncated = true
                    // A short pause between bursts keeps the page smooth: many tiny edits cost more than a few small ones.
                    if (System.currentTimeMillis() - last >= BURST_MS) flush()
                }
            } catch (e: CancellationException) {
                // Stop pressed: fall through to the clean-up below.
            } catch (e: Exception) {
                if (!stopped) error = e.message ?: "The request failed."
            } finally {
                withContext(NonCancellable) {
                    flush()
                    push(md.finish())
                    if (md.skipped > 0) _state.update { it.copy(warning = it.warning ?: "A chart could not be read, so it was left out.") }
                    sink.end()
                    val wrote = _state.value.written > 0
                    _state.update {
                        it.copy(
                            phase = if (wrote || error == null) AiPhase.Written else AiPhase.Idle,
                            error = error,
                            canContinue = wrote && (truncated || (error != null && !stopped)),
                        )
                    }
                }
            }
        }
    }

    // ---- the structured flow: a list of changes to review (fix the document, format headings) ----------------

    fun runStructured(instruction: String, ctx: AiContext) {
        val c = client() ?: return
        stopped = false
        _state.value = AiState(phase = AiPhase.Running, title = "Working…")
        job?.cancel()
        job = scope.launch {
            val raw = StringBuilder()
            try {
                c.stream(AiPrompts.structuredSystem, AiPrompts.structuredUser(instruction, ctx), json = true, maxOutputTokens = 16384, temperature = 0.3).collect { chunk ->
                    raw.append(chunk.text)
                    _state.update { it.copy(streamed = "${raw.length} characters received") }
                }
                val plan = AiPlans.parse(raw.toString(), ctx)
                _state.update { it.copy(phase = AiPhase.Review, plan = plan, streamed = "", title = plan.summary) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (stopped) return@launch
                _state.value = AiState(error = e.message ?: "The request failed.")
            }
        }
    }

    // ---- a question about the document: the answer appears in the sheet and the document is not touched --------

    fun runQuestion(question: String, ctx: AiContext) {
        val c = client() ?: return
        stopped = false
        _state.value = AiState(phase = AiPhase.Answering, title = "Reading the document…")
        job?.cancel()
        job = scope.launch {
            val answer = StringBuilder()
            var error: String? = null
            try {
                c.stream(AiPrompts.answerSystem, AiPrompts.answerUser(question, ctx), json = false, maxOutputTokens = 4096, temperature = 0.3).collect { chunk ->
                    answer.append(chunk.text)
                    _state.update { it.copy(streamed = answer.toString()) }
                }
            } catch (e: CancellationException) {
                // Stop pressed: keep what arrived.
            } catch (e: Exception) {
                if (!stopped) error = e.message ?: "The request failed."
            } finally {
                withContext(NonCancellable) {
                    _state.update { it.copy(phase = if (answer.isNotEmpty() || error == null) AiPhase.Answered else AiPhase.Idle, error = error, streamed = answer.toString()) }
                }
            }
        }
    }

    companion object {
        private const val BURST_MS = 90L
    }
}
