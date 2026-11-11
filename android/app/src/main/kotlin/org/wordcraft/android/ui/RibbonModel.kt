package org.wordcraft.android.ui

import org.json.JSONArray

/** One input of a command, read from the registry's free-form `params` text. */
enum class FieldKind { TEXT, NUMBER, BOOL, JSON }

data class ParamField(val name: String, val optional: Boolean, val hint: String, val kind: FieldKind, val options: List<String>)

private val FIELD = Regex("\"([A-Za-z0-9_]+)\"(\\?)?\\s*:\\s*([^,}]*)")
private val QUOTED = Regex("\"([^\"]+)\"")

fun parseParams(p: String): List<ParamField> {
    if (p.isBlank() || p.trimStart().startsWith("{}")) return emptyList()
    return FIELD.findAll(p).map { m ->
        val hint = m.groupValues[3].trim()
        val h = hint.lowercase()
        val options = if (hint.contains('|')) QUOTED.findAll(hint).flatMap { it.groupValues[1].split('|') }.toList() else emptyList()
        val kind = when {
            h.startsWith("bool") -> FieldKind.BOOL
            h == "n" || h.startsWith("n ") || h.startsWith("number") || h.startsWith("integer") || h.startsWith("non-negative") ||
                h.startsWith("f32") || h.startsWith("u32") || h.startsWith("percent") || h.startsWith("pt") -> FieldKind.NUMBER
            h.startsWith("string") || h.startsWith("\"") -> FieldKind.TEXT
            else -> FieldKind.JSON
        }
        ParamField(m.groupValues[1], m.groupValues[2].isNotEmpty(), hint, kind, options)
    }.toList()
}

/** A registry command: `location` is `Tab › Group › Sub-group`, exactly as the engine reports it. */
data class Cmd(val id: String, val label: String, val location: String, val shortcut: String, val params: String, val mutates: Boolean) {
    private val parts: List<String> get() = location.split(" › ")
    val tab: String get() = parts.first()
    val group: String get() = parts.getOrNull(1) ?: tab
    val sub: String get() = parts.drop(2).joinToString(" › ")
    val fields: List<ParamField> by lazy { parseParams(params) }

    /** Commands whose every input is optional run straight away; the rest ask first. */
    val needsInput: Boolean get() = fields.any { !it.optional }
}

data class RibbonGroup(val name: String, val cmds: List<Cmd>)

val RIBBON_TABS = listOf("Home", "Insert", "Draw", "Design", "Layout", "References", "Mailings", "Review", "View", "Developer", "Zotero", "Help")

/** Tabs that appear only while they apply (a table, a picture, a header, …). */
val CONTEXT_TABS = listOf("Table Design", "Table Layout", "Picture Format", "Shape Format", "Equation", "Chart Design", "SmartArt Design", "Outlining", "Header & Footer")

class RibbonModel(val all: List<Cmd>) {
    val byId: Map<String, Cmd> = all.associateBy { it.id }
    private val byTab: Map<String, List<Cmd>> = all.groupBy { it.tab }

    val tabs: List<String> = RIBBON_TABS.filter { byTab.containsKey(it) || it == "Home" }
    val contextTabs: List<String> = CONTEXT_TABS.filter { byTab.containsKey(it) }

    fun tabCmds(tab: String): List<Cmd> = byTab[tab].orEmpty()
    fun groups(tab: String): List<RibbonGroup> = tabCmds(tab).groupBy { it.group }.map { (g, c) -> RibbonGroup(g, c) }

    /** Every id whose enabled state the ribbon needs for `tab`, plus the contextual tabs' (to know which to show). */
    fun watchedIds(tab: String): List<String> =
        (tabCmds(tab) + contextTabs.flatMap { tabCmds(it) }).map { it.id }.distinct()

    companion object {
        fun parse(json: String): RibbonModel {
            val arr = JSONArray(json)
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Cmd(o.getString("id"), o.optString("label"), o.optString("location"), o.optString("shortcut"), o.optString("params"), o.optBoolean("mutates", true))
            }
            return RibbonModel(list)
        }
    }
}
