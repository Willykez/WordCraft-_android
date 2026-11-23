package com.willkez.wordcraft.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willkez.wordcraft.data.DeviceDoc

private val KINDS = listOf(
    Triple("All", PaletteBlue, null as Set<String>?),
    Triple("Word", PaletteIndigo, setOf("docx", "doc", "docm", "dotx")),
    Triple("PDF", PaletteCrimson, setOf("pdf")),
    Triple("Text", PaletteTeal, setOf("txt", "md", "rtf")),
    Triple("Web & ODT", PaletteOrange, setOf("html", "htm", "odt")),
)

/** Every document on the device in one searchable, filterable list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    docs: List<DeviceDoc>,
    scanning: Boolean,
    fullAccess: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onGrantAccess: () -> Unit,
    onOpen: (DeviceDoc) -> Unit,
    onBrowse: () -> Unit,
) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("All") }
    var sort by remember { mutableStateOf("Newest") }
    var sortMenu by remember { mutableStateOf(false) }

    val shown = remember(docs, query, kind, sort) {
        val exts = KINDS.first { it.first == kind }.third
        docs.filter { d ->
            (exts == null || d.extension in exts) &&
                (query.isBlank() || d.name.contains(query.trim(), true) || d.folder.contains(query.trim(), true))
        }.let {
            when (sort) {
                "Name" -> it.sortedBy { d -> d.name.lowercase() }
                "Largest" -> it.sortedByDescending { d -> d.size }
                else -> it.sortedByDescending { d -> d.modified }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(PageWash)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(ThemeGradient)).statusBarsPadding().padding(bottom = 12.dp)) {
                Row(Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text("Device documents", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (scanning) "Looking through storage…" else "${shown.size} of ${docs.size} documents", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                    IconButton(onRefresh, enabled = !scanning) { Icon(Icons.Default.Refresh, "Rescan", tint = Color.White) }
                    Box {
                        IconButton({ sortMenu = true }) { Icon(Icons.Default.Sort, "Sort", tint = Color.White) }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            listOf("Newest", "Name", "Largest").forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s) }, onClick = { sort = s; sortMenu = false },
                                    leadingIcon = { if (sort == s) Icon(Icons.Default.Check, null) },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
                    placeholder = { Text("Search by name or folder") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Close, "Clear") } },
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceWhite, unfocusedContainerColor = SurfaceWhite,
                        focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                    ),
                )
            }
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth(), color = PalettePink, trackColor = tint(PalettePink, 0.2f))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KINDS.forEach { (label, c, _) ->
                    FilterChip(
                        kind == label, { kind = label }, { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = tint(c, 0.10f), labelColor = c, selectedContainerColor = c, selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (!fullAccess) item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = tint(PaletteAmber, 0.18f)), shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Only some documents are visible", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Android hides documents saved by other apps. Allow access to all files to list every document on this device.",
                                fontSize = 13.sp, color = TextMuted,
                            )
                            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onGrantAccess, colors = ButtonDefaults.buttonColors(containerColor = PaletteOrange)) { Text("Allow access") }
                                TextButton(onBrowse) { Text("Pick a file instead") }
                            }
                        }
                    }
                }
                if (shown.isEmpty() && !scanning) item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(52.dp), tint = PaletteIndigo)
                        Text(if (docs.isEmpty()) "No documents found yet" else "Nothing matches", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Pull the rescan button after saving or copying files.", fontSize = 13.sp, color = Color.Gray)
                    }
                }
                items(shown, key = { it.uri }) { d -> DocRow(d, Formatter.formatShortFileSize(ctx, d.size)) { onOpen(d) } }
            }
        }
    }
}

@Composable
private fun DocRow(d: DeviceDoc, size: String, onClick: () -> Unit) {
    val (color, label) = typeStyle(d.extension)
    val whenText = DateUtils.getRelativeTimeSpanString(d.modified, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = tint(color, 0.09f)), shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(color), contentAlignment = Alignment.Center) {
                Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(d.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(d.folder, fontSize = 12.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$size · $whenText", fontSize = 11.sp, color = Color.Gray, maxLines = 1)
            }
            if (!d.editable) Icon(Icons.Default.OpenInNew, "Opens in another app", Modifier.size(18.dp), tint = Color.Gray)
        }
    }
}
