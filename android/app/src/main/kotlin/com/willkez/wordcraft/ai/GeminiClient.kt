package com.willkez.wordcraft.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AiException(val code: Int, message: String) : Exception(message)

/**
 * Minimal Gemini REST client: one JSON answer (`generateContent`) or a live stream of text (`streamGenerateContent`,
 * server-sent events). The key is the user's own; it is sent in a header, never in the URL. Rate limits (429) and
 * server hiccups (5xx) are retried with exponential backoff before giving up.
 */
class GeminiClient(private val apiKey: String, private val model: String) {

    private fun open(method: String): HttpURLConnection {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:$method")
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
    }

    private fun body(system: String, user: String, json: Boolean): String = JSONObject()
        .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
        .put("generationConfig", JSONObject().put("temperature", 0.2).apply { if (json) put("responseMimeType", "application/json") })
        .toString()

    private fun failure(conn: HttpURLConnection): AiException {
        val code = conn.responseCode
        val text = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
        val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrDefault(text.take(200))
        return AiException(code, when (code) {
            400 -> "The request was rejected: $msg"
            401, 403 -> "The API key was not accepted. Check it in the AI settings."
            404 -> "That model name was not found. Check the model in the AI settings."
            429 -> "Too many requests; try again in a moment."
            else -> msg.ifBlank { "The AI service returned an error ($code)." }
        })
    }

    private fun textOf(candidatesJson: JSONObject): String {
        val parts = candidatesJson.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        return buildString { for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text")) }
    }

    /** One complete answer, as JSON text. */
    suspend fun generateJson(system: String, user: String): String = withContext(Dispatchers.IO) {
        var lastError: AiException? = null
        for (attempt in 0..3) {
            currentCoroutineContext().ensureActive()
            val conn = open("generateContent")
            try {
                conn.outputStream.use { it.write(body(system, user, json = true).toByteArray()) }
                if (conn.responseCode == 200) return@withContext textOf(JSONObject(conn.inputStream.bufferedReader().readText()))
                val err = failure(conn)
                if (err.code != 429 && err.code < 500) throw err
                lastError = err
            } finally { conn.disconnect() }
            delay(1000L shl (attempt + 1))
        }
        throw lastError ?: AiException(0, "The AI service did not answer.")
    }

    /** Streams the answer; `onText` gets each fragment as it arrives. Cancelling the coroutine closes the connection. */
    suspend fun stream(system: String, user: String, onText: (String) -> Unit) = withContext(Dispatchers.IO) {
        var lastError: AiException? = null
        for (attempt in 0..3) {
            currentCoroutineContext().ensureActive()
            val conn = open("streamGenerateContent?alt=sse")
            try {
                conn.outputStream.use { it.write(body(system, user, json = false).toByteArray()) }
                if (conn.responseCode != 200) {
                    val err = failure(conn)
                    if (err.code != 429 && err.code < 500) throw err
                    lastError = err
                } else {
                    BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val line = reader.readLine() ?: break
                            if (!line.startsWith("data:")) continue
                            val payload = line.removePrefix("data:").trim()
                            if (payload.isEmpty() || payload == "[DONE]") continue
                            val chunk = runCatching { textOf(JSONObject(payload)) }.getOrDefault("")
                            if (chunk.isNotEmpty()) onText(chunk)
                        }
                    }
                    return@withContext
                }
            } finally { conn.disconnect() }
            delay(1000L shl (attempt + 1))
        }
        throw lastError ?: AiException(0, "The AI service did not answer.")
    }
}
