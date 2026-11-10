package com.wordcraft.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val SENTINEL = "\u200B"
private const val MAX_ZOOM = 2.5f
private const val MIN_ZOOM = 0.6f

/**
 * The document surface. Every page is rasterised by the WordCraft engine (layout + renderer);
 * Kotlin only displays the bitmaps, forwards touches/typing to the engine, and draws the caret
 * and selection overlay from the engine's own caret geometry.
 */
@Composable
fun DocumentCanvas(state: EditorState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val hScroll = rememberScrollState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { state.refresh() }
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }.collect { state.visiblePage = it }
    }

    BoxWithConstraints(
        modifier
            .background(Color(0xFFD9DEE7))
            .pointerInput(Unit) {
                // Two-finger pinch only; single-finger drags still reach the list's scrolling.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.size >= 2) {
                            val change = event.calculateZoom()
                            if (change != 1f) {
                                state.zoom = (state.zoom * change).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val viewportPx = with(density) { maxWidth.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }
        val firstWidth = state.pages.firstOrNull()?.w ?: 595f
        // Pixels per point: fit the first page to the screen at zoom 1, then apply zoom.
        val pxPerPoint = ((viewportPx - 2 * marginPx) / firstWidth) * state.zoom
        val widestPx = (state.pages.maxOfOrNull { it.w } ?: firstWidth) * pxPerPoint
        val contentWidthDp = with(density) { max(viewportPx, widestPx + 2 * marginPx).toDp() }

        Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
            LaunchedEffect(state.scrollTarget) {
                state.scrollTarget?.let { t ->
                    val index = t.page.coerceIn(0, (state.pages.size - 1).coerceAtLeast(0))
                    listState.animateScrollToItem(index, (t.yPoints * pxPerPoint).toInt())
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.width(contentWidthDp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
            ) {
                itemsIndexed(state.pages) { index, info ->
                    PageView(
                        state = state, index = index, info = info, pxPerPoint = pxPerPoint,
                        onTouched = {
                            focusRequester.requestFocus()
                            keyboard?.show()
                        }
                    )
                }
            }
        }

        ImeCapture(state, focusRequester, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun PageView(
    state: EditorState,
    index: Int,
    info: PageInfo,
    pxPerPoint: Float,
    onTouched: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var image by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    // Quantise the scale so pinch-zooming doesn't re-render on every tiny change.
    val renderScale = (pxPerPoint * 20f).roundToInt().coerceAtLeast(1) / 20f
    LaunchedEffect(index, state.revision, renderScale) {
        state.renderPage(index, renderScale)?.let { image = it.asImageBitmap() }
    }

    val widthDp = with(density) { (info.w * pxPerPoint).toDp() }
    val heightDp = with(density) { (info.h * pxPerPoint).toDp() }

    Box(
        Modifier
            .size(widthDp, heightDp)
            .shadow(3.dp, RoundedCornerShape(2.dp))
            .background(Color.White)
            .pointerInput(index, pxPerPoint) {
                detectTapGestures(onTap = { offset ->
                    scope.launch { state.hit(index, offset.x / pxPerPoint, offset.y / pxPerPoint, false) }
                    onTouched()
                })
            }
            .pointerInput(index, pxPerPoint) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        scope.launch {
                            state.hit(index, offset.x / pxPerPoint, offset.y / pxPerPoint, false)
                            state.command("select.word", edits = false)
                        }
                        onTouched()
                    },
                    onDrag = { change, _ ->
                        val p = change.position
                        scope.launch { state.hit(index, p.x / pxPerPoint, p.y / pxPerPoint, true) }
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            image?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())) }
            drawSelection(state, index, info, pxPerPoint)
            drawCaret(state, index, pxPerPoint)
        }
    }
}

private fun DrawScope.drawCaret(state: EditorState, index: Int, ppp: Float) {
    val caret = state.focus ?: return
    if (caret.page != index) return
    drawRect(
        color = Color(0xFF1B1B1B),
        topLeft = Offset(caret.x * ppp, caret.top * ppp),
        size = Size(2f.coerceAtLeast(1.5f * ppp), caret.height * ppp)
    )
}

/**
 * Approximate selection highlight built from the engine's caret geometry (first line from the
 * anchor to the right margin, whole lines in between, last line up to the focus). Exact
 * per-line rectangles and table-cell selection need a dedicated engine query (next milestone).
 */
private fun DrawScope.drawSelection(state: EditorState, index: Int, info: PageInfo, ppp: Float) {
    val a = state.anchor ?: return
    val f = state.focus ?: return
    if (state.collapsed) return
    val (first, last) = if (a.page < f.page || (a.page == f.page && (a.top < f.top || (abs(a.top - f.top) < 1f && a.x <= f.x)))) a to f else f to a
    if (index < first.page || index > last.page) return
    val color = Color(0x553D7BFF)
    val left = info.bx
    val right = info.bx + info.bw
    val startsHere = index == first.page
    val endsHere = index == last.page
    fun fill(x: Float, y: Float, w: Float, h: Float) {
        if (w > 0f && h > 0f) drawRect(color, Offset(x * ppp, y * ppp), Size(w * ppp, h * ppp))
    }
    if (startsHere && endsHere && abs(first.top - last.top) < 1f) {
        fill(first.x, first.top, last.x - first.x, first.height)
        return
    }
    val middleTop = if (startsHere) {
        fill(first.x, first.top, right - first.x, first.height)
        first.top + first.height
    } else info.by
    val middleBottom = if (endsHere) {
        fill(left, last.top, last.x - left, last.height)
        last.top
    } else info.by + info.bh
    fill(left, middleTop, right - left, middleBottom - middleTop)
}

/**
 * Invisible text field that only exists to bring up the Android keyboard. Every change is
 * diffed against a one-character sentinel and forwarded to the engine as text.insert /
 * text.backspace, then the field is reset. The field uses a password-type input so keyboards
 * send committed characters rather than composing regions.
 * Limitation: no composing text, predictive suggestions or surrounding-text IME protocol yet.
 */
@Composable
private fun ImeCapture(state: EditorState, focusRequester: FocusRequester, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf(TextFieldValue(SENTINEL, TextRange(SENTINEL.length))) }
    BasicTextField(
        value = value,
        onValueChange = { next ->
            val text = next.text
            when {
                text.length < SENTINEL.length -> scope.launch { state.backspace() }
                text.length > SENTINEL.length -> {
                    val added = text.removePrefix(SENTINEL).ifEmpty { text.drop(SENTINEL.length) }
                    if (added.isNotEmpty()) scope.launch { state.typeText(added) }
                }
            }
            value = TextFieldValue(SENTINEL, TextRange(SENTINEL.length))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = modifier.size(1.dp).alpha(0f).focusRequester(focusRequester).padding(0.dp)
    )
}
