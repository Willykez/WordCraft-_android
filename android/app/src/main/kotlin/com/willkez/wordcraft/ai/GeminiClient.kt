package com.willkez.wordcraft.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One piece of a streamed answer. [finish] is set on the last piece ("STOP", "MAX_TOKENS", "SAFETY", …). */
data class Chunk(val text: String, val finish: String? = null)

class AiException(val code: Int, message: String) : IOException(message)

/** Anything that can stream an answer: Gemini, Claude, OpenAI or any OpenAI-compatible server. */
interface AiProvider {
    fun stream(system: String, user: String, json: Boolean, maxOutputTokens: Int = 8192, temperature: Double = 0.4): Flow<Chunk>
}

/**
 * Streams an answer over server-sent events. The provider subclasses only say where to POST, what the body is and
 * how one event becomes text; the connection handling is shared:
 *
 *  - Cancelling the collecting coroutine closes the socket at once, which is how Stop ends a generation.
 *  - A rate limit (429) or a server hiccup (5xx) before the first token is retried with exponential backoff, and a
 *    provider may then switch to a backup model ([switchModel]). Once tokens have arrived a failure is reported
 *    instead, so the caller keeps the partial text and can offer "Continue".
 *  - A 400 caused by an option this particular model refuses (a thinking level, a sampling setting…) can be repaired
 *    by the provider ([repair]) and the request sent again straight away.
 */
abstract class SseClient : AiProvider {
    protected abstract fun url(): String
    protected abstract fun headers(): Map<String, String>
    protected abstract fun body(system: String, user: String, json: Boolean, maxOutputTokens: Int, temperature: Double): String
    /** One server-sent event as text. Return null to skip it; throw [AiException] for an error event. */
    protected abstract fun parse(data: JSONObject): Chunk?
    protected open fun friendly(code: Int, body: String): String {
        val msg = errorMessage(body)
        return when (code) {
            401, 403 -> "The API key was not accepted, or this model is not available to it."
            404 -> "That model (or address) was not found. Check it in AI settings."
            429 -> "Rate limit or quota reached. Wait a moment and try again."
            in 500..599 -> "The AI service is busy right now. Try again shortly."
            else -> msg.ifEmpty { "Request failed ($code)." }
        }
    }
    protected open fun repair(message: String): Boolean = false
    protected open fun switchModel(): Boolean = false

    protected fun errorMessage(body: String): String = runCatching {
        val o = JSONObject(body)
        val e = o.opt("error")
        when (e) { is JSONObject -> e.optString("message"); is String -> e; else -> o.optString("message") }
    }.getOrDefault("")

    override fun stream(system: String, user: String, json: Boolean, maxOutputTokens: Int, temperature: Double): Flow<Chunk> = flow {
        var attempt = 0
        var repaired = 0
        var delivered = false
        while (true) {
            try {
                request(system, user, json, maxOutputTokens, temperature) { chunk -> delivered = true; emit(chunk) }
                return@flow
            } catch (e: AiException) {
                if (e.code == 400 && !delivered && repaired < 3 && repair(e.message.orEmpty())) { repaired++; continue }
                val retryable = (e.code == 429 || e.code in 500..599) && !delivered
                if (!retryable) throw e
                if (attempt >= MAX_RETRIES) {
                    if (e.code == 429 && switchModel()) { attempt = 0; continue }
                    throw e
                }
            } catch (e: IOException) {
                if (delivered || attempt >= MAX_RETRIES) throw e
            }
            delay(BASE_DELAY_MS shl attempt)
            attempt++
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun request(
        system: String, user: String, json: Boolean, maxOutputTokens: Int, temperature: Double,
        emit: suspend (Chunk) -> Unit,
    ) {
        val payload = body(system, user, json, maxOutputTokens, temperature)
        val conn = (URL(url()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            headers().forEach { (k, v) -> setRequestProperty(k, v) }
        }
        // A blocked read cannot be interrupted, so closing the socket is how a cancelled collector stops waiting.
        coroutineScope {
            val watcher = launch { try { awaitCancellation() } finally { runCatching { conn.disconnect() } } }
            try {
                conn.outputStream.use { it.write(payload.toByteArray()) }
                val code = conn.responseCode
                if (code != 200) {
                    val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
                    throw AiException(code, friendly(code, err))
                }
                var finished = false
                conn.inputStream.bufferedReader().use { r ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val line = r.readLine() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isEmpty()) continue
                        if (data == "[DONE]") { finished = true; continue }
                        val obj = runCatching { JSONObject(data) }.getOrNull() ?: continue
                        val chunk = parse(obj) ?: continue
                        if (chunk.finish != null) finished = true
                        if (chunk.text.isNotEmpty() || chunk.finish != null) emit(chunk)
                    }
                }
                if (!finished) throw IOException("The connection closed before the answer finished.")
            } finally {
                watcher.cancel()
                runCatching { conn.disconnect() }
            }
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
        const val BASE_DELAY_MS = 1000L
    }
}

/**
 * Google Gemini (`streamGenerateContent`). On the free tier a rate limit switches to a backup model once, since each
 * model has its own quota. Gemini 4 models think before answering: that is turned down for writing (first words
 * sooner) and kept moderate for plans; a model that refuses the setting is simply asked again without it.
 */
class GeminiClient(private val apiKey: String, private var model: String, private val fallbackModel: String? = null) : SseClient() {
    private var thinking = model.startsWith("gemini-4")
    private var fellBack = false

    override fun url() = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse"
    override fun headers() = mapOf("x-goog-api-key" to apiKey)

    override fun body(system: String, user: String, json: Boolean, maxOutputTokens: Int, temperature: Double): String = JSONObject().apply {
        put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
        put(
            "generationConfig",
            JSONObject().put("temperature", temperature).put("maxOutputTokens", maxOutputTokens).apply {
                if (json) put("responseMimeType", "application/json")
                if (thinking) put("thinkingConfig", JSONObject().put("thinkingLevel", if (json) "MEDIUM" else "LOW"))
            },
        )
    }.toString()

    override fun parse(data: JSONObject): Chunk? {
        data.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotEmpty() }?.let {
            throw AiException(400, "The request was blocked ($it). Try rephrasing.")
        }
        val cand = data.optJSONArray("candidates")?.optJSONObject(0) ?: return null
        val parts = cand.optJSONObject("content")?.optJSONArray("parts")
        val text = buildString {
            for (i in 0 until (parts?.length() ?: 0)) {
                val p = parts!!.getJSONObject(i)
                if (!p.optBoolean("thought", false)) append(p.optString("text"))
            }
        }
        return Chunk(text, cand.optString("finishReason").takeIf { it.isNotEmpty() })
    }

    override fun friendly(code: Int, body: String): String {
        val msg = errorMessage(body)
        return when (code) {
            400 -> if (msg.contains("API key", true)) "The API key was not accepted. Check it in AI settings." else msg.ifEmpty { "The request was rejected." }
            else -> super.friendly(code, body)
        }
    }

    override fun repair(message: String): Boolean {
        if (thinking && message.contains("thinking", true)) { thinking = false; return true }
        return false
    }

    override fun switchModel(): Boolean {
        val next = fallbackModel?.takeIf { !fellBack && it != model } ?: return false
        fellBack = true
        model = next
        thinking = next.startsWith("gemini-4")
        return true
    }
}

/**
 * Any server that speaks the OpenAI chat-completions protocol: OpenAI itself, OpenRouter, Groq, DeepSeek, Mistral,
 * Together, and local servers such as Ollama or LM Studio. Newer OpenAI models want `max_completion_tokens` and no
 * sampling settings; other servers want `max_tokens` and accept them. Where a server refuses an option, the request is
 * repeated without it.
 */
class OpenAiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    official: Boolean,
    private var jsonMode: Boolean,
) : SseClient() {
    private var completionTokens = official
    private var sampling = !official

    override fun url() = baseUrl.trimEnd('/') + "/chat/completions"
    override fun headers() = if (apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $apiKey")

    override fun body(system: String, user: String, json: Boolean, maxOutputTokens: Int, temperature: Double): String = JSONObject().apply {
        put("model", model)
        put("stream", true)
        put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system)).put(JSONObject().put("role", "user").put("content", user)))
        put(if (completionTokens) "max_completion_tokens" else "max_tokens", maxOutputTokens)
        if (sampling) put("temperature", temperature)
        if (json && jsonMode) put("response_format", JSONObject().put("type", "json_object"))
    }.toString()

    override fun parse(data: JSONObject): Chunk? {
        data.optJSONObject("error")?.let { throw AiException(500, it.optString("message").ifEmpty { "The AI service reported an error." }) }
        val choice = data.optJSONArray("choices")?.optJSONObject(0) ?: return null
        val text = choice.optJSONObject("delta")?.optString("content").orEmpty().let { if (it == "null") "" else it }
        val finish = when (val f = choice.optString("finish_reason").takeIf { it.isNotEmpty() && it != "null" }) {
            null -> null
            "stop" -> "STOP"
            "length" -> "MAX_TOKENS"
            "content_filter" -> "SAFETY"
            else -> f.uppercase()
        }
        return Chunk(text, finish)
    }

    override fun repair(message: String): Boolean {
        val m = message.lowercase()
        return when {
            jsonMode && "response_format" in m -> { jsonMode = false; true }
            !completionTokens && "max_completion_tokens" in m -> { completionTokens = true; true }
            completionTokens && "max_tokens" in m && "max_completion_tokens" !in m -> { completionTokens = false; true }
            sampling && "temperature" in m -> { sampling = false; true }
            else -> false
        }
    }
}

/** Anthropic Claude (`/v1/messages`, streamed). */
class AnthropicClient(private val apiKey: String, private val model: String, private val baseUrl: String = "https://api.anthropic.com") : SseClient() {
    private var sampling = true

    override fun url() = baseUrl.trimEnd('/') + "/v1/messages"
    override fun headers() = mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01")

    override fun body(system: String, user: String, json: Boolean, maxOutputTokens: Int, temperature: Double): String = JSONObject().apply {
        put("model", model)
        put("max_tokens", maxOutputTokens)
        put("stream", true)
        put("system", if (json) "$system\n\nReply with a single JSON object and nothing else: no Markdown fences, no commentary." else system)
        put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
        if (sampling) put("temperature", temperature)
    }.toString()

    override fun parse(data: JSONObject): Chunk? = when (data.optString("type")) {
        "content_block_delta" -> data.optJSONObject("delta")?.takeIf { it.optString("type") == "text_delta" }?.optString("text")?.let { Chunk(it) }
        "message_delta" -> data.optJSONObject("delta")?.optString("stop_reason")?.takeIf { it.isNotEmpty() && it != "null" }?.let {
            Chunk("", when (it) { "max_tokens" -> "MAX_TOKENS"; "refusal" -> "SAFETY"; else -> "STOP" })
        }
        "error" -> throw AiException(500, data.optJSONObject("error")?.optString("message").orEmpty().ifEmpty { "The AI service reported an error." })
        else -> null
    }

    override fun repair(message: String): Boolean {
        if (sampling && message.contains("temperature", true)) { sampling = false; return true }
        return false
    }

    override fun friendly(code: Int, body: String): String =
        if (code == 529) "Claude is overloaded right now. Try again shortly." else super.friendly(code, body)
}
