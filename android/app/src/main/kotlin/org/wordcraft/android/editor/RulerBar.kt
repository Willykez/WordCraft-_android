package org.wordcraft.android.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.wordcraft.android.ui.WordBlue
import org.wordcraft.bridge.RulerInfo
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val KINDS = listOf("left", "center", "right", "decimal", "bar")
private val KIND_GLYPH = mapOf("left" to "L", "center" to "⊥", "right" to "⅃", "decimal" to "⊥.", "bar" to "|")
private enum class Grab { FIRST, HANGING, LEFT_BOTH, RIGHT, TAB }

/**
 * The horizontal ruler for the caret's paragraph: inch graduations, the text column, and Word's
 * indent markers (first line, hanging, left, right) and tab stops. Drag a marker to change it;
 * tap the ruler to add a tab stop of the kind chosen in the corner box; drag a tab off the ruler to clear it.
 */
@Composable
fun RulerBar(
    r: RulerInfo,
    pageLeftPx: Float,
    pxPerPt: Float,
    onIndents: (left: Float?, right: Float?, first: Float?) -> Unit,
    onTab: (clear: Float?, pos: Float?, kind: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var kind by remember { mutableStateOf("left") }
    // While dragging: which marker, its live position (page points) and the vertical pull for removing a tab.
    var grab by remember { mutableStateOf<Grab?>(null) }
    var live by remember { mutableFloatStateOf(0f) }
    var pull by remember { mutableFloatStateOf(0f) }
    var tabIndex by remember { mutableIntStateOf(-1) }

    fun snap(pt: Float) = (pt / 4.5f).roundToInt() * 4.5f
    val leftAbs = r.bodyX + r.left
    val firstAbs = leftAbs + r.firstLine
    val rightAbs = r.bodyX + r.bodyW - r.right

    Box(modifier.fillMaxWidth().height(26.dp).background(Color(0xFFE9ECF1))) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(r, pageLeftPx, pxPerPt) {
                    detectTapGestures { o ->
                        val pt = snap((o.x - pageLeftPx) / pxPerPt) - r.bodyX
                        if (pt > 0f && pt < r.bodyW) onTab(null, pt, kind)
                    }
                }
                .pointerInput(r, pageLeftPx, pxPerPt, kind) {
                    detectDragGestures(
                        onDragStart = { o ->
                            val h = size.height.toFloat()
                            val reach = 18.dp.toPx()
                            fun px(abs: Float) = pageLeftPx + abs * pxPerPt
                            val topZone = o.y < h * 0.42f
                            val midZone = o.y in (h * 0.42f)..(h * 0.75f)
                            grab = when {
                                topZone && abs(o.x - px(firstAbs)) < reach -> Grab.FIRST.also { live = firstAbs }
                                !topZone && abs(o.x - px(rightAbs)) < reach -> Grab.RIGHT.also { live = rightAbs }
                                midZone && abs(o.x - px(leftAbs)) < reach -> Grab.HANGING.also { live = leftAbs }
                                !topZone && !midZone && abs(o.x - px(leftAbs)) < reach -> Grab.LEFT_BOTH.also { live = leftAbs }
                                else -> {
                                    val i = r.tabs.indexOfFirst { abs(o.x - px(r.bodyX + it.pos)) < reach * 0.7f }
                                    if (i >= 0) { tabIndex = i; live = r.bodyX + r.tabs[i].pos; Grab.TAB } else null
                                }
                            }
                            pull = 0f
                        },
                        onDragEnd = {
                            val g = grab
                            when (g) {
                                Grab.FIRST -> onIndents(null, null, live - leftAbs)
                                Grab.HANGING -> onIndents(live - r.bodyX, null, firstAbs - live)
                                Grab.LEFT_BOTH -> onIndents(live - r.bodyX, null, null)
                                Grab.RIGHT -> onIndents(null, r.bodyX + r.bodyW - live, null)
                                Grab.TAB -> r.tabs.getOrNull(tabIndex)?.let { t ->
                                    if (abs(pull) > 40.dp.toPx()) onTab(t.pos, null, t.kind) else onTab(t.pos, live - r.bodyX, t.kind)
                                }
                                null -> {}
                            }
                            grab = null
                        },
                        onDragCancel = { grab = null },
                    ) { c, d ->
                        c.consume()
                        live = snap(live + d.x / pxPerPt)
                        pull += d.y
                    }
                },
        ) {
            val h = size.height
            fun px(abs: Float) = pageLeftPx + abs * pxPerPt
            // Page and text column.
            drawRect(Color(0xFFD5DAE2), Offset(px(0f), 4.dp.toPx()), androidx.compose.ui.geometry.Size(r.pageW * pxPerPt, h - 8.dp.toPx()))
            drawRect(Color.White, Offset(px(r.bodyX), 4.dp.toPx()), androidx.compose.ui.geometry.Size(r.bodyW * pxPerPt, h - 8.dp.toPx()))
            // Graduations from the left margin: every quarter inch, a number each inch.
            val paint = android.graphics.Paint().apply { color = 0xFF5F6672.toInt(); textSize = 9.sp.toPx(); isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER }
            val kMin = floor(-r.bodyX / 18f).toInt(); val kMax = ceil((r.pageW - r.bodyX) / 18f).toInt()
            for (k in kMin..kMax) {
                val x = px(r.bodyX + k * 18f)
                when {
                    k % 4 == 0 -> if (k != 0) drawContext.canvas.nativeCanvas.drawText("${abs(k / 4)}", x, h / 2f + 3.dp.toPx(), paint)
                    k % 2 == 0 -> drawLine(Color(0xFF8A919C), Offset(x, h / 2f - 3.dp.toPx()), Offset(x, h / 2f + 3.dp.toPx()), 1.2f)
                    else -> drawLine(Color(0xFFB4BAC4), Offset(x, h / 2f - 1.5.dp.toPx()), Offset(x, h / 2f + 1.5.dp.toPx()), 1f)
                }
            }
            // Tab stops.
            r.tabs.forEachIndexed { i, t ->
                val ax = if (grab == Grab.TAB && i == tabIndex) live else r.bodyX + t.pos
                tabMarker(px(ax), h, t.kind, if (grab == Grab.TAB && i == tabIndex) WordBlue else Color(0xFF444B57))
            }
            // Indent markers; the dragged one follows the finger.
            val fx = when (grab) { Grab.FIRST -> live; else -> firstAbs }
            val lx = when (grab) { Grab.HANGING, Grab.LEFT_BOTH -> live; else -> leftAbs }
            val fxx = if (grab == Grab.LEFT_BOTH) fx + (live - leftAbs) else fx
            val rx = if (grab == Grab.RIGHT) live else rightAbs
            val ink = Color(0xFF3A404B)
            tri(px(fxx), 3.dp.toPx(), down = true, color = if (grab == Grab.FIRST) WordBlue else ink)
            tri(px(lx), h - 8.dp.toPx(), down = false, color = if (grab == Grab.HANGING) WordBlue else ink)
            drawRect(if (grab == Grab.LEFT_BOTH) WordBlue else ink, Offset(px(lx) - 4.5.dp.toPx(), h - 7.dp.toPx()), androidx.compose.ui.geometry.Size(9.dp.toPx(), 4.dp.toPx()))
            tri(px(rx), h - 8.dp.toPx(), down = false, color = if (grab == Grab.RIGHT) WordBlue else ink)
        }
        // Corner box: the kind of tab stop a tap on the ruler adds.
        Box(
            Modifier.align(Alignment.CenterStart).size(26.dp).background(Color(0xFFF7F8FA)).clickable { kind = KINDS[(KINDS.indexOf(kind) + 1) % KINDS.size] },
            contentAlignment = Alignment.Center,
        ) { Text(KIND_GLYPH[kind] ?: "L", fontSize = 13.sp, color = Color(0xFF3A404B)) }
    }
}

private fun DrawScope.tri(x: Float, top: Float, down: Boolean, color: Color) {
    val w = 4.5.dp.toPx(); val hgt = 5.dp.toPx()
    val path = Path().apply {
        if (down) { moveTo(x - w, top); lineTo(x + w, top); lineTo(x, top + hgt) }
        else { moveTo(x - w, top + hgt); lineTo(x + w, top + hgt); lineTo(x, top) }
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.tabMarker(x: Float, h: Float, kind: String, color: Color) {
    val y = h - 6.dp.toPx(); val s = 2.dp.toPx(); val st = Stroke(1.6.dp.toPx())
    when (kind) {
        "center" -> { drawLine(color, Offset(x, y - 5.dp.toPx()), Offset(x, y), st.width); drawLine(color, Offset(x - 4.dp.toPx(), y), Offset(x + 4.dp.toPx(), y), st.width) }
        "right" -> { drawLine(color, Offset(x, y - 5.dp.toPx()), Offset(x, y), st.width); drawLine(color, Offset(x - 4.dp.toPx(), y), Offset(x, y), st.width) }
        "decimal" -> { drawLine(color, Offset(x - 3.dp.toPx(), y), Offset(x + 3.dp.toPx(), y), st.width); drawLine(color, Offset(x - s, y - 5.dp.toPx()), Offset(x - s, y), st.width); drawCircle(color, 1.2.dp.toPx(), Offset(x + 3.dp.toPx(), y - 3.dp.toPx())) }
        "bar" -> drawLine(color, Offset(x, y - 7.dp.toPx()), Offset(x, y + 1.dp.toPx()), st.width)
        else -> { drawLine(color, Offset(x, y - 5.dp.toPx()), Offset(x, y), st.width); drawLine(color, Offset(x, y), Offset(x + 4.dp.toPx(), y), st.width) }
    }
}
