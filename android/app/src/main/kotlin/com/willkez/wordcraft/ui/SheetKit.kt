package com.willkez.wordcraft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------------------------
// Shared look-and-feel for the full-screen-ish bottom sheets in BorderSheets.kt/FormSheets.kt
// (borders & shading, format table, insert link, table of contents, quick tables). The ribbon's
// older floating panels (Panels.kt/ToolPanels.kt) have their own, lighter-weight pieces; these are
// kept separate so neither file has to match the other's conventions.
// ---------------------------------------------------------------------------------------------

internal val SheetLine = Color(0xFFE4E7EC)
internal val SheetMuted = Color(0xFF667085)
internal val SheetInk = Color(0xFF1B1B1B)
internal val PanelBg = Color(0xFFFFFFFF)
internal val PillOn = Color(0xFFDCE8FA)
internal val PillIdle = Color(0xFFF2F4F7)

internal val LINE_COLORS = listOf("000000", "595959", "A6A6A6", "C00000", "ED7D31", "FFC000", "548235", "00B0F0", "185ABD", "7030A0")
internal val SHADE_COLORS = listOf("FFFFFF", "F2F2F2", "D9D9D9", "FFF2CC", "FCE4D6", "E2EFDA", "DDEBF7", "E4DFEC", "FFFF00", "BDD7EE", "C6E0B4")
internal val PAGE_BORDER_COLORS = LINE_COLORS
internal val LINE_WIDTHS = listOf(0.5, 1.0, 1.5, 2.25, 3.0, 4.5, 6.0)

/** "C00000" -> that colour, opaque; null or malformed input -> null (so callers can fall back). */
internal fun parseHex(hex: String?): Color? =
    hex?.takeIf { it.length == 6 && it.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' } }
        ?.let { Color((0xFF000000L or it.toLong(16)).toInt()) }

/** The modal bottom sheet every sheet in BorderSheets.kt/FormSheets.kt is built from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), content = content)
    }
}

/** The sheet's heading: a bold title and, under it, where it lives in the ribbon. */
@Composable
fun SheetTitle(title: String, location: String) {
    Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = SheetInk)
    Text(location, fontSize = 12.sp, color = SheetMuted, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
}

/** A scrollable region inside the sheet, capped so the sheet never grows past most of the screen. */
@Composable
fun SheetScroll(heightFraction: Float = 1f, content: @Composable ColumnScope.() -> Unit) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    Column(
        Modifier.fillMaxWidth().heightIn(max = screenHeight * 0.62f * heightFraction).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/** A small, muted heading above one group of controls ("Where", "Line style", "Colour", …). */
@Composable
fun SectionLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SheetMuted, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

/** A row of [Pill]s that wraps onto further lines instead of scrolling off the sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PillRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

/** A row of colour swatches, [selected] drawn with a highlight ring (sheet call order: selected, then the list). */
@Composable
fun SwatchRow(selected: String?, colors: List<String>, onPick: (String) -> Unit) {
    SwatchRow(colors, selected, onPick)
}

/** Free-typed hex colour, in case none of the swatches is the right one. */
@Composable
fun HexField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.removePrefix("#").filter { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' }.take(6).uppercase()) },
        singleLine = true, label = { Text("Hex colour") }, placeholder = { Text("e.g. 185ABD") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

/** The sheet's main action, full-width and filled. */
@Composable
fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = enabled,
        shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = WordBlue),
    ) { Text(label) }
}

/** A secondary, lower-emphasis action (e.g. "No shading", "Remove link from selection"). */
@Composable
fun QuietButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick, Modifier.fillMaxWidth(), enabled = enabled) { Text(label) }
}

/** Closes the sheet; the sheet's one way out when there's nothing left to apply. */
@Composable
fun DoneButton(label: String = "Done", onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(14.dp)) { Text(label) }
}

/** A labelled +/- stepper, for small bounded integers like heading levels. */
@Composable
fun Stepper(value: Int, range: IntRange, label: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp, color = SheetInk)
        IconButton({ if (value > range.first) onChange(value - 1) }) { Icon(Icons.Default.Remove, "Fewer") }
        Text("$value", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
        IconButton({ if (value < range.last) onChange(value + 1) }) { Icon(Icons.Default.Add, "More") }
    }
}

/** A labelled on/off switch, with an optional one-line explanation underneath. */
@Composable
fun SwitchRow(label: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = SheetInk)
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = SheetMuted)
        }
        Switch(checked, onChange)
    }
}
