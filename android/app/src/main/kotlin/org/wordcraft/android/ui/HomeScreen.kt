package org.wordcraft.android.ui

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import org.wordcraft.android.R
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.wordcraft.android.data.RecentDoc
import org.wordcraft.android.data.Recovery
import java.util.Calendar

private val PageBg = Color(0xFFF4F5F7)
private val TEMPLATES = listOf(
    Triple("Blank document", "blank", "Start from nothing"),
    Triple("Letter", "letter", "Formal letter"),
    Triple("Resume", "resume", "CV with sections"),
    Triple("Report", "report", "Title, headings, body"),
    Triple("Sample", "sample", "Tour of formatting"),
)

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Working late?"
}

private fun dayBucket(t: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = t }
    fun Calendar.dayKey() = get(Calendar.YEAR) * 1000 + get(Calendar.DAY_OF_YEAR)
    val diff = now.dayKey() - then.dayKey()
    return when {
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && diff == 0 -> "Today"
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && diff == 1 -> "Yesterday"
        now.timeInMillis - t < 7 * DateUtils.DAY_IN_MILLIS -> "Earlier this week"
        now.timeInMillis - t < 31 * DateUtils.DAY_IN_MILLIS -> "Earlier this month"
        else -> "Older"
    }
}

/** Start screen: greeting and search, a template gallery, and every recent activity grouped by day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recents: List<RecentDoc>,
    recovery: Recovery? = null,
    onRecover: () -> Unit = {},
    onDiscardRecovery: () -> Unit = {},
    onNew: (String) -> Unit,
    onOpenFile: () -> Unit,
    onOpenRecent: (RecentDoc) -> Unit,
    onPin: (RecentDoc) -> Unit = {},
    onRemove: (RecentDoc) -> Unit = {},
    onClear: () -> Unit = {},
    thumbFor: (String) -> android.graphics.Bitmap? = { null },
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var confirmClear by remember { mutableStateOf(false) }

    val shown = remember(recents, query, filter) {
        recents.filter { r ->
            (query.isBlank() || r.name.contains(query.trim(), ignoreCase = true)) &&
                when (filter) { "Pinned" -> r.pinned; "All" -> true; else -> r.action == filter }
        }
    }
    val pinned = shown.filter { it.pinned }
    val grouped = shown.filter { !it.pinned }.sortedByDescending { it.openedAt }.groupBy { dayBucket(it.openedAt) }

    Scaffold(
        containerColor = PageBg,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNew("blank") }, containerColor = WordBlue, contentColor = Color.White,
                modifier = Modifier.navigationBarsPadding(),
            ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New document") }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 112.dp)) {
            item {
                Column(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(WordBlue, WordBlueDark)))
                        .statusBarsPadding().padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                                .background(Brush.radialGradient(listOf(Color(0xFF0F4C8E), Color(0xFF051A3D)))),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(painterResource(R.drawable.wordcraft_logo), "WordCraft", Modifier.padding(5.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(greeting(), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                            Text("WordCraft", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onOpenFile) { Icon(Icons.Default.FolderOpen, "Open a file", tint = Color.White) }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("Search recent documents") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Close, "Clear") } },
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                        ),
                    )
                }
            }

            recovery?.let { r ->
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E0)), shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Recover unsaved work", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${r.name} · autosaved " + DateUtils.getRelativeTimeSpanString(r.savedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE),
                                fontSize = 13.sp, color = Color.DarkGray,
                            )
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onRecover, colors = ButtonDefaults.buttonColors(containerColor = WordBlue)) { Text("Recover") }
                                TextButton(onDiscardRecovery) { Text("Discard") }
                            }
                        }
                    }
                }
            }

            item { SectionTitle("New") }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TEMPLATES.forEach { (name, key, hint) -> TemplateCard(name, key, hint) { onNew(key) } }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (recents.any { !it.pinned }) TextButton({ confirmClear = true }) { Text("Clear") }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Pinned", "Opened", "Saved", "Exported").forEach { f ->
                        FilterChip(filter == f, { filter = f }, { Text(f) })
                    }
                }
            }

            if (shown.isEmpty()) item { EmptyState(recents.isEmpty(), onOpenFile) }
            if (pinned.isNotEmpty()) {
                item { GroupTitle("Pinned") }
                pinned.forEach { r -> item(key = "p" + r.uri) { RecentRow(r, thumbFor, onOpenRecent, onPin, onRemove) } }
            }
            grouped.forEach { (day, docs) ->
                item(key = "h$day") { GroupTitle(day) }
                docs.forEach { r -> item(key = r.uri) { RecentRow(r, thumbFor, onOpenRecent, onPin, onRemove) } }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear recent list?") },
            text = { Text("Your files stay where they are. Pinned documents are kept.") },
            confirmButton = { TextButton({ confirmClear = false; onClear() }) { Text("Clear") } },
            dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun GroupTitle(text: String) =
    Text(text.uppercase(), Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp), fontSize = 11.sp, color = WordBlue, fontWeight = FontWeight.SemiBold)

@Composable
private fun EmptyState(noneAtAll: Boolean, onOpenFile: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Description, null, Modifier.size(56.dp), tint = Color(0xFFB0B7C3))
        Spacer(Modifier.height(8.dp))
        Text(if (noneAtAll) "No documents yet" else "Nothing matches", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            if (noneAtAll) "Documents you create, open, save or export show up here." else "Try a different search or filter.",
            fontSize = 13.sp, color = Color.Gray,
        )
        if (noneAtAll) TextButton(onOpenFile) { Text("Open a file") }
    }
}

@Composable
private fun TemplateCard(name: String, key: String, hint: String, onClick: () -> Unit) {
    Column(Modifier.width(112.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.77f).clip(RoundedCornerShape(8.dp)).background(Color.White)
                .border(1.dp, Color(0xFFD6D9DF), RoundedCornerShape(8.dp)),
        ) { PaperPreview(key) }
        Text(name, Modifier.padding(top = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(hint, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A tiny drawing of the page layout each template starts with. */
@Composable
private fun PaperPreview(kind: String) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val ink = Color(0xFFCDD2DA); val accent = WordBlue.copy(alpha = 0.75f)
        fun line(x: Float, y: Float, len: Float, c: Color = ink, t: Float = h * 0.018f) = drawRect(c, Offset(x * w, y * h), Size(len * w, t))
        when (kind) {
            "blank" -> {
                drawCircle(WordBlue.copy(alpha = 0.12f), w * 0.2f, Offset(w / 2, h / 2))
                line(0.42f, 0.485f, 0.16f, accent, h * 0.03f); drawRect(accent, Offset(w * 0.488f, h * 0.43f), Size(w * 0.024f, h * 0.14f))
            }
            "letter" -> {
                line(0.62f, 0.10f, 0.28f); line(0.66f, 0.14f, 0.24f)
                line(0.12f, 0.26f, 0.3f, accent); line(0.12f, 0.30f, 0.26f)
                (0 until 7).forEach { line(0.12f, 0.44f + it * 0.065f, if (it == 6) 0.4f else 0.76f) }
            }
            "resume" -> {
                drawRect(accent, Offset(0f, 0f), Size(w, h * 0.2f))
                line(0.12f, 0.08f, 0.4f, Color.White, h * 0.03f)
                drawRect(Color(0xFFEEF1F6), Offset(0f, h * 0.2f), Size(w * 0.32f, h * 0.8f))
                (0 until 4).forEach { line(0.06f, 0.28f + it * 0.07f, 0.2f) }
                (0 until 8).forEach { line(0.38f, 0.28f + it * 0.075f, if (it % 3 == 2) 0.3f else 0.52f) }
            }
            "report" -> {
                line(0.12f, 0.12f, 0.6f, accent, h * 0.045f); line(0.12f, 0.2f, 0.34f)
                line(0.12f, 0.32f, 0.3f, accent, h * 0.03f)
                (0 until 4).forEach { line(0.12f, 0.39f + it * 0.06f, 0.76f) }
                line(0.12f, 0.67f, 0.3f, accent, h * 0.03f)
                (0 until 3).forEach { line(0.12f, 0.74f + it * 0.06f, if (it == 2) 0.4f else 0.76f) }
            }
            else -> {
                line(0.12f, 0.1f, 0.5f, accent, h * 0.04f)
                (0 until 9).forEach { line(0.12f, 0.22f + it * 0.07f, if (it % 4 == 3) 0.35f else 0.76f) }
            }
        }
    }
}

@Composable
private fun TypeTile(doc: RecentDoc) {
    val (bg, label) = when (doc.extension) {
        "pdf" -> Color(0xFFD64545) to "PDF"
        "txt", "md" -> Color(0xFF6B7280) to "TXT"
        "odt" -> Color(0xFF1F9D8A) to "ODT"
        "html", "htm" -> Color(0xFFE8833A) to "HTM"
        "rtf" -> Color(0xFF7C5CC4) to "RTF"
        else -> WordBlue to "DOC"
    }
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecentRow(r: RecentDoc, thumbFor: (String) -> android.graphics.Bitmap?, onOpen: (RecentDoc) -> Unit, onPin: (RecentDoc) -> Unit, onRemove: (RecentDoc) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val whenText = DateUtils.getRelativeTimeSpanString(r.openedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { onOpen(r) },
        colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumb = remember(r.uri, r.openedAt) { thumbFor(r.uri)?.asImageBitmap() }
            if (thumb != null) {
                Image(
                    thumb, "Preview of ${r.name}",
                    Modifier.height(64.dp).aspectRatio(thumb.width.toFloat() / thumb.height).clip(RoundedCornerShape(4.dp)).border(1.dp, Color(0xFFD6D9DF), RoundedCornerShape(4.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            } else TypeTile(r)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(r.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (r.action) { "Saved" -> Icons.Default.Save; "Exported" -> Icons.Default.Share; else -> Icons.Default.FolderOpen },
                        null, Modifier.size(13.dp), tint = Color.Gray,
                    )
                    Text("  ${r.action} · $whenText", fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                }
            }
            if (r.pinned) Icon(Icons.Default.Star, "Pinned", Modifier.size(18.dp), tint = Color(0xFFF2B01E))
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (r.editable) "Open" else "Open in another app") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, null) }, onClick = { menu = false; onOpen(r) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (r.pinned) "Unpin" else "Pin to top") },
                        leadingIcon = { Icon(if (r.pinned) Icons.Default.Star else Icons.Outlined.StarBorder, null) },
                        onClick = { menu = false; onPin(r) },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from list") }, leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menu = false; onRemove(r) },
                    )
                }
            }
        }
    }
}
