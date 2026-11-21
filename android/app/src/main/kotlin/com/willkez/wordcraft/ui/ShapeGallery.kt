package com.willkez.wordcraft.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SHAPE_SECTIONS = listOf(
    "Lines and connectors" to listOf("Line" to "line", "Straight connector" to "straightConnector", "Elbow connector" to "elbowConnector", "Curved connector" to "curvedConnector"),
    "Basic shapes" to listOf(
        "Rectangle" to "rectangle", "Rounded rectangle" to "roundedRectangle", "Oval" to "ellipse", "Triangle" to "triangle",
        "Diamond" to "diamond", "Star" to "star", "Heart" to "heart", "Arrow" to "arrow",
    ),
)

/** Shapes as Word shows them: a gallery of drawn previews, grouped, instead of a list of names. */
@Composable
fun ShapeGalleryBtn(enabled: Boolean, onPick: (kind: String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RBtn("Shapes ▾", Icons.Default.Category, enabled) { open = true }
        DropdownMenu(open, { open = false }, Modifier.heightIn(max = 420.dp)) {
            Column(Modifier.width(272.dp).padding(horizontal = 10.dp, vertical = 4.dp)) {
                SHAPE_SECTIONS.forEach { (title, shapes) ->
                    Text(title, Modifier.padding(top = 8.dp, bottom = 4.dp), fontSize = 11.sp, color = SheetMuted)
                    shapes.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { (name, kind) ->
                                Column(
                                    Modifier.width(60.dp).clip(RoundedCornerShape(4.dp)).clickable { open = false; onPick(kind) }.padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Canvas(Modifier.size(40.dp, 32.dp)) { preview(kind, WordBlue) }
                                    Text(name, fontSize = 9.sp, maxLines = 2, lineHeight = 10.sp, color = RibbonInk)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.preview(kind: String, c: Color) {
    val w = size.width; val h = size.height; val p = 4f
    val line = Stroke(width = 3.5f, cap = StrokeCap.Round)
    fun poly(vararg pts: Pair<Float, Float>): Path = Path().apply {
        moveTo(pts[0].first, pts[0].second); for (i in 1 until pts.size) lineTo(pts[i].first, pts[i].second); close()
    }
    when (kind) {
        "rectangle" -> drawRect(c, Offset(p, p * 2), Size(w - 2 * p, h - 4 * p))
        "roundedRectangle" -> drawRoundRect(c, Offset(p, p * 2), Size(w - 2 * p, h - 4 * p), CornerRadius(9f))
        "ellipse" -> drawOval(c, Offset(p, p), Size(w - 2 * p, h - 2 * p))
        "triangle" -> drawPath(poly(w / 2 to p, w - p to h - p, p to h - p), c)
        "diamond" -> drawPath(poly(w / 2 to p, w - p to h / 2, w / 2 to h - p, p to h / 2), c)
        "arrow" -> drawPath(poly(p to h * .34f, w * .58f to h * .34f, w * .58f to p, w - p to h / 2, w * .58f to h - p, w * .58f to h * .66f, p to h * .66f), c)
        "star" -> {
            val cx = w / 2; val cy = h / 2 + 1f; val ro = minOf(w, h) / 2 - 1f; val ri = ro * 0.45f
            val path = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) ro else ri
                val a = -PI / 2 + i * PI / 5
                val x = cx + (r * cos(a)).toFloat(); val y = cy + (r * sin(a)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close(); drawPath(path, c)
        }
        "heart" -> drawPath(
            Path().apply {
                moveTo(w / 2, h - p)
                cubicTo(w * .02f, h * .58f, w * .08f, p, w / 2, h * .30f)
                cubicTo(w * .92f, p, w * .98f, h * .58f, w / 2, h - p)
                close()
            },
            c,
        )
        "line" -> drawLine(c, Offset(p, h - p), Offset(w - p, p), 3.5f, StrokeCap.Round)
        "straightConnector" -> {
            drawLine(c, Offset(p + 3, h - p - 3), Offset(w - p - 3, p + 3), 3.5f, StrokeCap.Round)
            drawCircle(c, 3.5f, Offset(p + 3, h - p - 3)); drawCircle(c, 3.5f, Offset(w - p - 3, p + 3))
        }
        "elbowConnector" -> drawPath(Path().apply { moveTo(p, h - p); lineTo(w / 2, h - p); lineTo(w / 2, p); lineTo(w - p, p) }, c, style = line)
        "curvedConnector" -> drawPath(Path().apply { moveTo(p, h - p); cubicTo(w * .75f, h - p, w * .25f, p, w - p, p) }, c, style = line)
    }
}

/** Symbols as a tappable grid (8 across) instead of a long list. */
@Composable
fun SymbolGalleryBtn(enabled: Boolean, symbols: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RBtn("Symbol ▾", Icons.Default.EmojiSymbols, enabled) { open = true }
        DropdownMenu(open, { open = false }, Modifier.heightIn(max = 360.dp)) {
            Column(Modifier.width(272.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                symbols.chunked(8).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { s ->
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)).clickable { open = false; onPick(s) }, contentAlignment = Alignment.Center) {
                                Text(s, fontSize = 17.sp, color = RibbonInk)
                            }
                        }
                    }
                }
            }
        }
    }
}
