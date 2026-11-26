package com.willkez.wordcraft.ai

import org.json.JSONArray
import org.json.JSONObject

/** One change the assistant proposes. Each becomes one or more engine commands when accepted. */
sealed interface AiOp {
    data class InsertText(val text: String) : AiOp
    data class ReplaceSelection(val text: String) : AiOp
    data class Replace(val find: String, val with: String) : AiOp
    data class Table(val rows: List<List<String>>) : AiOp
    data class Chart(val type: String, val title: String?, val categories: List<String>, val series: List<Pair<String, List<Double?>>>) : AiOp
    data class Style(val find: String, val style: String) : AiOp
}

data class AiPlan(val summary: String, val ops: List<AiOp>, val skipped: Int = 0)

/** What the assistant is allowed to know: the selection and the document's text. */
data class AiContext(val selection: String, val document: String)

private val CHART_TYPES = setOf("column", "columnStacked", "bar", "barStacked", "line", "area", "pie", "doughnut", "scatter")

object AiPlans {

    /** The model's JSON answer, tolerant of code fences and stray words around the object. */
    fun parse(raw: String, ctx: AiContext): AiPlan {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start in 0 until end) { "The assistant did not return an edit I could read. Try again." }
        val obj = JSONObject(raw.substring(start, end + 1))
        val arr = obj.optJSONArray("ops") ?: JSONArray()
        val ops = mutableListOf<AiOp>()
        var skipped = 0
        var structures = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val op: AiOp? = when (o.optString("op")) {
                "insert_text" -> o.optString("text").takeIf { it.isNotBlank() }?.let { AiOp.InsertText(it) }
                "replace_selection" -> o.optString("text").takeIf { it.isNotBlank() && ctx.selection.isNotEmpty() }?.let { AiOp.ReplaceSelection(it) }
                "replace" -> {
                    val f = o.optString("find")
                    val w = o.optString("with")
                    // A phrase that is not really in the document would make the whole edit fail: leave it out.
                    if (f.isNotEmpty() && f != w && ctx.document.contains(f)) AiOp.Replace(f, w) else null
                }
                "style" -> {
                    val f = o.optString("find")
                    val st = o.optString("style")
                    if (f.isNotEmpty() && st.isNotEmpty() && ctx.document.contains(f)) AiOp.Style(f, st) else null
                }
                "table" -> {
                    val rows = o.optJSONArray("rows")?.let { r ->
                        (0 until r.length()).mapNotNull { y ->
                            r.optJSONArray(y)?.let { row -> (0 until row.length()).map { x -> row.opt(x)?.toString().orEmpty() } }
                        }
                    }.orEmpty()
                    val width = rows.maxOfOrNull { it.size } ?: 0
                    if (rows.isNotEmpty() && width > 0 && structures == 0) {
                        structures++
                        AiOp.Table(rows.map { it + List(width - it.size) { "" } })
                    } else null
                }
                "chart" -> {
                    val cats = o.optJSONArray("categories")?.let { a -> (0 until a.length()).map { a.opt(it)?.toString().orEmpty() } }.orEmpty()
                    val series = o.optJSONArray("series")?.let { a ->
                        (0 until a.length()).mapNotNull { i2 ->
                            val s = a.optJSONObject(i2) ?: return@mapNotNull null
                            val v = s.optJSONArray("values") ?: return@mapNotNull null
                            s.optString("name", "Series ${i2 + 1}") to (0 until v.length()).map { k -> if (v.isNull(k)) null else v.optDouble(k).takeIf { d -> !d.isNaN() } }
                        }
                    }.orEmpty()
                    val type = o.optString("type").let { if (it in CHART_TYPES) it else "column" }
                    if (cats.isNotEmpty() && series.isNotEmpty() && structures == 0) {
                        structures++
                        AiOp.Chart(type, o.optString("title").takeIf { it.isNotBlank() }, cats, series)
                    } else null
                }
                else -> null
            }
            if (op != null) ops += op else skipped++
        }
        return AiPlan(obj.optString("summary"), ops, skipped)
    }

    /** One line saying what an operation will do, for the review list. */
    fun describe(op: AiOp): String = when (op) {
        is AiOp.InsertText -> "Insert text at the cursor:\n" + preview(op.text)
        is AiOp.ReplaceSelection -> "Replace the selection with:\n" + preview(op.text)
        is AiOp.Replace -> "Change “${op.find}” to “${op.with}”"
        is AiOp.Style -> "Make “${op.find.take(40)}” ${op.style}"
        is AiOp.Table -> "Insert a table, ${op.rows.size} rows × ${op.rows.firstOrNull()?.size ?: 0} columns"
        is AiOp.Chart -> "Insert a ${op.type} chart${op.title?.let { ": $it" } ?: ""} (${op.series.size} series, ${op.categories.size} points)"
    }

    private fun preview(t: String) = t.trim().let { if (it.length > 240) it.take(240) + "…" else it }

    /**
     * The engine commands for the accepted operations, in a safe order: text goes in at the cursor first, then a
     * table or chart, and the find-based changes last (they move the selection).
     */
    fun commands(ops: List<AiOp>): JSONArray {
        val out = JSONArray()
        fun cmd(id: String, params: JSONObject = JSONObject()) { out.put(JSONObject().put("cmd", id).put("params", params)) }
        fun paragraphs(t: String) = t.replace("\r", "").replace(Regex("\n{2,}"), "\n").trim('\n')

        for (op in ops) when (op) {
            is AiOp.InsertText -> cmd("text.insert", JSONObject().put("text", paragraphs(op.text)).put("paragraphs", true))
            is AiOp.ReplaceSelection -> cmd("text.insert", JSONObject().put("text", paragraphs(op.text)).put("paragraphs", true))
            else -> {}
        }
        for (op in ops) when (op) {
            is AiOp.Table -> cmd("insert.spreadsheet", JSONObject().put("csv", csv(op.rows)).put("after", true).put("tidy", true))
            is AiOp.Chart -> cmd(
                "insert.chart",
                JSONObject().put("type", op.type).put("legend", if (op.series.size > 1 || op.type in setOf("pie", "doughnut")) "bottom" else "none")
                    .apply { op.title?.let { put("title", it) } }
                    .put(
                        "data",
                        JSONObject().put("categories", JSONArray(op.categories)).put(
                            "series",
                            JSONArray().apply {
                                op.series.forEach { (n, v) ->
                                    put(JSONObject().put("name", n).put("values", JSONArray().apply { v.forEach { x -> put(x ?: JSONObject.NULL) } }))
                                }
                            },
                        ),
                    ),
            ).also { cmd("caret.right") } // the engine leaves the new chart selected
            else -> {}
        }
        for (op in ops) when (op) {
            // `special: false` so a "^" or "\" in the model's text is just text, not a search code.
            is AiOp.Replace -> cmd("edit.replaceAll", JSONObject().put("text", op.find).put("with", op.with).put("matchCase", true).put("special", false))
            is AiOp.Style -> {
                cmd("caret.docStart", JSONObject())
                cmd("edit.find", JSONObject().put("text", op.find).put("matchCase", true).put("special", false))
                cmd("para.style", JSONObject().put("style", op.style))
            }
            else -> {}
        }
        return out
    }

    private fun csv(rows: List<List<String>>): String = rows.joinToString("\n") { r ->
        r.joinToString(",") { c ->
            val t = c.replace("\n", " ")
            if (t.any { it == ',' || it == '"' }) "\"" + t.replace("\"", "\"\"") + "\"" else t
        }
    }
}
