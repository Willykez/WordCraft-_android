package com.willkez.wordcraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Colours picked this session, newest first, so a favourite is one tap away next time. */
object RecentColors {
    val list = mutableStateListOf<String>()
    fun add(hex: String) {
        list.remove(hex); list.add(0, hex)
        while (list.size > 12) list.removeAt(list.size - 1)
    }
}

internal fun hexToColor(hex: String): Color? = hex.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
internal fun colorToHex(c: Color): String = "%02X%02X%02X".format((c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())

private fun hsl(h: Float, s: Float, l: Float): String {
    val c = (1 - abs(2 * l - 1)) * s
    val x = c * (1 - abs((h / 60f) % 2 - 1))
    val m = l - c / 2
    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0f); h < 120 -> Triple(x, c, 0f); h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c); h < 300 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    fun ch(v: Float) = ((v + m) * 255).roundToInt().coerceIn(0, 255)
    return "%02X%02X%02X".format(ch(r), ch(g), ch(b))
}

/** Word's ten "standard colours". */
private val STANDARD = listOf("C00000", "FF0000", "FFC000", "FFFF00", "92D050", "00B050", "00B0F0", "0070C0", "002060", "7030A0")

/** 12 columns: a grey ramp, then 12 hues each from pale to deep, so every family has light and dark shades. */
internal val RICH_GRID: List<List<String>> = run {
    val greys = (0 until 12).map { hsl(0f, 0f, it / 11f) }
    val hues = listOf(0f, 25f, 45f, 62f, 95f, 145f, 172f, 195f, 215f, 245f, 285f, 325f)
    val lights = listOf(0.90f, 0.78f, 0.64f, 0.50f, 0.38f, 0.26f)
    listOf(greys) + lights.map { l -> hues.map { h -> hsl(h, 0.88f, l) } }
}

@Composable
private fun Swatch(hex: String, size: Dp, onClick: (String) -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier.size(size).clip(shape).background(hexToColor(hex) ?: Color.Transparent)
            .border(1.dp, Color(0x33000000), shape).clickable { onClick(hex) },
    )
}

@Composable
private fun ChooserLabel(text: String) =
    Text(text, Modifier.padding(top = 6.dp, bottom = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF667085))

/**
 * The colour list used for text, shading, page colour, borders and shapes: a full grid of hues and shades,
 * standard colours, recently used colours and a custom picker. `autoLabel` adds the "Automatic" / "No colour" entry,
 * which reports `"auto"`.
 */
@Composable
fun ColorChooser(autoLabel: String?, onPick: (String) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    fun pick(hex: String) { if (hex != "auto") RecentColors.add(hex); onPick(hex) }
    Column(Modifier.width(300.dp).padding(horizontal = 10.dp, vertical = 4.dp)) {
        if (autoLabel != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { pick("auto") }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(18.dp).border(1.dp, Color.Gray, CircleShape))
                Text(autoLabel, Modifier.padding(start = 10.dp), fontSize = 14.sp)
            }
        }
        ChooserLabel("Theme colours")
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RICH_GRID.forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { row.forEach { Swatch(it, 22.dp, ::pick) } } }
        }
        ChooserLabel("Standard colours")
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { STANDARD.forEach { Swatch(it, 27.dp, ::pick) } }
        if (RecentColors.list.isNotEmpty()) {
            ChooserLabel("Recent colours")
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { RecentColors.list.take(10).forEach { Swatch(it, 27.dp, ::pick) } }
        }
        TextButton({ custom = true }, Modifier.padding(top = 4.dp)) { Text("More colours…") }
    }
    if (custom) CustomColorDialog("2B579A", { custom = false }) { custom = false; pick(it) }
}

/** Any colour: hue, saturation and brightness sliders, or a hex code. */
@Composable
fun CustomColorDialog(initial: String = "2B579A", onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV((hexToColor(initial) ?: Color.Red).toArgb(), it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(h.coerceIn(0f, 359.9f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
    var hex by remember { mutableStateOf(colorToHex(color)) }
    fun fromSliders() { hex = colorToHex(Color.hsv(h.coerceIn(0f, 359.9f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("More colours") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)).background(hexToColor(hex) ?: color).border(1.dp, Color(0x33000000), RoundedCornerShape(10.dp)))
                Text("Hue", fontSize = 12.sp)
                Slider(h, { h = it; fromSliders() }, valueRange = 0f..359f)
                Text("Saturation", fontSize = 12.sp)
                Slider(s, { s = it; fromSliders() })
                Text("Brightness", fontSize = 12.sp)
                Slider(v, { v = it; fromSliders() })
                OutlinedTextField(
                    hex, { raw ->
                        val clean = raw.filter { it.isLetterOrDigit() }.uppercase().take(6)
                        hex = clean
                        hexToColor(clean)?.let { c ->
                            val a = FloatArray(3); android.graphics.Color.colorToHSV(c.toArgb(), a); h = a[0]; s = a[1]; v = a[2]
                        }
                    },
                    singleLine = true, label = { Text("Hex") }, prefix = { Text("#") },
                )
            }
        },
        confirmButton = { TextButton({ hexToColor(hex)?.let { onPick(hex) } }, enabled = hexToColor(hex) != null) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
