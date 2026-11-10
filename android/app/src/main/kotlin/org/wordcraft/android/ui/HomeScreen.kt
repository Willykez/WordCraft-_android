package org.wordcraft.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.wordcraft.android.data.RecentDoc

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(recents: List<RecentDoc>, onNew: (String) -> Unit, onOpenFile: () -> Unit, onOpenRecent: (RecentDoc) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("WordCraft") }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button({ onNew("blank") }, Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text("  New document") }
                    OutlinedButton(onOpenFile, Modifier.weight(1f)) { Icon(Icons.Default.FolderOpen, null); Text("  Open") }
                }
            }
            item { Text("Templates", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Letter" to "letter", "Resume" to "resume", "Report" to "report", "Sample" to "sample").forEach { (n, k) ->
                        AssistChip({ onNew(k) }, { Text(n) }, leadingIcon = { Icon(Icons.Default.Description, null) })
                    }
                }
            }
            item { Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            if (recents.isEmpty()) item { Text("Documents you open or save appear here.", style = MaterialTheme.typography.bodyMedium) }
            items(recents, key = { it.uri }) { r ->
                ListItem(
                    headlineContent = { Text(r.name) },
                    leadingContent = { Icon(Icons.Default.Description, null) },
                    modifier = Modifier.clickable { onOpenRecent(r) },
                )
            }
        }
    }
}
