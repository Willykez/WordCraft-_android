package com.willkez.wordcraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Where documents are saved: one folder, chosen once, used by every new document, Save As and export. */
@Composable
fun SaveFolderDialog(folder: String?, askEveryTime: Boolean, onChoose: () -> Unit, onAskEveryTime: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (folder != null) "New documents, Save As and exports go to:" else "No folder chosen yet. Pick one once and WordCraft will save there without asking again.",
                    fontSize = 14.sp,
                )
                if (folder != null) {
                    Text(
                        folder, Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(tint(PaletteOrange, 0.14f)).padding(12.dp),
                        fontWeight = FontWeight.SemiBold, color = PaletteOrange,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ask for a folder every time", Modifier.weight(1f), fontSize = 14.sp)
                    Switch(askEveryTime, onAskEveryTime)
                }
                Text("Files that already exist get a number: report.pdf, report(1).pdf, report(2).pdf.", fontSize = 12.sp, color = TextMuted)
            }
        },
        confirmButton = { TextButton(onChoose) { Text(if (folder == null) "Choose folder" else "Change folder") } },
        dismissButton = { TextButton(onDismiss) { Text("Done") } },
    )
}

/** The app's accent theme (title bar, buttons, headers). Document colours are chosen separately in the ribbon. */
@Composable
fun ThemeDialog(selected: Int, mode: Int, onPick: (Int) -> Unit, onMode: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App theme") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Appearance", fontSize = 12.sp, color = SheetMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Phone setting", "Light", "Dark").forEachIndexed { i, n -> Pill(n, mode == i) { onMode(i) } }
                }
                Text("Accent colour", fontSize = 12.sp, color = SheetMuted, modifier = Modifier.padding(top = 6.dp))
                AppTheme.specs.forEachIndexed { i, spec ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .border(if (i == selected) 2.dp else 1.dp, if (i == selected) spec.primary else SheetLine, RoundedCornerShape(12.dp))
                            .clickable { onPick(i) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(34.dp).background(Brush.linearGradient(spec.gradient), CircleShape))
                        Text(spec.name, Modifier.padding(start = 12.dp).weight(1f), fontWeight = FontWeight.Medium)
                        if (i == selected) Text("✓", color = spec.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
    )
}
