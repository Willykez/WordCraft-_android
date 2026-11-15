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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.pointer.PointerType
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
@OptIn(ExperimentalFoundationApi::class)
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
    tableGeo: org.wordcraft.bridge.TableGeo? = null,
    ruler: org.wordcraft.bridge.RulerInfo? = null,
    draw: DrawSettings? = null,
    scrollToPage: Int? = null,
    scrollTick: Int = 0,
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
        LaunchedEffect(scrollTick) { scrollToPage?.let { list.animateScrollToItem(it.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) } }

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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = if (ruler != null) 36.dp else 12.dp, bottom = 8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(pages, key = { i, _ -> i }) { index, page ->
                PageItem(
                    vm = vm, index = index, page = page, dpPerPt = dpPerPt, revision = revision, marks = marks, obj = obj?.takeIf { it.page.toInt() == index }, tableGeo = tableGeo?.takeIf { it.page.toInt() == index }, draw = draw,
                    caret = caret?.takeIf { it.page.toInt() == index },
                    selection = selection.filter { it.page.toInt() == index },
                    allSelection = selection,
                    onTapped = onTapped,
                )
            }
        }

        // The ruler follows the caret's page: its left edge tracks the page as it is centred or scrolled sideways.
        if (ruler != null) {
            val pageW = (pages.getOrNull(ruler.page.toInt())?.widthPt?.toFloat() ?: ruler.pageW) * pxPerPtNow
            val wide = zoom > 1.02f
            val columnW = if (wide) maxWidthPt * pxPerPtNow else constraints.maxWidth.toFloat()
            val pageLeft = (columnW - pageW) / 2f - (if (wide) horizontal.value.toFloat() else 0f)
            RulerBar(
                ruler, pageLeft, pxPerPtNow,
                onIndents = { l, r, f -> vm.setIndents(l, r, f) },
                onTab = { clear, pos, kind -> vm.setTabStop(clear, pos, kind) },
                modifier = Modifier.align(Alignment.TopStart),
            )
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
    tableGeo: org.wordcraft.bridge.TableGeo?,
    draw: DrawSettings?,
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
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var committed by remember { mutableStateOf(false) }
    
    // Keep showing the previous bitmap until the new one is ready, so typing doesn't flicker.
    LaunchedEffect(revision, marks, index, (rasterScale * 20).roundToInt()) {
        vm.render(index, rasterScale)?.let { bitmap = it.asImageBitmap() }
        if (committed) { live = emptyList(); committed = false } // the new stroke is in the page picture now
    }
    // Safety net: never leave a drawn stroke hanging if the page picture is slow.
    LaunchedEffect(committed) { if (committed) { kotlinx.coroutines.delay(2500); live = emptyList(); committed = false } }

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
            .then(
                if (draw == null) Modifier
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
                    }
                else Modifier.pointerInput(index, pxPerPt, draw) {
                    // Ink: the finger or stylus draws instead of scrolling or placing the caret.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (draw.stylusOnly && down.type != PointerType.Stylus) return@awaitEachGesture
                        down.consume()
                        val pts = ArrayList<Offset>().apply { add(down.position) }
                        live = pts.toList()
                        var lastErase = down.position
                        if (draw.tool == "eraser") vm.eraseInk(index, down.position.x / pxPerPt, down.position.y / pxPerPt)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            for (at in ch.historical.map { it.position } + ch.position) {
                                val prev = pts.last()
                                if ((at - prev).getDistance() >= 1.5f) pts.add(at)
                                if (draw.tool == "eraser" && (at - lastErase).getDistance() >= 8f) {
                                    lastErase = at; vm.eraseInk(index, at.x / pxPerPt, at.y / pxPerPt)
                                }
                            }
                            live = if (draw.tool == "eraser") listOf(ch.position) else pts.toList()
                            ch.consume()
                            if (!ch.pressed) break
                        }
                        val all = pts.map { it.x / pxPerPt to it.y / pxPerPt }
                        val step = if (all.size > 4000) all.size / 4000 + 1 else 1
                        val sampled = all.filterIndexed { i, _ -> i % step == 0 || i == all.lastIndex }
                        when (draw.tool) {
                            "eraser" -> live = emptyList()
                            "lasso" -> { if (sampled.size >= 3) vm.lassoInk(index, sampled); live = emptyList() }
                            else -> {
                                val line = if (sampled.size == 1) listOf(sampled[0], sampled[0].first + 0.1f to sampled[0].second) else sampled
                                vm.drawStroke(index, line, draw.tool, draw.colorHex, draw.widthPt)
                                committed = true
                            }
                        }
                    }
                },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            bitmap?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())) }
            // A selected picture/shape gets its own turned frame; the engine's flat selection box would be wrong for it.
            if (obj == null) selection.forEach {
                drawRect(
                    SelectionBlue, 
                    Offset(it.x.toFloat() * pxPerPt, it.y.toFloat() * pxPerPt), 
                    Size(it.w.toFloat() * pxPerPt, it.h.toFloat() * pxPerPt)
                )
            }
            if (draw != null && live.isNotEmpty()) {
                val color = Color(draw.color)
                when (draw.tool) {
                    "eraser" -> {
                        drawCircle(Color(0x33000000), 14.dp.toPx(), live.last())
                        drawCircle(Color(0x99000000), 14.dp.toPx(), live.last(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                    }
                    "lasso" -> drawPath(
                        androidx.compose.ui.graphics.Path().apply { moveTo(live[0].x, live[0].y); live.drop(1).forEach { lineTo(it.x, it.y) } },
                        Color(0xFF2B579A),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            2.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 9f)),
                        ),
                    )
                    else -> {
                        val w = draw.widthPt * pxPerPt
                        val c = if (draw.tool == "highlighter") color.copy(alpha = 0.4f) else color
                        if (live.size < 2) drawCircle(c, w / 2f, live[0])
                        else drawPath(
                            androidx.compose.ui.graphics.Path().apply { moveTo(live[0].x, live[0].y); live.drop(1).forEach { lineTo(it.x, it.y) } },
                            c,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                w, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round,
                            ),
                        )
                    }
                }
            }
            if (selection.isEmpty() && caret != null && draw == null) {
                drawRect(
                    Color.Black.copy(alpha = blink.value), 
                    Offset(caret.x.toFloat() * pxPerPt - 1f, caret.y.toFloat() * pxPerPt), 
                    Size(2.2f, caret.height.toFloat() * pxPerPt)
                )
            }
        }
        if (tableGeo != null && obj == null) TableHandles(tableGeo, pxPerPt) { x, y, tx, ty -> vm.dragTableBorder(index, x, y, tx, ty) }
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

/** The active Draw tool as the page canvas needs it. */
class DrawSettings(val tool: String, val colorHex: String, widthPt: Float, val stylusOnly: Boolean) {
    val color: Long = 0xFF000000 or (colorHex.toLongOrNull(16) ?: 0L)
    /** A highlighter is a broad marker: always at least 10 pt. */
    val widthPt: Float = if (tool == "highlighter") maxOf(widthPt * 3f, 10f) else widthPt
}
