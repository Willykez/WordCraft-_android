package org.wordcraft.android.editor

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt
import org.wordcraft.bridge.CaretRect
import org.wordcraft.bridge.PageInfo
import org.wordcraft.bridge.SelRect

private val PaperGrey = Color(0xFFE3E5E8)
private val SelectionBlue = Color(0x553D7BFF)
private val HandleBlue = Color(0xFF2F6BFF)

/**
 * Continuous-scroll document view. Each page is a bitmap the Rust engine rendered from its own
 * pagination; selection, caret and handles are drawn on top in page points, so they stay crisp at
 * any zoom and never need a re-render.
 *
 * Zoom 1.0 fits the widest page to the screen width. Pinch (two fingers) changes it; one finger
 * keeps scrolling the list.
 */
@Composable
fun PageCanvas(
    vm: EditorViewModel,
    pages: List<PageInfo>,
    caret: CaretRect?,
    selection: List<SelRect>,
    revision: ULong,
    zoom: Float,
    onZoom: (Float) -> Unit,
    onTapped: () -> Unit,
    modifier: Modifier = Modifier,
    marks: Boolean = false,
    obj: org.wordcraft.bridge.ObjRect? = null,
) {
    val list = rememberLazyListState()
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnZoom by rememberUpdatedState(onZoom)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(PaperGrey)
            // Two-finger pinch. Watched in the Initial pass and consumed only with 2+ pointers down.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var lastSpan = 0f
                    do {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val down = ev.changes.filter { it.pressed }
                        if (down.size >= 2) {
                            val span = (down[0].position - down[1].position).getDistance()
                            if (lastSpan > 0f) currentOnZoom(currentZoom * span / lastSpan)
                            lastSpan = span
                            ev.changes.forEach { it.consume() }
                        } else lastSpan = 0f
                    } while (ev.changes.any { it.pressed })
                }
            },
    ) {
        // Cast to Float to avoid Double vs Float resolution ambiguity from UniFFI properties
        val maxWidthPt = max(1f, pages.maxOfOrNull { it.widthPt.toFloat() } ?: 595f)
        
        // dp per point so that zoom 1.0 fills the width with an 8dp gutter.
        val dpPerPt = (maxWidth.value - 16f) / maxWidthPt * zoom
        val horizontal = rememberScrollState()

        // Keep the caret on screen as it moves: typing at the bottom, Enter, arrow keys, Find.
        val pxPerPtNow = with(LocalDensity.current) { dpPerPt.dp.toPx() }
        val edge = with(LocalDensity.current) { 72.dp.toPx() } // clear of the mini toolbar and keyboard edge
        LaunchedEffect(caret?.page, caret?.y, caret?.height) {
            val c = caret ?: return@LaunchedEffect
            val p = c.page.toInt()
            if (list.layoutInfo.visibleItemsInfo.none { it.index == p }) list.scrollToItem(p)
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == p } ?: return@LaunchedEffect
            val top = item.offset + c.y.toFloat() * pxPerPtNow
            val bottom = top + c.height.toFloat() * pxPerPtNow
            val viewStart = list.layoutInfo.viewportStartOffset + edge
            val viewEnd = list.layoutInfo.viewportEndOffset - edge
            val delta = when {
                top < viewStart -> top - viewStart
                bottom > viewEnd -> bottom - viewEnd
                else -> 0f
            }
            if (delta != 0f) list.animateScrollBy(delta)
        }

        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().then(if (zoom > 1.02f) Modifier.horizontalScroll(horizontal) else Modifier),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(pages, key = { i, _ -> i }) { index, page ->
                PageItem(
                    vm = vm, index = index, page = page, dpPerPt = dpPerPt, revision = revision, marks = marks, obj = obj?.takeIf { it.page.toInt() == index },
                    caret = caret?.takeIf { it.page.toInt() == index },
                    selection = selection.filter { it.page.toInt() == index },
                    allSelection = selection,
                    onTapped = onTapped,
                )
            }
        }
    }
}

@Composable
private fun PageItem(
    vm: EditorViewModel,
    index: Int,
    page: PageInfo,
    dpPerPt: Float,
    revision: ULong,
    marks: Boolean,
    obj: org.wordcraft.bridge.ObjRect?,
    caret: CaretRect?,
    selection: List<SelRect>,
    allSelection: List<SelRect>,
    onTapped: () -> Unit,
) {
    val density = LocalDensity.current
    val pxPerPt = with(density) { dpPerPt.dp.toPx() }
    
    // Explicit Float conversions to prevent Kotlin extension ambiguity 
    val widthDp = (page.widthPt.toFloat() * dpPerPt).dp
    val heightDp = (page.heightPt.toFloat() * dpPerPt).dp

    // Raster at the on-screen size, capped so a pinched-in page stays within memory.
    val rasterScale = (pxPerPt).coerceIn(0.5f, 3.2f)
    var bitmap by remember(index) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    
    // Keep showing the previous bitmap until the new one is ready, so typing doesn't flicker.
    LaunchedEffect(revision, marks, index, (rasterScale * 20).roundToInt()) {
        vm.render(index, rasterScale)?.let { bitmap = it.asImageBitmap() }
    }

    var lastDouble by remember { mutableLongStateOf(0L) }
    val blink = rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "caretAlpha",
    )

    Box(
        Modifier
            .size(widthDp, heightDp)
            .shadow(4.dp, RectangleShape)
            .background(Color.White)
            .pointerInput(index, pxPerPt) {
                detectTapGestures(
                    // A third quick tap right after a double tap selects the whole paragraph.
                    onTap = { o ->
                        if (System.currentTimeMillis() - lastDouble < 900) vm.selectParagraph(index, o.x / pxPerPt, o.y / pxPerPt)
                        else vm.tap(index, o.x / pxPerPt, o.y / pxPerPt)
                        onTapped()
                    },
                    onDoubleTap = { o -> lastDouble = System.currentTimeMillis(); vm.selectWord(index, o.x / pxPerPt, o.y / pxPerPt); onTapped() },
                )
            }
            // Long-press selects a word, then dragging extends the selection from it.
            .pointerInput(index, pxPerPt) {
                var anchor = Offset.Zero
                detectDragGesturesAfterLongPress(
                    onDragStart = { o -> anchor = o; vm.selectWord(index, o.x / pxPerPt, o.y / pxPerPt); onTapped() },
                    onDrag = { change, _ ->
                        val p = change.position
                        vm.selectBetween(index, anchor.x / pxPerPt, anchor.y / pxPerPt, index, p.x / pxPerPt, p.y / pxPerPt)
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            bitmap?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())) }
            selection.forEach {
                drawRect(
                    SelectionBlue, 
                    Offset(it.x.toFloat() * pxPerPt, it.y.toFloat() * pxPerPt), 
                    Size(it.w.toFloat() * pxPerPt, it.h.toFloat() * pxPerPt)
                )
            }
            if (selection.isEmpty() && caret != null) {
                drawRect(
                    Color.Black.copy(alpha = blink.value), 
                    Offset(caret.x.toFloat() * pxPerPt - 1f, caret.y.toFloat() * pxPerPt), 
                    Size(2.2f, caret.height.toFloat() * pxPerPt)
                )
            }
        }
        if (obj != null) {
            ObjectHandles(obj, pxPerPt) { x, y, w, h, moved -> vm.resizeObject(index, x, y, w, h, moved) }
        } else if (allSelection.isNotEmpty()) SelectionHandles(vm, index, pxPerPt, allSelection)
    }
}

/** Teardrop-style handles at both ends of the selection; dragging either one moves that end. */
@Composable
private fun SelectionHandles(vm: EditorViewModel, pageIndex: Int, pxPerPt: Float, all: List<SelRect>) {
    val first = all.first()
    val last = all.last()
    val handle = 22.dp
    val density = LocalDensity.current
    val handlePx = with(density) { handle.toPx() }

    // Start handle sits under the selection's first line start; end handle under its last line end.
    if (first.page.toInt() == pageIndex) {
        Handle(first.x.toFloat() * pxPerPt, (first.y.toFloat() + first.h.toFloat()) * pxPerPt, handlePx) { dx, dy ->
            val nx = first.x.toFloat() + dx / pxPerPt
            val ny = first.y.toFloat() + first.h.toFloat() / 2f + dy / pxPerPt
            vm.selectBetween(pageIndex, nx, ny, last.page.toInt(), last.x.toFloat() + last.w.toFloat() - 0.5f, last.y.toFloat() + last.h.toFloat() / 2f)
        }
    }
    if (last.page.toInt() == pageIndex) {
        Handle((last.x.toFloat() + last.w.toFloat()) * pxPerPt, (last.y.toFloat() + last.h.toFloat()) * pxPerPt, handlePx) { dx, dy ->
            val nx = last.x.toFloat() + last.w.toFloat() + dx / pxPerPt
            val ny = last.y.toFloat() + last.h.toFloat() / 2f + dy / pxPerPt
            vm.selectBetween(first.page.toInt(), first.x.toFloat() + 0.5f, first.y.toFloat() + first.h.toFloat() / 2f, pageIndex, nx, ny)
        }
    }
}

@Composable
private fun Handle(xPx: Float, yPx: Float, sizePx: Float, onDragTotal: (Float, Float) -> Unit) {
    var total by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset((xPx - sizePx / 2).roundToInt(), yPx.roundToInt()) }
            .size(with(density) { sizePx.toDp() })
            .pointerInput(xPx, yPx) {
                detectDragGestures(
                    onDragStart = { total = Offset.Zero },
                    onDrag = { c, d -> c.consume(); total += d; onDragTotal(total.x, total.y) },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(HandleBlue, radius = size.minDimension / 2.6f, center = Offset(size.width / 2, size.height / 2))
        }
    }
}
