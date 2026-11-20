@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.willkez.wordcraft.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import com.willkez.wordcraft.editor.EditorViewModel

private val SchemeStart = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

/** A heading the engine can link to: its block number, level (1–9) and text. */
private class HeadingTarget(val block: Int, val level: Int, val text: String)

// ---------------------------------------------------------------------------------------------
// Insert › Link
// ---------------------------------------------------------------------------------------------

/**
 * Insert a link to a web page, to a place in this document (the top, a heading or a bookmark) or to
 * an e-mail address. Headings and bookmarks are read from the engine (`link.targets`).
 */
@Composable
fun LinkSheet(vm: EditorViewModel, d: Dispatcher, onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf("web") }
    var url by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<String?>(null) }
    var headings by remember { mutableStateOf(listOf<HeadingTarget>()) }
    var bookmarks by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(Unit) {
        vm.query("link.targets") { out ->
            runCatching {
                val o = JSONObject(out)
                val h = o.optJSONArray("headings")
                headings = (0 until (h?.length() ?: 0)).map { i ->
                    val e = h!!.getJSONObject(i)
                    HeadingTarget(e.optInt("block"), e.optInt("level", 1), e.optString("text"))
                }
                val b = o.optJSONArray("bookmarks")
                bookmarks = (0 until (b?.length() ?: 0)).map { b!!.getString(it) }
            }
        }
    }

    val canApply = when (tab) {
        "web" -> url.isNotBlank()
        "doc" -> picked != null
        else -> email.isNotBlank()
    }

    fun submit() {
        val params = HashMap<String, Any?>()
        when (tab) {
            "web" -> {
                val u = url.trim()
                params["url"] = if (SchemeStart.containsMatchIn(u)) u else "https://$u"
            }
            "email" -> {
                val q = if (subject.isNotBlank()) "?subject=" + android.net.Uri.encode(subject.trim()) else ""
                params["url"] = "mailto:" + email.trim() + q
            }
            else -> when {
                picked == "top" -> params["top"] = true
                picked?.startsWith("h:") == true -> params["heading"] = picked!!.removePrefix("h:").toInt()
                picked?.startsWith("b:") == true -> params["bookmark"] = picked!!.removePrefix("b:")
            }
        }
        if (text.isNotBlank()) params["text"] = text.trim()
        d.id("insert.link", params)
        onDismiss()
    }

    AppSheet(onDismiss) {
        SheetTitle("Insert link", "Insert")
        PillRow {
            Pill("Web page", tab == "web") { tab = "web" }
            Pill("This document", tab == "doc") { tab = "doc" }
            Pill("Email", tab == "email") { tab = "email" }
        }
        SheetScroll(0.5f) {
            when (tab) {
                "web" -> {
                    SheetField("Address", url, { url = it }, "https://example.com", KeyboardType.Uri)
                }
                "email" -> {
                    SheetField("Email address", email, { email = it }, "name@example.com", KeyboardType.Email)
                    SheetField("Subject (optional)", subject, { subject = it })
                }
                else -> {
                    TargetRow("Top of the document", 0, picked == "top") { picked = "top" }
                    if (headings.isNotEmpty()) SectionLabel("Headings")
                    headings.forEach { h ->
                        TargetRow(h.text, h.level - 1, picked == "h:${h.block}") { picked = "h:${h.block}" }
                    }
                    if (bookmarks.isNotEmpty()) SectionLabel("Bookmarks")
                    bookmarks.forEach { b -> TargetRow(b, 0, picked == "b:$b") { picked = "b:$b" } }
                    if (headings.isEmpty() && bookmarks.isEmpty()) {
                        Text(
                            "No headings or bookmarks yet. Apply a Heading style to a line, or add a bookmark, to link to it.",
                            Modifier.padding(top = 10.dp), fontSize = 13.sp, color = SheetMuted,
                        )
                    }
                }
            }
            SheetField("Text to display (optional)", text, { text = it })
        }
        PrimaryButton("Apply", canApply) { submit() }
        QuietButton("Remove link from selection") { d.id("insert.removeLink"); onDismiss() }
    }
}

@Composable
private fun SheetField(label: String, value: String, onChange: (String) -> Unit, placeholder: String? = null, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        placeholder = if (placeholder != null) ({ Text(placeholder, color = SheetMuted) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    )
}

@Composable
private fun TargetRow(label: String, indent: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        label,
        Modifier.fillMaxWidth().padding(top = 4.dp).clip(shape)
            .background(if (selected) PillOn else Color.Transparent)
            .border(1.dp, if (selected) WordBlue else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(start = (12 + indent.coerceIn(0, 6) * 14).dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
        fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
        color = if (selected) WordBlue else SheetInk,
    )
}

// ---------------------------------------------------------------------------------------------
// References › Table of Contents
// ---------------------------------------------------------------------------------------------

private val LEADERS = listOf("dot" to "Dots", "hyphen" to "Hyphens", "underscore" to "Underscore", "none" to "None")

/** Turns "Quote=2, Caption=3" into {"Quote": 2, "Caption": 3}; anything that isn't `name=level` is ignored. */
private fun parseStyleLevels(raw: String): Map<String, Int> =
    raw.split(',', ';').mapNotNull { part ->
        val bits = part.split('=', ':')
        val level = bits.getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it in 1..9 }
        val name = bits.getOrNull(0)?.trim().orEmpty()
        if (name.isNotEmpty() && level != null) name to level else null
    }.toMap()

/** Build a table of contents from headings, with a live preview of the page numbers and leaders. */
@Composable
fun TocSheet(d: Dispatcher, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("Contents") }
    var levels by remember { mutableStateOf(3) }
    var pageNumbers by remember { mutableStateOf(true) }
    var rightAlign by remember { mutableStateOf(true) }
    var leader by remember { mutableStateOf("dot") }
    var hyperlinks by remember { mutableStateOf(true) }
    var advanced by remember { mutableStateOf(false) }
    var headingStyles by remember { mutableStateOf(true) }
    var outlineLevels by remember { mutableStateOf(false) }
    var tcFields by remember { mutableStateOf(false) }
    var styleLevels by remember { mutableStateOf("") }

    AppSheet(onDismiss) {
        SheetTitle("Table of contents", "References")
        SheetScroll {
            TocPreview(title, levels, pageNumbers, rightAlign, leader)
            SheetField("Title", title, { title = it })
            Stepper(levels, 1..9, "Heading levels to show") { levels = it }
            SwitchRow("Show page numbers", pageNumbers) { pageNumbers = it }
            if (pageNumbers) {
                SwitchRow("Align page numbers right", rightAlign) { rightAlign = it }
                SectionLabel("Tab leader")
                PillRow { LEADERS.forEach { (k, n) -> Pill(n, leader == k) { leader = k } } }
            }
            SwitchRow("Entries are links", hyperlinks, "Tap an entry to jump to its heading") { hyperlinks = it }
            Pill(if (advanced) "Hide more options" else "More options", advanced) { advanced = !advanced }
            if (advanced) {
                SwitchRow("Use heading styles", headingStyles) { headingStyles = it }
                SwitchRow("Use outline levels", outlineLevels, "Include paragraphs that have an outline level") { outlineLevels = it }
                SwitchRow("Use TC fields", tcFields, "Include entries marked with TC fields") { tcFields = it }
                SheetField("Style levels (optional)", styleLevels, { styleLevels = it }, "Quote=2, Caption=3")
                Text("Treat other styles as headings: style name = level 1–9, separated by commas.", fontSize = 11.sp, color = SheetMuted, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
            }
        }
        PrimaryButton("Insert table of contents") {
            val params = HashMap<String, Any?>()
            params["levels"] = levels
            params["pageNumbers"] = pageNumbers
            params["rightAlign"] = rightAlign
            params["tabLeader"] = leader
            params["hyperlinks"] = hyperlinks
            params["headingStyles"] = headingStyles
            params["outlineLevels"] = outlineLevels
            params["tcFields"] = tcFields
            if (title.isNotBlank()) params["title"] = title.trim()
            parseStyleLevels(styleLevels).takeIf { it.isNotEmpty() }?.let { params["styleLevels"] = it }
            d.id("references.toc", params)
            onDismiss()
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            QuietButton("Update table") { d.id("references.updateToc"); onDismiss() }
            QuietButton("Remove table") { d.id("references.removeToc"); onDismiss() }
        }
    }
}

/** What the contents page will look like, using sample headings. */
@Composable
private fun TocPreview(title: String, levels: Int, pageNumbers: Boolean, rightAlign: Boolean, leader: String) {
    val sample = listOf(1 to "Introduction", 2 to "Background", 3 to "Method in detail", 4 to "Measures", 1 to "Findings", 2 to "Recommendations")
    val fill = when (leader) {
        "dot" -> ". ".repeat(60)
        "hyphen" -> "- ".repeat(60)
        "underscore" -> "_".repeat(120)
        else -> ""
    }
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Color(0xFFFAFAFA)).border(1.dp, SheetLine, shape).padding(12.dp)) {
        if (title.isNotBlank()) Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F4761), maxLines = 1)
        sample.filter { it.first <= levels }.forEachIndexed { i, (level, name) ->
            Row(Modifier.fillMaxWidth().padding(start = ((level - 1) * 14).dp, top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 12.sp, color = Color(0xFF1B1B1B), maxLines = 1)
                if (pageNumbers && rightAlign) {
                    Text(fill, Modifier.weight(1f).padding(horizontal = 4.dp), fontSize = 12.sp, color = Color(0xFF667085), maxLines = 1, overflow = TextOverflow.Clip)
                    Text("${i + 1}", fontSize = 12.sp, color = Color(0xFF1B1B1B))
                } else if (pageNumbers) {
                    Text("   ${i + 1}", fontSize = 12.sp, color = Color(0xFF1B1B1B))
                }
            }
        }
        if (sample.none { it.first <= levels }) Text("Nothing to show", fontSize = 12.sp, color = SheetMuted)
    }
}

// ---------------------------------------------------------------------------------------------
// Insert › Tables › Quick Tables
// ---------------------------------------------------------------------------------------------

private class QuickKind(val id: String, val title: String, val blurb: String, val rows: Int, val cols: Int)

private val QUICK_KINDS = listOf(
    QuickKind("calendar", "Calendar", "A month with a heading row of days", 6, 7),
    QuickKind("tabular", "Tabular list", "Two columns: an item and how many", 7, 2),
    QuickKind("matrix", "Matrix", "Row and column headings with numbers", 4, 4),
)

/** Ready-made tables, each shown as a small picture of what it inserts. */
@Composable
fun QuickTablesSheet(d: Dispatcher, onDismiss: () -> Unit) {
    AppSheet(onDismiss) {
        SheetTitle("Quick tables", "Insert")
        QUICK_KINDS.forEach { k ->
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(shape).background(PillIdle)
                    .clickable { d.id("table.quick", mapOf("kind" to k.id)); onDismiss() }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuickPreview(k.rows, k.cols)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(k.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = SheetInk)
                    Text(k.blurb, fontSize = 12.sp, color = SheetMuted)
                }
            }
        }
        DoneButton("Cancel", onDismiss)
    }
}

@Composable
private fun QuickPreview(rows: Int, cols: Int) {
    Canvas(Modifier.size(width = 84.dp, height = 64.dp).clip(RoundedCornerShape(6.dp)).background(Color.White)) {
        val rh = size.height / rows; val cw = size.width / cols
        drawRect(Color(0xFF4472C4), Offset(0f, 0f), Size(size.width, rh))
        for (r in 1 until rows) if (r % 2 == 0) drawRect(Color(0x144472C4), Offset(0f, r * rh), Size(size.width, rh))
        val grid = Color(0xFFB8BEC8)
        for (r in 0..rows) drawLine(grid, Offset(0f, r * rh), Offset(size.width, r * rh), 1f)
        for (c in 0..cols) drawLine(grid, Offset(c * cw, 0f), Offset(c * cw, size.height), 1f)
    }
}
