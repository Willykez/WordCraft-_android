package org.wordcraft.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Icons for the commands people reach for most; every other command gets a lettered badge. */
val CMD_ICONS: Map<String, ImageVector> = mapOf(
    "file.save" to Icons.Default.Save, "file.open" to Icons.Default.FolderOpen, "file.new" to Icons.Default.Description,
    "file.print" to Icons.Default.Print, "file.share" to Icons.Default.Share, "file.info" to Icons.Default.Info,
    "file.options" to Icons.Default.Settings, "file.protect" to Icons.Default.Lock,
    "edit.undo" to Icons.Default.Undo, "edit.redo" to Icons.Default.Redo,
    "edit.cut" to Icons.Default.ContentCut, "edit.copy" to Icons.Default.ContentCopy, "edit.paste" to Icons.Default.ContentPaste,
    "edit.formatPainter" to Icons.Default.FormatPaint, "edit.find" to Icons.Default.Search, "edit.replace" to Icons.Default.FindReplace,
    "format.bold" to Icons.Default.FormatBold, "format.italic" to Icons.Default.FormatItalic, "format.underline" to Icons.Default.FormatUnderlined,
    "format.strikethrough" to Icons.Default.StrikethroughS, "format.subscript" to Icons.Default.Subscript,
    "format.superscript" to Icons.Default.Superscript, "format.color" to Icons.Default.FormatColorText,
    "format.highlight" to Icons.Default.FormatColorFill, "format.clear" to Icons.Default.FormatClear,
    "format.growFont" to Icons.Default.Add, "format.shrinkFont" to Icons.Default.Remove, "format.size" to Icons.Default.FormatSize,
    "format.font" to Icons.Default.FontDownload,
    "para.alignLeft" to Icons.Default.FormatAlignLeft, "para.alignCenter" to Icons.Default.FormatAlignCenter,
    "para.alignRight" to Icons.Default.FormatAlignRight, "para.justify" to Icons.Default.FormatAlignJustify,
    "para.bullets" to Icons.Default.FormatListBulleted, "para.numbering" to Icons.Default.FormatListNumbered,
    "para.indent" to Icons.Default.FormatIndentIncrease, "para.outdent" to Icons.Default.FormatIndentDecrease,
    "para.lineSpacing" to Icons.Default.FormatLineSpacing, "para.style" to Icons.Default.Style,
    "insert.table" to Icons.Default.TableChart, "insert.picture" to Icons.Default.Image, "insert.link" to Icons.Default.Link,
    "insert.pageBreak" to Icons.Default.InsertPageBreak, "review.newComment" to Icons.Default.Comment,
    "review.deleteComment" to Icons.Default.Delete, "review.spelling" to Icons.Default.Spellcheck,
    "view.zoomIn" to Icons.Default.ZoomIn, "view.zoomOut" to Icons.Default.ZoomOut,
    "view.navigationPane" to Icons.Default.Toc, "styles.pane" to Icons.Default.Style, "tools.search" to Icons.Default.Search,
)

@Composable
fun CmdIcon(cmd: Cmd?, label: String, tint: Color, size: Int = 24) {
    val icon = cmd?.let { CMD_ICONS[it.id] }
    if (icon != null) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(size.dp))
    } else {
        val letters = label.split(' ', '&', '/', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
        Box(Modifier.size(size.dp).clip(RoundedCornerShape(6.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(letters, color = tint, fontSize = (size * 0.42f).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A ribbon button: icon over a short label. */
@Composable
fun RBtn(label: String, icon: ImageVector?, enabled: Boolean = true, active: Boolean = false, cmd: Cmd? = null, onClick: () -> Unit) {
    val ink = if (enabled) RibbonInk else RibbonInk.copy(alpha = 0.35f)
    Column(
        Modifier.widthIn(min = 52.dp, max = 72.dp).padding(horizontal = 2.dp).clip(RoundedCornerShape(6.dp))
            .background(if (active) WordBlue.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) Icon(icon, label, tint = ink, modifier = Modifier.size(24.dp)) else CmdIcon(cmd, label, ink)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, lineHeight = 11.sp, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

data class MenuEntry(val label: String, val enabled: Boolean = true, val hint: String = "", val onClick: () -> Unit)

/** A ribbon button that opens a dropdown of commands (Word's split/drop-down buttons). */
@Composable
fun MenuBtn(label: String, icon: ImageVector?, entries: List<MenuEntry>, enabled: Boolean = true, cmd: Cmd? = null) {
    var open by remember { mutableStateOf(false) }
    Box {
        RBtn("$label ▾", icon, enabled && entries.isNotEmpty(), cmd = cmd) { open = true }
        DropdownMenu(open, { open = false }) {
            entries.forEach { e ->
                DropdownMenuItem(
                    text = { Text(e.label, fontSize = 14.sp) },
                    enabled = e.enabled,
                    trailingIcon = if (e.hint.isNotEmpty()) ({ Text(e.hint, fontSize = 11.sp, color = Color.Gray) }) else null,
                    onClick = { open = false; e.onClick() },
                )
            }
        }
    }
}

/** A small boxed drop-down (font name, size) with its current value. */
@Composable
fun Combo(value: String, width: Int, entries: List<MenuEntry>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.width(width.dp).height(30.dp).clip(RoundedCornerShape(4.dp)).background(Color.White)
                .clickable { open = true }.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = RibbonInk)
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp), tint = RibbonInk)
        }
        DropdownMenu(open, { open = false }, Modifier.heightIn(max = 360.dp)) {
            entries.forEach { e -> DropdownMenuItem(text = { Text(e.label) }, onClick = { open = false; e.onClick() }) }
        }
    }
}

/** A small square toggle (bold, italic, …). */
@Composable
fun MiniToggle(icon: ImageVector, label: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val ink = if (enabled) RibbonInk else RibbonInk.copy(alpha = 0.35f)
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(4.dp)).background(if (active) WordBlue.copy(alpha = 0.28f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, Modifier.size(20.dp), tint = ink) }
}

@Composable
fun GroupBox(title: String, content: @Composable () -> Unit) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) { content() }
            Text(title, fontSize = 10.sp, color = Color(0xFF666666), maxLines = 1)
        }
        VerticalDivider(Modifier.padding(vertical = 6.dp), color = Color(0xFFD0D0D0))
    }
}

@Composable
fun PanelSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = WordBlue)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

val OutlineBorder = BorderStroke(1.dp, Color(0xFFCCCCCC))
