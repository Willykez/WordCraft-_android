package org.wordcraft.android.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.wordcraft.android.ui.WordBlue
import org.wordcraft.bridge.ObjRect
import kotlin.math.max
import kotlin.math.roundToInt

private const val MIN_PT = 12f

/**
 * Selection frame with eight resize handles over a selected picture or shape, as in Word. Corners keep
 * the proportions; edges stretch one side. Floating objects (any wrap but inline) can also be dragged.
 * The frame follows the finger and the engine is asked once, on release.
 */
@Composable
fun ObjectHandles(
    obj: ObjRect,
    pxPerPt: Float,
    onCommit: (x: Float, y: Float, w: Float, h: Float, moved: Boolean) -> Unit,
) {
    var x by remember(obj) { mutableFloatStateOf(obj.x) }
    var y by remember(obj) { mutableFloatStateOf(obj.y) }
    var w by remember(obj) { mutableFloatStateOf(obj.w) }
    var h by remember(obj) { mutableFloatStateOf(obj.h) }
    var moved by remember(obj) { mutableStateOf(false) }
    val floating = obj.wrap != "inline" && obj.wrap.isNotEmpty()
    val aspect = if (obj.h > 0f) obj.w / obj.h else 1f
    val density = LocalDensity.current
    val handle = with(density) { 28.dp.toPx() }

    fun commit() { onCommit(x, y, w, h, moved) }

    Box(
        Modifier
            .offset { IntOffset((x * pxPerPt).roundToInt(), (y * pxPerPt).roundToInt()) }
            .size(with(density) { (w * pxPerPt).toDp() }, with(density) { (h * pxPerPt).toDp() })
            .drawBehind {
                drawRect(WordBlue, style = Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f))))
            }
            .then(
                if (floating) Modifier.pointerInput(obj) {
                    detectDragGestures(onDragEnd = { commit() }) { c, d ->
                        c.consume(); x += d.x / pxPerPt; y += d.y / pxPerPt; moved = true
                    }
                } else Modifier,
            ),
    ) {
        // (column, row) in {0, 1, 2}: 0 = low edge, 2 = high edge, 1 = middle.
        listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 2 to 1, 0 to 2, 1 to 2, 2 to 2).forEach { (cx, cy) ->
            Box(
                Modifier
                    .align(
                        when {
                            cx == 0 && cy == 0 -> Alignment.TopStart; cx == 1 && cy == 0 -> Alignment.TopCenter; cx == 2 && cy == 0 -> Alignment.TopEnd
                            cx == 0 && cy == 1 -> Alignment.CenterStart; cx == 2 && cy == 1 -> Alignment.CenterEnd
                            cx == 0 && cy == 2 -> Alignment.BottomStart; cx == 1 && cy == 2 -> Alignment.BottomCenter
                            else -> Alignment.BottomEnd
                        },
                    )
                    .offset { IntOffset(((cx - 1) * handle / 2f).roundToInt(), ((cy - 1) * handle / 2f).roundToInt()) }
                    .size(28.dp)
                    .pointerInput(obj, cx, cy) {
                        detectDragGestures(onDragEnd = { commit() }) { c, d ->
                            c.consume()
                            val dx = d.x / pxPerPt; val dy = d.y / pxPerPt
                            var nx = x; var ny = y; var nw = w; var nh = h
                            if (cx == 2) nw += dx else if (cx == 0) { nx += dx; nw -= dx }
                            if (cy == 2) nh += dy else if (cy == 0) { ny += dy; nh -= dy }
                            if (cx != 1 && cy != 1) { // corner: keep the proportions, driven by the wider change
                                nh = nw / aspect
                                if (cy == 0) ny = y + h - nh
                            }
                            nw = max(MIN_PT, nw); nh = max(MIN_PT, nh)
                            // Only a left/top drag moves the origin, and then only for floating objects.
                            if (cx == 0) nx = x + w - nw
                            if (cy == 0) ny = y + h - nh
                            if (floating && (cx == 0 || cy == 0)) moved = true
                            x = nx; y = ny; w = nw; h = nh
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(12.dp).background(Color.White, CircleShape).border(2.dp, WordBlue, CircleShape))
            }
        }
    }
}
