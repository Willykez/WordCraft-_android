package com.willkez.wordcraft.ai

/** The quick actions that stream plain text straight into the preview. */
enum class TextTask(val label: String, val instruction: String, val needsSelection: Boolean) {
    Rewrite("Rewrite", "Rewrite the selected text so it reads better. Keep the meaning and roughly the same length.", true),
    Fix("Fix grammar", "Fix spelling, grammar and punctuation in the selected text. Change nothing else.", true),
    Shorten("Shorten", "Make the selected text clearly shorter while keeping every key point.", true),
    Expand("Expand", "Expand the selected text with more detail and better flow, in the same voice.", true),
    Formal("Make formal", "Rewrite the selected text in a polished, professional tone.", true),
    Summarize("Summarize", "Summarize the selected text in a few sentences.", true),
    Simplify("Simplify", "Rewrite the selected text in plain, simple language that is easy to read. Keep the meaning.", true),
    Friendly("Friendlier", "Rewrite the selected text in a warm, friendly tone. Keep the meaning.", true),
    TranslateEn("To English", "Translate the selected text into English. Keep names, numbers and formatting intent.", true),
    TranslateAr("To Arabic", "Translate the selected text into Arabic. Keep names and numbers.", true),
    TranslateFr("To French", "Translate the selected text into French. Keep names and numbers.", true),
    TranslateEs("To Spanish", "Translate the selected text into Spanish. Keep names and numbers.", true),
    Continue("Continue writing", "Continue the document from where it ends or from the cursor, in the same voice and format. Write the next few paragraphs.", false),
}

object AiPrompts {

    /** The quick actions that work on a selection. */
    val selectionTasks: List<TextTask> get() = TextTask.values().filter { it.needsSelection }

    val answerSystem = """
        You answer questions about the user's document. Use only the document and the selection. If the answer is not there, say so plainly.
        Be concise and exact. Plain text only: no Markdown. Quote figures and names as they appear. Reply in the language of the question.
    """.trimIndent()

    fun answerUser(question: String, ctx: AiContext): String = buildString {
        append("<document_context>\n").append(clip(ctx.document, STRUCTURED_BUDGET).ifBlank { "(the document is empty or was not shared)" }).append("\n</document_context>\n\n")
        if (ctx.selection.isNotEmpty()) append("<selection>\n").append(ctx.selection).append("\n</selection>\n\n")
        append("<question>\n").append(question.trim()).append("\n</question>")
    }

    private const val TEXT_BUDGET = 12_000
    private const val STRUCTURED_BUDGET = 40_000

    val textSystem = """
        You are a writing assistant inside a word processor.
        Output ONLY the resulting text: no preamble, no explanation, no quotation marks around it, no Markdown, no code fences.
        Separate paragraphs with a blank line. Keep the language of the input unless the user asks for another one.
    """.trimIndent()

    val structuredSystem = """
        You are the editing engine of a word processor. The user asks for a change to their document.
        Reply with ONE JSON object and nothing else:
        {"summary": "<one short sentence saying what you did>", "ops": [ ... ]}

        Operations (use only these):
        {"op":"insert_text","text":"..."}   insert new paragraphs at the cursor
        {"op":"replace_selection","text":"..."}   replace the selected text (only when <selection> is present)
        {"op":"replace","find":"...","with":"..."}   change every exact occurrence of a phrase that already exists in the document
        {"op":"table","rows":[["Header","Header"],["cell","cell"]]}   insert a table at the cursor; the first row is the header; every row has the same length
        {"op":"chart","type":"column|bar|line|area|pie|doughnut|scatter","title":"...","categories":["A","B"],"series":[{"name":"S","values":[1,2]}]}
        {"op":"style","find":"<exact beginning of a paragraph>","style":"Title|Heading 1|Heading 2|Heading 3|Normal|Quote"}

        Rules:
        - Text is plain: no Markdown, no HTML. Separate paragraphs with \n.
        - "find" must be copied exactly from <document_context>. Keep it short (3 to 60 characters) but unique.
        - For spelling and grammar fixes use small replace operations on the wrong phrase. Never rewrite whole paragraphs for a fix.
        - For headings, use style operations on the paragraphs that are really titles or section headings.
        - At most one table or chart per answer. Chart values must be numbers. Use the document's own figures when the user refers to them; invent figures only when asked for example data.
        - Write in the document's language unless told otherwise.
        - If the request cannot be done with these operations, answer {"summary":"<why>","ops":[]}.
    """.trimIndent()

    private fun clip(text: String, max: Int) = if (text.length <= max) text else text.take(max) + "\n[…the document continues…]"

    /** For writing straight onto the page: a small Markdown subset that [MdStream] turns into real formatting. */
    val liveSystem = """
        You write directly into a word processor page. Output only the content to place in the document: no preamble, no closing remarks, and no code fence around the whole answer.
        The editor turns this small markup into real formatting. Use it only where it helps or where the user asks:
        # Heading, ## Heading, ### Heading   (headings; do not number them)
        > a quotation line
        **bold**   *italic*   __underline__   ~~strikethrough~~   ==highlight==   {c:C00000}coloured text{/c}  (a colour is a 6-digit hex or red, green, blue, orange, purple, gray, teal)
        - bullet items, one per line
        1. numbered items, one per line
        | a | b |   table rows, with a |---|---| separator row after the header row
        ::: callout   then lines   then :::     a bordered, shaded box (add green, red, yellow or gray after callout for another colour)
        ::: box   ...   :::     a bordered paragraph block
        ::: center   ...   :::   (also right, justify)     aligned paragraphs
        ::: pagebreak     starts a new page
        A chart is a fenced block:
        ```chart
        {"type":"column|bar|line|area|pie|doughnut|scatter","title":"...","categories":["A","B"],"series":[{"name":"S","values":[1,2]}]}
        ```
        Write normal text as plain paragraphs separated by blank lines. Do not nest marks inside each other. Use no other Markdown: no links, images, rules or HTML.
        Keep the language of the user's instruction or document. If text is selected, what you write replaces it.
    """.trimIndent()

    fun liveUser(instruction: String, ctx: AiContext): String = buildString {
        if (ctx.document.isNotBlank()) append("<document_context>\n").append(clip(ctx.document, STRUCTURED_BUDGET)).append("\n</document_context>\n\n")
        if (ctx.selection.isNotEmpty()) append("<selection>\n").append(ctx.selection).append("\n</selection>\n\n")
        append("<user_instruction>\n").append(instruction.trim()).append("\n</user_instruction>")
    }

    fun textUser(task: TextTask, extra: String, ctx: AiContext): String = buildString {
        if (ctx.document.isNotBlank()) append("<document_context>\n").append(clip(ctx.document, TEXT_BUDGET)).append("\n</document_context>\n\n")
        if (ctx.selection.isNotEmpty()) append("<selection>\n").append(ctx.selection).append("\n</selection>\n\n")
        append("<user_instruction>\n").append(task.instruction)
        if (extra.isNotBlank()) append(' ').append(extra.trim())
        append("\n</user_instruction>")
    }

    /** A request to carry on from text the assistant already wrote before the connection dropped. */
    fun resume(task: TextTask, extra: String, ctx: AiContext, written: String): String =
        textUser(task, extra, ctx) + "\n\n<already_written>\n" + written + "\n</already_written>\n" +
            "Continue exactly where <already_written> stops. Output only the remaining text, without repeating anything."

    fun structuredUser(instruction: String, ctx: AiContext): String = buildString {
        append("<document_context>\n").append(clip(ctx.document, STRUCTURED_BUDGET).ifBlank { "(empty document)" }).append("\n</document_context>\n\n")
        if (ctx.selection.isNotEmpty()) append("<selection>\n").append(ctx.selection).append("\n</selection>\n\n")
        else append("(There is no selection; the cursor is somewhere in the document.)\n\n")
        append("<user_instruction>\n").append(instruction.trim()).append("\n</user_instruction>")
    }
}
