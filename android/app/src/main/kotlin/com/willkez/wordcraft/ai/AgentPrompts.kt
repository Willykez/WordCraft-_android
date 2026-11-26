package com.willkez.wordcraft.ai

import com.willkez.wordcraft.ui.Cmd
import org.json.JSONArray
import org.json.JSONObject

data class AgentStep(val command: String, val params: JSONObject)
data class AgentPlan(val summary: String, val steps: List<AgentStep>)
data class AgentRunResult(val applied: Int, val errors: List<String>)

/** Prompts and parsing for the assistant. The model never edits the document itself: it names engine commands. */
object AgentPrompts {

    /** Commands the model may not use: they leave the document, change files or are not edits. */
    val BLOCKED_PREFIXES = listOf("file.", "window.", "addins.")

    fun planSystem(commands: List<Cmd>): String = buildString {
        appendLine("You operate a word processor by choosing commands from the list below. Reply with JSON only, in this shape:")
        appendLine("""{"summary": "one short sentence on what you will do", "steps": [{"command": "<id>", "params": { ... }}]}""")
        appendLine("Rules:")
        appendLine("- Use only command ids from the list, and give params exactly as the command's parameter text describes. Leave params out when a command takes none.")
        appendLine("- Steps run in order. Formatting commands act on the user's current selection (the <selection> block, when present), or on the caret's paragraph or word when nothing is selected.")
        appendLine("- To format a different part of the document, first select it with a selection command, then format.")
        appendLine("- Do not explain outside the JSON. If the request cannot be done with these commands, return {\"summary\": \"<why>\", \"steps\": []}.")
        appendLine("- Never invent parameter names. Prefer few, precise steps.")
        appendLine()
        appendLine("Commands (id | name | parameters):")
        commands.forEach { c -> appendLine("${c.id} | ${c.label} | ${c.params.replace('\n', ' ').take(220).ifBlank { "none" }}") }
    }

    const val WRITE_SYSTEM = "You are a writing assistant inside a word processor. Output only the text to insert at the caret: " +
        "plain text, no Markdown, no preamble, no quotation marks around it, no closing remarks. Separate paragraphs with a blank line. " +
        "Match the language and tone of the document."

    fun planUser(instruction: String, documentText: String, selection: String, pages: Int, words: Long): String = buildString {
        appendLine("<document_info>pages: $pages, words: $words</document_info>")
        appendLine("<document_context>")
        appendLine(documentText.ifBlank { "(the document is empty)" })
        appendLine("</document_context>")
        if (selection.isNotBlank()) {
            appendLine("<selection>")
            appendLine(selection)
            appendLine("</selection>")
        }
        appendLine("<user_instruction>")
        appendLine(instruction.trim())
        appendLine("</user_instruction>")
    }

    /** Lenient: strips code fences, keeps only known commands, drops steps with unusable params. */
    fun parsePlan(text: String, allowed: Set<String>): AgentPlan {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = runCatching { JSONObject(cleaned) }.getOrNull() ?: return AgentPlan("The assistant's answer could not be read.", emptyList())
        val arr = root.optJSONArray("steps") ?: JSONArray()
        val steps = ArrayList<AgentStep>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("command")
            if (id !in allowed) continue
            steps += AgentStep(id, o.optJSONObject("params") ?: JSONObject())
        }
        return AgentPlan(root.optString("summary").ifBlank { "Apply ${steps.size} change(s)" }, steps)
    }
}
