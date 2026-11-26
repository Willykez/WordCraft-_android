package com.willkez.wordcraft.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns the assistant's text, which arrives in pieces, into engine commands that write it into the page as real formatting.
 *
 * Inline marks: `**bold**`, `*italic*`, `__underline__`, `~~strikethrough~~`, `==highlight==`, `{c:C00000}coloured{/c}`.
 * Line marks: `# ` `## ` `### ` headings, `> ` quote, `- ` bullets, `1. ` numbers, `| a | b |` tables.
 * Blocks: `::: callout [green|red|yellow|gray]` (border and shading), `::: box`, `::: center`, `::: right`, `::: justify`,
 * each closed by `:::`; `::: pagebreak`; a ```chart fence holding the chart as JSON.
 * Stray marks never reach the page.
 *
 * Text is emitted as soon as it is known, so a long paragraph appears word by word. Call [feed] with each piece and
 * [finish] at the end; each returns the commands to run next, in order. [skipped] counts blocks it could not use.
 */
class MdStream(fresh: Boolean = false) {
    private val out = mutableListOf<JSONObject>()
    private val text = StringBuilder()

    private var needBreak = false      // a paragraph has been written, so the next line starts a new one
    private var atLineStart = true
    private val prefix = StringBuilder()

    private var bold = false
    private var italic = false
    private var underline = false
    private var strike = false
    private var mark = false
    private var colored = false
    private var star = false           // one `*` seen, waiting to learn whether it is `**` or an italic mark
    private var us = false             // one `_` seen
    private var eq = false             // one `=` seen
    private var tilde = false          // one `~` seen
    private var brace: StringBuilder? = null

    private var list = 0               // list the current paragraph belongs to: 0 none, 1 bullets, 2 numbers
    private var styled = false         // the current paragraph carries a heading or quote style
    private var block = ""             // open block: callout, box, center, right, justify
    private var blockColor = ""
    private var resetBlock = false     // a block just closed: the next paragraph must not inherit its look

    private var skipToEol = false
    private var tableLine: StringBuilder? = null
    private val tableRows = mutableListOf<String>()
    private var fence: StringBuilder? = null

    var skipped = 0
        private set

    init {
        // Writing at the cursor: the paragraph there may be a heading, centred, boxed or in a list. Start from plain.
        if (fresh) { styled = true; list = UNKNOWN_LIST; resetBlock = true }
    }

    fun feed(piece: String): List<JSONObject> {
        for (c in piece) step(c)
        flushText()
        return take()
    }

    fun finish(): List<JSONObject> {
        if (atLineStart && prefix.isNotEmpty()) { if (prefix.startsWith(":::")) directive(prefix.toString()) else resolvePlain() }
        if (!atLineStart) endLine()
        tableLine?.let { addTableRow(it.toString()); tableLine = null }
        flushTable()
        fence?.let { f -> closeFence(f.toString()); fence = null }
        flushText()
        return take()
    }

    private fun take(): List<JSONObject> = out.toList().also { out.clear() }

    private fun cmd(id: String, params: JSONObject = JSONObject()) { out.add(JSONObject().put("cmd", id).put("params", params)) }

    private fun flushText() {
        if (text.isEmpty()) return
        cmd("text.insert", JSONObject().put("text", text.toString()).put("raw", true))
        text.setLength(0)
    }

    private fun emitPlain(t: String) {
        t.lines().filter { it.isNotBlank() }.forEach { line ->
            startParagraph(0, null)
            text.append(line)
            flushText()
            needBreak = true
        }
    }

    // ---- one character at a time ----------------------------------------------------------------

    private fun step(c: Char) {
        if (skipToEol) { if (c == '\n') skipToEol = false; return }

        fence?.let { f ->
            f.append(c)
            if (f.endsWith("\n```")) {
                fence = null
                closeFence(f.substring(0, f.length - 4))
                skipToEol = true
                atLineStart = true
            }
            return
        }

        tableLine?.let { tl ->
            if (c == '\n') { addTableRow(tl.toString()); tableLine = null; atLineStart = true } else tl.append(c)
            return
        }

        if (atLineStart) { lineStart(c); return }
        inline(c)
    }

    /** Characters inside a line: the marks that switch character formatting on and off. */
    private fun inline(c: Char) {
        brace?.let { b ->
            if (c == '}') { endBrace(b.substring(1)); brace = null; return }
            if (c != '\n' && b.length < 14) { b.append(c); return }
            text.append(b) // it was not a tag after all
            brace = null
        }
        if (star) {
            star = false
            if (c == '*') { toggleBold(); return }
            // A single `*` is an italic mark when it opens a word or closes an open italic; otherwise it is dropped.
            if (italic || !c.isWhitespace()) toggleItalic()
        }
        if (us) { us = false; if (c == '_') { toggleUnderline(); return }; text.append('_') }
        if (eq) { eq = false; if (c == '=') { toggleMark(); return }; text.append('=') }
        if (tilde) { tilde = false; if (c == '~') { toggleStrike(); return }; text.append('~') }
        when (c) {
            '*' -> star = true
            '_' -> us = true
            '=' -> eq = true
            '~' -> tilde = true
            '{' -> brace = StringBuilder("{")
            '\n' -> endLine()
            '`' -> {}
            else -> text.append(c)
        }
    }

    private fun toggleBold() { flushText(); bold = !bold; cmd("format.bold", JSONObject().put("value", bold)) }
    private fun toggleItalic() { flushText(); italic = !italic; cmd("format.italic", JSONObject().put("value", italic)) }
    private fun toggleUnderline() { flushText(); underline = !underline; cmd("format.underline", JSONObject().put("value", underline)) }
    private fun toggleStrike() { flushText(); strike = !strike; cmd("format.strikethrough", JSONObject().put("value", strike)) }
    private fun toggleMark() { flushText(); mark = !mark; cmd("format.highlight", JSONObject().put("color", if (mark) "yellow" else "none")) }

    private fun endBrace(tag: String) {
        val colour = tag.removePrefix("c:").takeIf { tag.startsWith("c:") }?.let { COLOURS[it.lowercase()] ?: it.removePrefix("#").takeIf { h -> h.length == 6 && h.all { x -> x.isLetterOrDigit() } } }
        when {
            colour != null -> { flushText(); colored = true; cmd("format.color", JSONObject().put("color", colour.uppercase())) }
            tag == "/c" -> { flushText(); colored = false; cmd("format.color", JSONObject().put("color", "auto")) }
            else -> text.append('{').append(tag).append('}') // not ours: keep it as written
        }
    }

    /** End of a line: marks never run on into the next paragraph. */
    private fun endLine() {
        if (star && italic) toggleItalic()
        star = false
        if (us) { text.append('_'); us = false }
        if (eq) { text.append('='); eq = false }
        if (tilde) { text.append('~'); tilde = false }
        brace?.let { text.append(it); brace = null }
        flushText()
        if (bold) { bold = false; cmd("format.bold", JSONObject().put("value", false)) }
        if (italic) { italic = false; cmd("format.italic", JSONObject().put("value", false)) }
        if (underline) { underline = false; cmd("format.underline", JSONObject().put("value", false)) }
        if (strike) { strike = false; cmd("format.strikethrough", JSONObject().put("value", false)) }
        if (mark) { mark = false; cmd("format.highlight", JSONObject().put("color", "none")) }
        if (colored) { colored = false; cmd("format.color", JSONObject().put("color", "auto")) }
        needBreak = true
        atLineStart = true
    }

    // ---- the first characters of a line decide what kind of line it is ---------------------------

    private fun lineStart(c: Char) {
        if (c == '\n') {
            val p = prefix.toString()
            prefix.setLength(0)
            when {
                p.startsWith(":::") -> directive(p)
                p == "---" || p.isEmpty() -> {} // a blank line or a rule: paragraph spacing does that job
                else -> { prefix.append(p); resolvePlain(); endLine() }
            }
            return
        }
        prefix.append(c)
        val p = prefix.toString()
        when {
            p == "|" -> { prefix.setLength(0); flushText(); tableLine = StringBuilder("|") }
            p.all { it == '#' } && p.length <= 6 -> {}
            p.dropLast(1).all { it == '#' } && p.length in 2..7 && c == ' ' -> { prefix.setLength(0); startParagraph(0, "Heading ${(p.length - 1).coerceIn(1, 3)}"); atLineStart = false }
            p.all { it == '`' } && p.length <= 3 -> if (p.length == 3) { prefix.setLength(0); fence = StringBuilder() }
            p == ">" -> {}
            p == "> " -> { prefix.setLength(0); startParagraph(0, "Quote"); atLineStart = false }
            p.startsWith(":::") || p.all { it == ':' } -> {}
            p == "-" || p == "+" || p == "*" -> {}
            p == "--" || p == "---" -> {}
            p == "- " || p == "+ " || p == "* " -> { prefix.setLength(0); startParagraph(1, null); atLineStart = false }
            p.all { it.isDigit() } -> {}
            p.length >= 2 && p.dropLast(1).all { it.isDigit() } && c == '.' -> {}
            p.length >= 3 && p.dropLast(2).all { it.isDigit() } && p[p.length - 2] == '.' && c == ' ' -> { prefix.setLength(0); startParagraph(2, null); atLineStart = false }
            else -> resolvePlain()
        }
    }

    /** The prefix turned out to be ordinary text: write it as the start of a normal line. */
    private fun resolvePlain() {
        val p = prefix.toString()
        prefix.setLength(0)
        startParagraph(0, null)
        atLineStart = false
        for (ch in p) inline(ch)
    }

    /** Begin a paragraph: new paragraph if needed, then its style, list and block look. */
    private fun startParagraph(kind: Int, style: String?) {
        flushTable()
        flushText()
        if (needBreak) {
            cmd("text.newParagraph")
            needBreak = false
        }
        // The style first: applying one after the list is on could take the list away again.
        if (style != null) { cmd("para.style", JSONObject().put("style", style)); styled = true }
        else if (styled) { cmd("para.style", JSONObject().put("style", "Normal")); styled = false }
        if (kind != list) {
            // The new paragraph inherited the previous one's list; switch it off before turning the other on.
            if (list == 1 || list == UNKNOWN_LIST) cmd("para.bullets", JSONObject().put("off", true))
            if (list == 2 || list == UNKNOWN_LIST) cmd("para.numbering", JSONObject().put("off", true))
            if (kind == 1) cmd("para.bullets")
            if (kind == 2) cmd("para.numbering")
            list = kind
        }
        applyBlock()
    }

    private fun applyBlock() {
        if (resetBlock) {
            resetBlock = false
            cmd("para.borders", JSONObject().put("kind", "none"))
            cmd("para.shading", JSONObject().put("color", JSONObject.NULL))
            cmd("para.align", JSONObject().put("value", "left"))
        }
        when (block) {
            "callout" -> {
                val (line, fill) = CALLOUTS[blockColor] ?: CALLOUTS.getValue("blue")
                cmd("para.borders", JSONObject().put("kind", "all").put("width", 1).put("color", line))
                cmd("para.shading", JSONObject().put("color", fill))
            }
            "box" -> cmd("para.borders", JSONObject().put("kind", "all").put("width", 1).put("color", "404040"))
            "center", "right", "justify" -> cmd("para.align", JSONObject().put("value", block))
        }
    }

    /** A `::: name [colour]` line opens a block; a bare `:::` closes it. */
    private fun directive(line: String) {
        val parts = line.removePrefix(":::").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val name = parts.firstOrNull()?.lowercase().orEmpty()
        when (name) {
            "" -> { if (block.isNotEmpty()) { block = ""; blockColor = ""; resetBlock = true } }
            "callout", "box", "center", "right", "justify" -> {
                if (block.isNotEmpty()) resetBlock = true
                block = name; blockColor = parts.getOrNull(1)?.lowercase().orEmpty()
            }
            "pagebreak" -> {
                flushTable(); flushText()
                if (needBreak) { cmd("text.newParagraph"); needBreak = false }
                cmd("text.pageBreak")
            }
        }
    }

    // ---- tables and charts ------------------------------------------------------------------------

    private fun addTableRow(line: String) { if (line.isNotBlank()) tableRows.add(line) }

    /** Written out only when the next line is not a table row: until then the table may still grow. */
    private fun flushTable() {
        if (tableRows.isEmpty()) return
        val rows = tableRows.map { r -> r.trim().trim('|').split('|').map { it.trim().replace("**", "").replace("`", "") } }
            .filterNot { cells -> cells.all { it.matches(Regex(":?-{2,}:?")) } }
        tableRows.clear()
        if (rows.isEmpty()) return
        val width = rows.maxOf { it.size }
        leaveParagraphKind()
        val csv = rows.joinToString("\n") { r ->
            (r + List(width - r.size) { "" }).joinToString(",") { c -> if (c.any { it == ',' || it == '"' }) "\"" + c.replace("\"", "\"\"") + "\"" else c }
        }
        cmd("insert.spreadsheet", JSONObject().put("csv", csv).put("after", true).put("tidy", true))
        // The caret is now in the paragraph below the table, which the next line writes into.
        needBreak = false
    }

    /** Before a table or chart: a fresh paragraph that is not a list item, heading or block member. */
    private fun leaveParagraphKind() {
        if (needBreak) { cmd("text.newParagraph"); needBreak = false }
        if (list != 0) {
            if (list != 2) cmd("para.bullets", JSONObject().put("off", true))
            if (list != 1) cmd("para.numbering", JSONObject().put("off", true))
            list = 0
        }
        if (styled) { cmd("para.style", JSONObject().put("style", "Normal")); styled = false }
        if (block.isEmpty() && resetBlock) applyBlock()
    }

    private fun closeFence(body: String) {
        val lines = body.lines()
        val rest = lines.drop(1).joinToString("\n")
        val chart = runCatching { JSONObject(rest.trim()) }.getOrNull()
        val cats = chart?.optJSONArray("categories")
        val series = chart?.optJSONArray("series")
        if (chart == null || cats == null || series == null || cats.length() == 0 || series.length() == 0) {
            // Not a chart we can draw: say so, and keep any text rather than losing it.
            if (lines.firstOrNull()?.trim()?.lowercase() == "chart") skipped++ else emitPlain(rest)
            return
        }
        leaveParagraphKind()
        val type = chart.optString("type").let { if (it in CHART_TYPES) it else "column" }
        val data = JSONObject().put("categories", cats).put(
            "series",
            JSONArray().apply {
                for (i in 0 until series.length()) {
                    val s = series.optJSONObject(i) ?: continue
                    put(JSONObject().put("name", s.optString("name", "Series ${i + 1}")).put("values", s.optJSONArray("values") ?: JSONArray()))
                }
            },
        )
        cmd(
            "insert.chart",
            JSONObject().put("type", type).put("data", data).put("legend", if (series.length() > 1 || type == "pie" || type == "doughnut") "bottom" else "none")
                .apply { chart.optString("title").takeIf { it.isNotBlank() }?.let { put("title", it) } },
        )
        // The engine leaves the new chart selected; collapse that, or the next paragraph break would replace the chart.
        cmd("caret.right")
        needBreak = true
    }

    private companion object {
        const val UNKNOWN_LIST = 3
        val CHART_TYPES = setOf("column", "columnStacked", "bar", "barStacked", "line", "area", "pie", "doughnut", "scatter")
        val COLOURS = mapOf(
            "red" to "C00000", "green" to "2E7D32", "blue" to "1F4E9C", "orange" to "E36C0A", "purple" to "7030A0",
            "gray" to "595959", "grey" to "595959", "teal" to "00796B", "black" to "000000",
        )
        /** Callout colours: border, then fill. */
        val CALLOUTS = mapOf(
            "blue" to ("2E75B6" to "DEEAF6"), "green" to ("548235" to "E2EFDA"), "red" to ("C00000" to "FBE4E4"),
            "yellow" to ("BF9000" to "FFF2CC"), "gray" to ("7F7F7F" to "EDEDED"),
        )
    }
}
