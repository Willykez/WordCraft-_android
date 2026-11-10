package com.wordcraft.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

val WordBlue = Color(0xFF185ABD)
val ChromeGrey = Color(0xFFF3F3F3)

/** Tab strip (File, Home, Insert...) with a button that collapses or expands the ribbon. */
@Composable
fun RibbonTabBar(
    tabs: List<RibbonTab>,
    selectedTab: String,
    expanded: Boolean,
    onSelectTab: (String) -> Unit,
    onToggle: () -> Unit
) {
    Row(Modifier.fillMaxWidth().background(ChromeGrey), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            tabs.forEach { tab ->
                val selected = expanded && tab.name == selectedTab
                Column(
                    Modifier.clickable { onSelectTab(tab.name) }.padding(horizontal = 12.dp).height(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        tab.name, fontSize = 14.sp, maxLines = 1,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) WordBlue else Color(0xFF242424)
                    )
                    Box(Modifier.padding(top = 3.dp).width(if (selected) 22.dp else 0.dp).height(2.dp).background(WordBlue))
                }
            }
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse ribbon" else "Expand ribbon"
            )
        }
    }
}

/** The selected tab's groups. Common commands are icon buttons; the rest are text buttons. */
@Composable
fun RibbonBody(state: EditorState, tab: RibbonTab?, onInvoke: (RibbonItem) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFFBFBFB))) {
        Row(
            Modifier.fillMaxWidth().height(112.dp).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.Top
        ) {
            tab?.groups?.forEach { group ->
                RibbonGroupView(state, group, onInvoke)
                Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 8.dp).background(Color(0xFFE1E1E1)))
            }
        }
        HorizontalDivider(color = Color(0xFFE1E1E1))
    }
}

@Composable
private fun RibbonGroupView(state: EditorState, group: RibbonGroup, onInvoke: (RibbonItem) -> Unit) {
    val (iconItems, textItems) = group.items.partition { commandIcon(it.id) != null }
    Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.weight(1f)) {
            // Icon buttons: two rows, like the desktop Font and Paragraph groups.
            iconItems.chunked(2).forEach { column ->
                Column {
                    column.forEach { item -> RibbonIconButton(state, item, onInvoke) }
                }
            }
            // Text buttons: three per column.
            textItems.chunked(3).forEach { column ->
                Column(Modifier.padding(horizontal = 2.dp)) {
                    column.forEach { item -> RibbonTextButton(state, item, onInvoke) }
                }
            }
        }
        Text(group.name, fontSize = 10.sp, color = Color(0xFF616161), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RibbonIconButton(state: EditorState, item: RibbonItem, onInvoke: (RibbonItem) -> Unit) {
    val reason = state.disabled[item.id]
    Box(
        Modifier
            .size(46.dp)
            .alpha(if (reason != null) 0.38f else 1f)
            .clickable { if (reason != null) state.status = "${item.label}: $reason" else onInvoke(item) },
        contentAlignment = Alignment.Center
    ) {
        Icon(commandIcon(item.id) ?: Icons.Default.Search, item.label, Modifier.size(24.dp), tint = Color(0xFF242424))
    }
}

@Composable
private fun RibbonTextButton(state: EditorState, item: RibbonItem, onInvoke: (RibbonItem) -> Unit) {
    val reason = state.disabled[item.id]
    Box(
        Modifier
            .widthIn(min = 64.dp, max = 112.dp)
            .height(32.dp)
            .padding(vertical = 1.dp)
            .alpha(if (reason != null) 0.38f else 1f)
            .background(Color.White, RoundedCornerShape(6.dp))
            .clickable { if (reason != null) state.status = "${item.label}: $reason" else onInvoke(item) }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(item.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Asks for a command's parameters, with one field per parameter the engine describes. */
@Composable
fun ParamDialog(item: RibbonItem, onDismiss: () -> Unit, onRun: (String) -> Unit) {
    val fields = remember(item.id) { RibbonModel.fields(item.params) }
    val values = remember(item.id) { mutableStateMapOf<String, String>() }
    var rawJson by remember(item.id) { mutableStateOf("{}") }

    val canRun = if (fields.isEmpty()) true else fields.all { it.optional || !values[it.name].isNullOrBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.label) },
        text = {
            Column {
                if (fields.isEmpty()) {
                    Text("Parameters: ${item.params}", fontSize = 12.sp)
                    OutlinedTextField(value = rawJson, onValueChange = { rawJson = it }, label = { Text("JSON") })
                } else {
                    fields.forEach { f ->
                        val numeric = isNumeric(f.type)
                        OutlinedTextField(
                            value = values[f.name].orEmpty(),
                            onValueChange = { values[f.name] = it },
                            label = { Text(if (f.optional) "${f.name} (optional)" else f.name) },
                            supportingText = { Text(f.type, fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canRun, onClick = {
                onRun(if (fields.isEmpty()) rawJson else buildParams(fields, values))
            }) { Text("Run") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun isNumeric(type: String): Boolean {
    val t = type.lowercase()
    return Regex("\\b(n|number|int|integer|float|pt|px|cm|mm|ratio|percent|index|count)\\b").containsMatchIn(t)
}

private fun buildParams(fields: List<ParamField>, values: Map<String, String>): String {
    val out = JSONObject()
    for (f in fields) {
        val raw = values[f.name]?.trim().orEmpty()
        if (raw.isEmpty()) continue
        val t = f.type.lowercase()
        when {
            isNumeric(t) -> out.put(f.name, raw.toDoubleOrNull() ?: raw)
            t.contains("bool") -> out.put(f.name, raw.lowercase() in setOf("true", "1", "yes", "on"))
            raw.startsWith("[") -> out.put(f.name, runCatching { JSONArray(raw) }.getOrDefault(raw))
            raw.startsWith("{") -> out.put(f.name, runCatching { JSONObject(raw) }.getOrDefault(raw))
            else -> out.put(f.name, raw)
        }
    }
    return out.toString()
}

/** Search every engine command by name, location or id. */
@Composable
fun CommandPalette(all: List<RibbonItem>, onDismiss: () -> Unit, onPick: (RibbonItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query, all) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) all.take(60)
        else all.filter { it.label.lowercase().contains(q) || it.id.lowercase().contains(q) || it.tab.lowercase().contains(q) }.take(80)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search commands (${all.size})") },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("e.g. table, margins, find, track") }, modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.height(360.dp)) {
                    items(matches) { item ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(item) }.padding(vertical = 8.dp)) {
                            Text(item.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("${item.tab} · ${item.id}", fontSize = 11.sp, color = Color(0xFF667085))
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
