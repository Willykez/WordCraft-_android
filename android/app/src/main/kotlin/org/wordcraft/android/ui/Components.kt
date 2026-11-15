package org.wordcraft.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
private val BASE_ICONS: Map<String, ImageVector> = mapOf(
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

private val EXTRA_ICONS: Map<String, ImageVector> = mapOf(
    // Insert
    "insert.coverPage" to Icons.Default.Article, "insert.blankPage" to Icons.Default.NoteAdd, "table.fromText" to Icons.Default.ViewList,
    "insert.spreadsheet" to Icons.Default.GridOn, "table.quick" to Icons.Default.ViewModule, "insert.onlinePicture" to Icons.Default.ImageSearch,
    "insert.shape" to Icons.Default.Category, "insert.icon" to Icons.Default.Star, "insert.model3d" to Icons.Default.ViewInAr,
    "insert.smartArt" to Icons.Default.AccountTree, "insert.chart" to Icons.Default.BarChart, "insert.screenshot" to Icons.Default.CropFree,
    "insert.canvas" to Icons.Default.Brush, "addins.get" to Icons.Default.Extension, "insert.video" to Icons.Default.Videocam,
    "insert.removeLink" to Icons.Default.LinkOff, "insert.bookmark" to Icons.Default.Bookmark, "insert.crossReference" to Icons.Default.SwapHoriz,
    "insert.header" to Icons.Default.VerticalAlignTop, "insert.footer" to Icons.Default.VerticalAlignBottom, "insert.pageNumber" to Icons.Default.Numbers,
    "insert.editHeader" to Icons.Default.Edit, "insert.editFooter" to Icons.Default.Edit, "insert.removeHeader" to Icons.Default.Delete,
    "insert.removeFooter" to Icons.Default.Delete, "insert.closeHeader" to Icons.Default.Close, "insert.textBox" to Icons.Default.TextFields,
    "insert.quickParts" to Icons.Default.ViewQuilt, "insert.autoText" to Icons.Default.AutoAwesome, "insert.docProperty" to Icons.Default.Info,
    "insert.field" to Icons.Default.Code, "insert.wordArt" to Icons.Default.Title, "insert.dropCap" to Icons.Default.FormatSize,
    "insert.signatureLine" to Icons.Default.Edit, "insert.dateTime" to Icons.Default.Event, "insert.object" to Icons.Default.Widgets,
    "insert.textFromFile" to Icons.Default.FolderOpen, "insert.equation" to Icons.Default.Functions, "insert.inkEquation" to Icons.Default.Gesture,
    "insert.symbol" to Icons.Default.Star, "table.draw" to Icons.Default.Edit,
    // Design
    "design.theme" to Icons.Default.Palette, "design.styleSet" to Icons.Default.Style, "design.themeColors" to Icons.Default.ColorLens,
    "design.themeFonts" to Icons.Default.FontDownload, "design.paragraphSpacing" to Icons.Default.FormatLineSpacing, "design.effects" to Icons.Default.AutoAwesome,
    "design.setDefault" to Icons.Default.Check, "design.watermark" to Icons.Default.Opacity, "design.pageColor" to Icons.Default.FormatColorFill,
    "design.pageBorders" to Icons.Default.BorderOuter,
    // Layout
    "layout.margins" to Icons.Default.AspectRatio, "layout.orientation" to Icons.Default.ScreenRotation, "layout.size" to Icons.Default.Description,
    "layout.columns" to Icons.Default.ViewColumn, "layout.break" to Icons.Default.InsertPageBreak, "layout.lineNumbers" to Icons.Default.FormatListNumbered,
    "layout.hyphenation" to Icons.Default.TextFields, "layout.pageSetup" to Icons.Default.Settings, "layout.verticalAlign" to Icons.Default.VerticalAlignCenter,
    "para.indents" to Icons.Default.FormatIndentIncrease, "para.spacing" to Icons.Default.FormatLineSpacing,
    "arrange.position" to Icons.Default.OpenWith, "arrange.wrap" to Icons.Default.WrapText, "arrange.bringForward" to Icons.Default.FlipToFront,
    "arrange.sendBackward" to Icons.Default.FlipToBack, "arrange.selectionPane" to Icons.Default.Layers, "arrange.group" to Icons.Default.GroupWork,
    "arrange.rotate" to Icons.Default.RotateRight, "arrange.rotation" to Icons.Default.RotateRight,
    // References
    "references.toc" to Icons.Default.Toc, "references.addText" to Icons.Default.Add, "references.updateToc" to Icons.Default.Refresh,
    "references.removeToc" to Icons.Default.Delete, "references.footnote" to Icons.Default.Subject, "references.endnote" to Icons.Default.Subject,
    "references.nextFootnote" to Icons.Default.NavigateNext, "references.notes" to Icons.Default.Notes, "references.researcher" to Icons.Default.Search,
    "references.citation" to Icons.Default.FormatQuote, "references.sources" to Icons.Default.LibraryBooks, "references.citationStyle" to Icons.Default.Style,
    "references.bibliography" to Icons.Default.MenuBook, "references.caption" to Icons.Default.Title, "references.tableOfFigures" to Icons.Default.Toc,
    "references.updateFigures" to Icons.Default.Refresh, "references.markEntry" to Icons.Default.Bookmark, "references.index" to Icons.Default.SortByAlpha,
    "references.updateIndex" to Icons.Default.Refresh, "references.markCitation" to Icons.Default.FormatQuote, "references.tableOfAuthorities" to Icons.Default.Gavel,
    "references.updateAuthorities" to Icons.Default.Refresh, "references.updateFields" to Icons.Default.Refresh,
    // Review
    "review.thesaurus" to Icons.Default.MenuBook, "review.wordCount" to Icons.Default.Numbers, "review.readAloud" to Icons.Default.VolumeUp,
    "file.accessibility" to Icons.Default.Accessibility, "view.altTextPane" to Icons.Default.Accessibility, "review.translate" to Icons.Default.Translate,
    "review.language" to Icons.Default.Language, "review.previousComment" to Icons.Default.NavigateBefore, "review.nextComment" to Icons.Default.NavigateNext,
    "review.comments" to Icons.Default.Comment, "review.resolveComment" to Icons.Default.DoneAll, "review.reply" to Icons.Default.Reply,
    "review.trackChanges" to Icons.Default.EditNote, "review.markup" to Icons.Default.Visibility, "review.showMarkup" to Icons.Default.Visibility,
    "review.changes" to Icons.Default.ListAlt, "review.trackingOptions" to Icons.Default.Settings, "review.accept" to Icons.Default.Check,
    "review.reject" to Icons.Default.Close, "review.acceptAll" to Icons.Default.DoneAll, "review.rejectAll" to Icons.Default.Close,
    "review.previousChange" to Icons.Default.NavigateBefore, "review.nextChange" to Icons.Default.NavigateNext, "review.compare" to Icons.Default.CompareArrows,
    "review.combine" to Icons.Default.CallMerge, "review.blockAuthors" to Icons.Default.Block, "review.restrict" to Icons.Default.Lock,
    "review.hideInk" to Icons.Default.VisibilityOff,
    // View
    "view.readMode" to Icons.Default.MenuBook, "view.printLayout" to Icons.Default.Description, "view.webLayout" to Icons.Default.Language,
    "view.outline" to Icons.Default.FormatListBulleted, "view.draft" to Icons.Default.Notes, "view.focus" to Icons.Default.CenterFocusStrong,
    "view.immersive" to Icons.Default.Fullscreen, "view.vertical" to Icons.Default.ViewAgenda, "view.sideToSide" to Icons.Default.ViewColumn,
    "view.ruler" to Icons.Default.Straighten, "view.gridlines" to Icons.Default.GridOn, "view.zoom" to Icons.Default.ZoomIn,
    "view.zoom100" to Icons.Default.FitScreen, "view.onePage" to Icons.Default.Description, "view.multiplePages" to Icons.Default.ViewModule,
    "view.pageWidth" to Icons.Default.SwapHoriz, "view.darkMode" to Icons.Default.DarkMode, "view.newWindow" to Icons.Default.OpenInNew,
    "view.arrangeAll" to Icons.Default.ViewQuilt, "view.split" to Icons.Default.VerticalSplit, "view.sideBySide" to Icons.Default.ViewColumn,
    "view.syncScroll" to Icons.Default.SyncAlt, "view.switchWindows" to Icons.Default.OpenInNew, "tools.macros" to Icons.Default.Code,
    "tools.recordMacro" to Icons.Default.FiberManualRecord, "file.properties" to Icons.Default.Info,
    // Tables
    "table.selectTable" to Icons.Default.SelectAll, "table.selectRow" to Icons.Default.SelectAll, "table.selectColumn" to Icons.Default.SelectAll,
    "table.selectCell" to Icons.Default.SelectAll, "table.viewGridlines" to Icons.Default.GridOn, "table.properties" to Icons.Default.Settings,
    "table.altText" to Icons.Default.Accessibility, "table.rtl" to Icons.Default.SwapHoriz, "table.eraser" to Icons.Default.Clear,
    "table.deleteCells" to Icons.Default.Delete, "table.deleteRow" to Icons.Default.Delete, "table.deleteColumn" to Icons.Default.Delete,
    "table.deleteTable" to Icons.Default.Delete, "table.insertRowAbove" to Icons.Default.AddBox, "table.insertRowBelow" to Icons.Default.AddBox,
    "table.insertColumnLeft" to Icons.Default.AddBox, "table.insertColumnRight" to Icons.Default.AddBox, "table.merge" to Icons.Default.CallMerge,
    "table.split" to Icons.Default.CallSplit, "table.splitTable" to Icons.Default.CallSplit, "table.autofit" to Icons.Default.AspectRatio,
    "table.rowHeight" to Icons.Default.Height, "table.columnWidth" to Icons.Default.SwapHoriz, "table.distributeRows" to Icons.Default.ViewAgenda,
    "table.distributeColumns" to Icons.Default.ViewColumn, "table.cellAlign" to Icons.Default.GridOn, "table.textDirection" to Icons.Default.ScreenRotation,
    "table.cellMargins" to Icons.Default.BorderOuter, "table.sort" to Icons.Default.SortByAlpha, "table.repeatHeader" to Icons.Default.Repeat,
    "table.toText" to Icons.Default.TextFields, "table.formula" to Icons.Default.Functions, "table.look" to Icons.Default.CheckBox,
    "table.style" to Icons.Default.TableChart, "table.newStyle" to Icons.Default.Add, "table.modifyStyle" to Icons.Default.Edit,
    "table.deleteStyle" to Icons.Default.Delete, "table.shading" to Icons.Default.FormatColorFill, "table.borders" to Icons.Default.BorderAll,
    "table.borderPainter" to Icons.Default.Brush,
    // Pictures
    "picture.removeBackground" to Icons.Default.Layers, "picture.corrections" to Icons.Default.Tune, "picture.color" to Icons.Default.Palette,
    "picture.effects" to Icons.Default.AutoAwesome, "picture.transparency" to Icons.Default.Opacity, "picture.compress" to Icons.Default.Compress,
    "picture.change" to Icons.Default.SwapHoriz, "picture.reset" to Icons.Default.Restore, "picture.crop" to Icons.Default.Crop,
    "picture.size" to Icons.Default.AspectRatio, "picture.style" to Icons.Default.Style, "picture.border" to Icons.Default.BorderColor,
    "picture.altText" to Icons.Default.Accessibility,
    // Draw
    "draw.select" to Icons.Default.NearMe, "draw.lasso" to Icons.Default.Gesture, "draw.eraser" to Icons.Default.Clear,
    "draw.pen" to Icons.Default.Edit, "draw.pencil" to Icons.Default.Create, "draw.highlighter" to Icons.Default.BorderColor,
    "draw.inkToShape" to Icons.Default.Category, "draw.convertInk" to Icons.Default.Category, "draw.replay" to Icons.Default.Refresh,
    "edit.selectAll" to Icons.Default.SelectAll,
)

val CMD_ICONS: Map<String, ImageVector> = BASE_ICONS + EXTRA_ICONS

/** An icon for a command: a specific one, else a family icon (mailings, developer controls). */
fun iconFor(id: String): ImageVector? = CMD_ICONS[id] ?: when {
    id.startsWith("mailings.") -> Icons.Default.Mail
    id.startsWith("developer.") -> Icons.Default.Code
    else -> null
}

@Composable
fun CmdIcon(cmd: Cmd?, label: String, tint: Color, size: Int = 24) {
    val icon = cmd?.let { iconFor(it.id) }
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

/** An icon-only button that opens a drop-down (line spacing, change case…). */
@Composable
fun MiniMenu(icon: ImageVector, label: String, enabled: Boolean = true, entries: List<MenuEntry>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(30.dp).clip(RoundedCornerShape(4.dp)).clickable(enabled = enabled) { open = true }.padding(start = 5.dp, end = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ink = if (enabled) RibbonInk else RibbonInk.copy(alpha = 0.35f)
            Icon(icon, label, Modifier.size(20.dp), tint = ink)
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(13.dp), tint = ink)
        }
        DropdownMenu(open, { open = false }, Modifier.heightIn(max = 400.dp)) {
            entries.forEach { e ->
                DropdownMenuItem(
                    text = { Text(e.label, fontSize = 14.sp) }, enabled = e.enabled,
                    trailingIcon = if (e.hint.isNotEmpty()) ({ Text(e.hint, fontSize = 11.sp, color = Color.Gray) }) else null,
                    onClick = { open = false; e.onClick() },
                )
            }
        }
    }
}

/** Word's split button: the icon applies the default, the arrow opens the choices. */
@Composable
fun SplitMini(icon: ImageVector, label: String, active: Boolean, enabled: Boolean, entries: List<MenuEntry>, onClick: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        MiniToggle(icon, label, active, enabled, onClick)
        Box {
            Box(Modifier.width(14.dp).height(30.dp).clickable(enabled = enabled) { open = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.KeyboardArrowDown, "$label options", Modifier.size(14.dp), tint = if (enabled) RibbonInk else RibbonInk.copy(alpha = 0.35f))
            }
            DropdownMenu(open, { open = false }, Modifier.heightIn(max = 400.dp)) {
                entries.forEach { e -> DropdownMenuItem(text = { Text(e.label) }, enabled = e.enabled, onClick = { open = false; e.onClick() }) }
            }
        }
    }
}

/** Insert Table: press and slide over the grid to size it, as in Word. */
@Composable
fun TableGridBtn(enabled: Boolean, onPick: (rows: Int, cols: Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var hr by remember { mutableIntStateOf(0) }
    var hc by remember { mutableIntStateOf(0) }
    Box {
        RBtn("Table ▾", Icons.Default.TableChart, enabled) { hr = 0; hc = 0; open = true }
        DropdownMenu(open, { open = false }) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(if (hr > 0) "$hc × $hr table" else "Insert table", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                for (r in 1..8) Row {
                    for (c in 1..10) Box(
                        Modifier.padding(1.dp).size(22.dp)
                            .background(if (r <= hr && c <= hc) WordBlue.copy(alpha = 0.35f) else Color.White)
                            .border(1.dp, if (r <= hr && c <= hc) WordBlue else Color(0xFFBBBBBB))
                            .pointerInput(r, c) {
                                detectTapGestures(onPress = { hr = r; hc = c; tryAwaitRelease() }, onTap = { open = false; onPick(r, c) })
                            },
                    )
                }
            }
        }
    }
}
