package com.willkez.wordcraft.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private val GlowColours = listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853))

/** A band of colour that flows sideways, looping seamlessly: the first colour is repeated at the end. */
private fun flowing(width: Float, shift: Float) =
    Brush.horizontalGradient(GlowColours + GlowColours.first(), startX = shift * width, endX = shift * width + width, tileMode = TileMode.Repeated)

/** Fills what it is drawn behind with the moving colours (the status bar, while the assistant writes). */
@Composable
fun Modifier.aiShimmer(): Modifier {
    val t = rememberInfiniteTransition(label = "aiShimmer")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "shift")
    return drawBehind { drawRect(flowing(size.width, shift), alpha = 0.85f) }
}

/** A soft glow that rises from the bottom edge, in the same colours, while the assistant writes. It takes no touches. */
@Composable
fun AiGlowEdge(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "aiGlow")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "shift")
    val pulse by t.animateFloat(0.5f, 0.95f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Box(
        modifier.fillMaxWidth().height(72.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawRect(flowing(size.width, shift), alpha = pulse)
                // Fade to nothing towards the top, so it reads as light spilling up from the bar.
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)), blendMode = BlendMode.DstIn)
            },
    )
}
