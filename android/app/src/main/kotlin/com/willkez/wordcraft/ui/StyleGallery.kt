package com.willkez.wordcraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How a style's sample looks in the gallery; `id` is what the engine's `para.style` takes. */
class StyleSpec(
    val id: String, val label: String, val color: Color, val sizeSp: Float,
    val bold: Boolean = false, val italic: Boolean = false, val sample: String = "AaBbCcDd",
)

val STYLE_SPECS = listOf(
    StyleSpec("Normal", "Normal", Color(0xFF000000), 13f),
    StyleSpec("NoSpacing", "No Spacing", Color(0xFF000000), 13f),
    StyleSpec("Heading1", "Heading 1", Color(0xFF2F5496), 17f, sample = "AaBbCc"),
    StyleSpec("Heading2", "Heading 2", Color(0xFF2F5496), 14f, sample = "AaBbCcDc"),
    StyleSpec("Heading3", "Heading 3", Color(0xFF1F3763), 13f, sample = "AaBbCcDc"),
    StyleSpec("Title", "Title", Color(0xFF000000), 21f, sample = "AaB"),
    StyleSpec("Subtitle", "Subtitle", Color(0xFF5A5A5A), 13f),
    StyleSpec("Quote", "Quote", Color(0xFF404040), 13f, italic = true, sample = "AaBbCcDd"),
    StyleSpec("IntenseQuote", "Intense Quote", Color(0xFF2F5496), 12f, bold = true, italic = true, sample = "AaBbCcDd"),
    StyleSpec("ListParagraph", "List Paragraph", Color(0xFF000000), 13f),
)

private fun norm(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()

/** True when the caret's style (the engine reports a name or an id) is this tile's style. */
fun styleMatches(current: String?, spec: StyleSpec): Boolean {
    val c = norm(current ?: return false)
    return c == norm(spec.id) || c == norm(spec.label)
}

/** Word's style tile: a sample of the look on white, the name on a grey strip, a blue frame when current. */
@Composable
fun StyleTile(spec: StyleSpec, selected: Boolean, width: Int = 86, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.width(width.dp).clip(shape).background(Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) WordBlue else Color(0xFFD0D5DD), shape).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
            Text(
                spec.sample, fontSize = spec.sizeSp.sp, color = spec.color, maxLines = 1,
                fontWeight = if (spec.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (spec.italic) FontStyle.Italic else FontStyle.Normal,
            )
        }
        Text(
            spec.label, Modifier.fillMaxWidth().background(if (selected) Color(0xFFDCE8FA) else Color(0xFFF2F4F7)).padding(vertical = 4.dp),
            fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = if (selected) WordBlue else Color(0xFF344054),
        )
    }
}

/** The tiles to show: the built-in looks first, then any other quick style the document has. */
fun styleTiles(quickNames: List<String>): List<StyleSpec> {
    val extra = quickNames.filter { n -> STYLE_SPECS.none { styleMatches(n, it) } }.map { StyleSpec(it, it, Color(0xFF000000), 13f) }
    return STYLE_SPECS + extra
}
