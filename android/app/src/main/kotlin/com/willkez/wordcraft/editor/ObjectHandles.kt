package com.willkez.wordcraft.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.willkez.wordcraft.ui.WordBlue
import com.willkez.wordcraft.bridge.ObjRect
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.roundToInt

private const val MIN_PT = 12f

/**
 * Selection frame with eight resize handles over a selected picture or shape, as in Word.
 *
 * The frame turns with the object (`obj.degrees`, about its centre) and the handles stay on its
 * corners. Dragging a handle resizes about the *opposite* handle, which stays put on the page even
 * when the object is rotated: the frame is kept as centre + size, and the centre moves by half the
 * size change, turned into page axes. Corners keep the proportions. Floating objects (any wrap but
 * inline) can also be dragged. The engine is asked once, on release.
 */
@Composable
fun ObjectHandles(
    obj: ObjRect,
    pxPerPt: Float,
    onCommit: (x: Float, y: Float, w: Float, h: Float, moved: Boolean) -> Unit,
) {
    val cx0 = obj.x + obj.w / 2f
    val cy0 = obj.y + obj.h / 2f
    var cx by remember(obj) { mutableFloatStateOf(cx0) }
    var cy by remember(obj) { mutableFloatStateOf(cy0) }
    var w by remember(obj) { mutableFloatStateOf(obj.w) }
    var h by remember(obj) { mutableFloatStateOf(obj.h) }
    val floating = obj.wrap != "inline" && obj.wrap.isNotEmpty()
    val aspect = if (obj.h > 0f) obj.w / obj.h else 1f
    val density = LocalDensity.current
    val handle = with(density) { 28.dp.toPx() }
    val rad = Math.toRadians(obj.degrees.toDouble())
    val cosT = cos(rad).toFloat()
    val sinT = sin(rad).toFloat()

    /** A vector in the object's own (turned) axes, in page axes. */
    fun toPage(dx: Float, dy: Float) = Offset(dx * cosT - dy * sinT, dx * sinT + dy * cosT)

    // If the engine keeps the old size (it refused, or changed nothing), the dragged frame must not stay where the
    // finger left it: after a moment it snaps back to what the engine says.
    val latest by rememberUpdatedState(obj)
    var settle by remember { mutableIntStateOf(0) }
    LaunchedEffect(settle) {
        if (settle > 0) {
            delay(900)
            val o = latest
            cx = o.x + o.w / 2f; cy = o.y + o.h / 2f; w = o.w; h = o.h
        }
    }

    fun commit() {
        val moved = floating && (abs(cx - cx0) > 0.01f || abs(cy - cy0) > 0.01f)
        onCommit(cx - w / 2f, cy - h / 2f, w, h, moved)
        settle++
    }

    Box(
        Modifier
            .offset { IntOffset(((cx - w / 2f) * pxPerPt).roundToInt(), ((cy - h / 2f) * pxPerPt).roundToInt()) }
            .size(with(density) { (w * pxPerPt).toDp() }, with(density) { (h * pxPerPt).toDp() })
            .graphicsLayer { rotationZ = obj.degrees } // about the centre, clockwise, like the engine
            .drawBehind {
                drawRect(WordBlue, style = Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f))))
            }
            .then(
                if (floating) Modifier.pointerInput(obj) {
                    detectDragGestures(onDragEnd = { commit() }) { c, d ->
                        c.consume()
                        val m = toPage(d.x / pxPerPt, d.y / pxPerPt)
                        cx += m.x; cy += m.y
                    }
                } else Modifier,
            ),
    ) {
        // (column, row) in {0, 1, 2}: 0 = low edge, 2 = high edge, 1 = middle.
        listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 2 to 1, 0 to 2, 1 to 2, 2 to 2).forEach { (gx, gy) ->
            Box(
                Modifier
                    .align(
                        when {
                            gx == 0 && gy == 0 -> Alignment.TopStart; gx == 1 && gy == 0 -> Alignment.TopCenter; gx == 2 && gy == 0 -> Alignment.TopEnd
                            gx == 0 && gy == 1 -> Alignment.CenterStart; gx == 2 && gy == 1 -> Alignment.CenterEnd
                            gx == 0 && gy == 2 -> Alignment.BottomStart; gx == 1 && gy == 2 -> Alignment.BottomCenter
                            else -> Alignment.BottomEnd
                        },
                    )
                    .offset { IntOffset(((gx - 1) * handle / 2f).roundToInt(), ((gy - 1) * handle / 2f).roundToInt()) }
                    .size(28.dp)
                    .pointerInput(obj, gx, gy) {
                        detectDragGestures(onDragEnd = { commit() }) { c, d ->
                            c.consume()
                            // Drag in the object's own axes (the handle sits inside the turned frame).
                            val dx = d.x / pxPerPt
                            val dy = d.y / pxPerPt
                            var dw = if (gx == 2) dx else if (gx == 0) -dx else 0f
                            var dh = if (gy == 2) dy else if (gy == 0) -dy else 0f
                            val corner = gx != 1 && gy != 1
                            var nw = max(MIN_PT, w + dw)
                            var nh = max(MIN_PT, h + dh)
                            if (corner) nh = nw / aspect // keep the proportions
                            if (nh < MIN_PT) { nh = MIN_PT; if (corner) nw = nh * aspect }
                            dw = nw - w
                            dh = nh - h
                            // The dragged edges move by dw / dh; the centre moves half as far, toward them.
                            val shift = toPage(
                                if (gx == 2) dw / 2f else if (gx == 0) -dw / 2f else 0f,
                                if (gy == 2) dh / 2f else if (gy == 0) -dh / 2f else 0f,
                            )
                            cx += shift.x; cy += shift.y; w = nw; h = nh
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(12.dp).background(Color.White, CircleShape).border(2.dp, WordBlue, CircleShape))
            }
        }
    }
}

/**
 * Grips on the borders of the table under the caret: drag a column border sideways, a row border
 * down, or the corner to scale the whole table. The engine is asked once, on release, with the
 * drag in page points, so each drag is one undo step.
 */
@Composable
fun TableHandles(
    geo: com.willkez.wordcraft.bridge.TableGeo,
    pxPerPt: Float,
    onDrag: (x: Float, y: Float, toX: Float, toY: Float) -> Unit,
    onSelect: (kind: String, x: Float, y: Float) -> Unit = { _, _, _ -> },
) {
    val density = LocalDensity.current
    val midY = (geo.top + geo.bottom) / 2f
    val midX = (geo.cols.firstOrNull() ?: 0f).let { l -> (l + (geo.cols.lastOrNull() ?: l)) / 2f }

    // Column borders (including the table's outer edges): grips above the table, a guide line while dragging.
    geo.cols.forEachIndexed { i, cx ->
        key(geo, i) {
            var d by remember { mutableFloatStateOf(0f) }
            Box(
                Modifier
                    .offset { IntOffset(((cx + d) * pxPerPt - with(density) { 14.dp.toPx() }).roundToInt(), (geo.top * pxPerPt - with(density) { 50.dp.toPx() }).roundToInt()) }
                    .size(28.dp, 24.dp)
                    .pointerInput(geo, i) {
                        detectDragGestures(onDragEnd = { val moved = d; d = 0f; if (moved != 0f) onDrag(cx, midY, cx + moved, midY) }, onDragCancel = { d = 0f }) { c, drag ->
                            c.consume(); d += drag.x / pxPerPt
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 14.dp, height = 18.dp).background(WordBlue, androidx.compose.foundation.shape.RoundedCornerShape(5.dp)))
            }
            if (d != 0f) {
                Box(
                    Modifier.offset { IntOffset(((cx + d) * pxPerPt).roundToInt(), (geo.top * pxPerPt).roundToInt()) }
                        .size(2.dp, with(density) { ((geo.bottom - geo.top) * pxPerPt).toDp() })
                        .background(WordBlue),
                )
            }
        }
    }

    // Row borders: grips on the table's left edge, outside the text.
    geo.rowBottoms.forEachIndexed { i, by ->
        key(geo, "r$i") {
            var d by remember { mutableFloatStateOf(0f) }
            val left = geo.cols.firstOrNull() ?: 0f
            Box(
                Modifier
                    .offset { IntOffset((left * pxPerPt - with(density) { 46.dp.toPx() }).roundToInt().coerceAtLeast(0), ((by + d) * pxPerPt - with(density) { 12.dp.toPx() }).roundToInt()) }
                    .size(24.dp, 24.dp)
                    .pointerInput(geo, i) {
                        detectDragGestures(onDragEnd = { val moved = d; d = 0f; if (moved != 0f) onDrag(midX, by, midX, by + moved) }, onDragCancel = { d = 0f }) { c, drag ->
                            c.consume(); d += drag.y / pxPerPt
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 16.dp, height = 8.dp).background(WordBlue, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
            }
        }
    }

    // Selecting: tap a bar above a column or beside a row to select it, or the handle at the top-left to select the whole table.
    val left0 = geo.cols.firstOrNull() ?: 0f
    val firstRowBottom = geo.rowBottoms.firstOrNull() ?: geo.bottom
    val firstCellX = if (geo.cols.size > 1) (geo.cols[0] + geo.cols[1]) / 2f else left0 + 6f
    val bar = WordBlue.copy(alpha = 0.30f)
    for (i in 0 until (geo.cols.size - 1).coerceAtLeast(0)) {
        val x0 = geo.cols[i]; val x1 = geo.cols[i + 1]
        key(geo, "sc$i") {
            Box(
                Modifier
                    .offset { IntOffset((x0 * pxPerPt + with(density) { 2.dp.toPx() }).roundToInt(), (geo.top * pxPerPt - with(density) { 15.dp.toPx() }).roundToInt()) }
                    .size(with(density) { ((x1 - x0) * pxPerPt).toDp() - 4.dp }.coerceAtLeast(6.dp), 13.dp)
                    .clip(RoundedCornerShape(3.dp)).background(bar)
                    .pointerInput(geo, i) { detectTapGestures(onTap = { onSelect("column", (x0 + x1) / 2f, (geo.top + firstRowBottom) / 2f) }) },
            )
        }
    }
    geo.rowBottoms.forEachIndexed { j, rowBottom ->
        val rowTop = if (j == 0) geo.top else geo.rowBottoms[j - 1]
        key(geo, "sr$j") {
            Box(
                Modifier
                    .offset { IntOffset((left0 * pxPerPt - with(density) { 15.dp.toPx() }).roundToInt().coerceAtLeast(0), (rowTop * pxPerPt + with(density) { 2.dp.toPx() }).roundToInt()) }
                    .size(13.dp, with(density) { ((rowBottom - rowTop) * pxPerPt).toDp() - 4.dp }.coerceAtLeast(6.dp))
                    .clip(RoundedCornerShape(3.dp)).background(bar)
                    .pointerInput(geo, j) { detectTapGestures(onTap = { onSelect("row", firstCellX, (rowTop + rowBottom) / 2f) }) },
            )
        }
    }
    key(geo, "selTable") {
        Box(
            Modifier
                .offset { IntOffset((left0 * pxPerPt - with(density) { 32.dp.toPx() }).roundToInt().coerceAtLeast(0), (geo.top * pxPerPt - with(density) { 30.dp.toPx() }).roundToInt()) }
                .size(26.dp).clip(RoundedCornerShape(5.dp)).background(WordBlue)
                .pointerInput(geo) { detectTapGestures(onTap = { onSelect("table", firstCellX, (geo.top + firstRowBottom) / 2f) }) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.OpenWith, "Select table", Modifier.size(16.dp), tint = Color.White) }
    }

    // Corner: scales the whole table. It belongs to the piece that holds the table's last row (the only place the engine takes it).
    if (geo.endsHere) key(geo, "corner") {
        var dx by remember { mutableFloatStateOf(0f) }
        var dy by remember { mutableFloatStateOf(0f) }
        val right = geo.cols.lastOrNull() ?: 0f
        Box(
            Modifier
                .offset { IntOffset(((right + dx) * pxPerPt).roundToInt(), ((geo.bottom + dy) * pxPerPt).roundToInt()) }
                .size(26.dp)
                .pointerInput(geo) {
                    detectDragGestures(
                        onDragEnd = { val a = dx; val b = dy; dx = 0f; dy = 0f; if (a != 0f || b != 0f) onDrag(right + 3f, geo.bottom + 3f, right + 3f + a, geo.bottom + 3f + b) },
                        onDragCancel = { dx = 0f; dy = 0f },
                    ) { c, drag -> c.consume(); dx += drag.x / pxPerPt; dy += drag.y / pxPerPt }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(14.dp).background(Color.White, androidx.compose.foundation.shape.CircleShape).border(2.dp, WordBlue, androidx.compose.foundation.shape.CircleShape))
        }
    }
}
