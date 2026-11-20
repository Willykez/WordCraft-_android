package com.willkez.wordcraft.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.willkez.wordcraft.editor.EditorUiState
import com.willkez.wordcraft.editor.EditorViewModel

internal val INK_COLORS = listOf(
    "000000", "404040", "808080", "C00000", "FF0000", "ED7D31", "FF9900", "FFC000", "FFFF00", "92D050",
    "548235", "00B050", "00B0F0", "185ABD", "0000FF", "7030A0", "FF00FF", "FF6699", "8B4513", "FFFFFF",
)
internal val INK_WIDTHS = listOf(1f, 2.5f, 4f, 6f, 10f)

private fun ink(hex: String) = Color(0xFF000000 or hex.toLong(16))

/** The current pen colour when it isn't one of the swatches, and a rainbow button for any other colour. */
@Composable
private fun InkMore(current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (INK_COLORS.none { it.equals(current, true) }) InkSwatch(current, true) {}
    Box(
        Modifier.size(28.dp).background(
            Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)), CircleShape,
        ).border(1.dp, Color(0xFFBBBBBB), CircleShape).clickable { open = true },
        contentAlignment = Alignment.Center,
    ) { Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    if (open) CustomColorDialog(current, { open = false }) { open = false; onPick(it) }
}

@Composable
private fun InkSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).background(ink(hex), CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) WordBlue else Color(0xFFBBBBBB), CircleShape).clickable(onClick = onClick),
    )
}

@Composable
private fun WidthDot(w: Float, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(if (selected) Color(0xFFDCE8FA) else Color(0xFFF2F4F7)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(width = 20.dp, height = (w.coerceAtMost(10f) + 1f).dp).background(Color(0xFF242424), RoundedCornerShape(4.dp))) }
}

/** The bar shown while a Draw tool is active: tool, thickness, colour, and Done. */
@Composable
fun DrawBar(ui: EditorUiState, vm: EditorViewModel, modifier: Modifier = Modifier) {
    val tool = ui.drawTool ?: return
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 10.dp, tonalElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(
                    Triple("pen", "Pen", Icons.Default.Edit), Triple("pencil", "Pencil", Icons.Default.Create),
                    Triple("highlighter", "Highlighter", Icons.Default.BorderColor), Triple("eraser", "Eraser", Icons.Default.Clear),
                    Triple("lasso", "Lasso", Icons.Default.Gesture),
                ).forEach { (id, label, icon) ->
                    FilterChip(tool == id, { vm.setDrawTool(id) }, { Text(label, fontSize = 12.sp) }, leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) })
                }
                Button({ vm.setDrawTool(null) }, contentPadding = PaddingValues(horizontal = 14.dp), colors = ButtonDefaults.buttonColors(containerColor = WordBlue)) { Text("Done") }
            }
            if (tool == "lasso") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (ui.lassoCount > 0) "${ui.lassoCount} stroke${if (ui.lassoCount == 1) "" else "s"} selected" else "Draw a loop around the ink to select it", fontSize = 12.sp, color = Color.DarkGray, modifier = Modifier.weight(1f, fill = false))
                    if (ui.lassoCount > 0) {
                        OutlinedButton({ vm.lassoDelete() }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Delete", fontSize = 12.sp) }
                        TextButton({ vm.lassoClear() }) { Text("Clear", fontSize = 12.sp) }
                    }
                }
            } else if (tool != "eraser") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    INK_WIDTHS.forEach { w -> WidthDot(w, ui.drawWidth == w) { vm.setDrawWidth(w) } }
                    Spacer(Modifier.width(6.dp))
                    INK_COLORS.forEach { hex -> InkSwatch(hex, ui.drawColor.equals(hex, true)) { vm.setDrawColor(hex) } }
                    InkMore(ui.drawColor) { vm.setDrawColor(it) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(ui.stylusOnly, { vm.toggleStylusOnly() }, Modifier.height(24.dp))
                Text("  Stylus only", fontSize = 12.sp, color = Color.DarkGray)
                Spacer(Modifier.weight(1f))
                Text(if (ui.stylusOnly) "Fingers scroll" else "Fingers draw", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

/** A pen in the gallery: a swatch of the stroke it makes, its name, and whether it's the one in hand. */
@Composable
private fun PenTile(label: String, color: Color, widthPt: Float, selected: Boolean, translucent: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(62.dp).padding(horizontal = 2.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFFDCE8FA) else Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) WordBlue else Color(0xFFD5D5D5), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(width = 44.dp, height = 30.dp)) {
            val w = widthPt.coerceIn(1.5f, 12f) * 1.6f
            drawLine(if (translucent) color.copy(alpha = 0.45f) else color, Offset(6f, size.height * 0.72f), Offset(size.width - 6f, size.height * 0.28f), w, StrokeCap.Round)
        }
        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Draw tab: Word's Tools and Pens groups, wired to ink on the page. */
@Composable
internal fun DrawGroups(ui: EditorUiState, d: Dispatcher) {
    val vm = d.vm
    val tool = ui.drawTool
    GroupBox("Tools") {
        RBtn("Select", Icons.Default.NearMe, active = tool == null) { vm.setDrawTool(null) }
        RBtn("Lasso", Icons.Default.Gesture, active = tool == "lasso") { vm.setDrawTool("lasso") }
        RBtn("Eraser", Icons.Default.Clear, active = tool == "eraser") { vm.setDrawTool("eraser") }
    }
    GroupBox("Pens") {
        val col = ink(ui.drawColor)
        PenTile("Pen", col, ui.drawWidth, tool == "pen", false) { vm.setDrawTool("pen") }
        PenTile("Pencil", col, ui.drawWidth * 0.8f, tool == "pencil", false) { vm.setDrawTool("pencil") }
        PenTile("Marker", col, maxOf(ui.drawWidth * 3f, 10f), tool == "highlighter", true) { vm.setDrawTool("highlighter") }
        ColorMenu("Ink color", Icons.Default.Palette, true, ui.drawColor, col) { hex -> vm.setDrawColor(if (hex == "auto") "000000" else hex); if (tool == null || tool == "eraser" || tool == "lasso") vm.setDrawTool("pen") }
        MiniMenu(Icons.Default.LineWeight, "Pen thickness", true, INK_WIDTHS.map { w -> MenuEntry("$w pt") { vm.setDrawWidth(w) } })
    }
}
