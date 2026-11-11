package org.wordcraft.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import org.json.JSONTokener
import org.wordcraft.android.editor.EditorUiState
import org.wordcraft.android.editor.EditorViewModel
import kotlin.math.abs

private fun buildJson(cmd: Cmd, values: Map<String, String>): String {
    val o = JSONObject()
    for (f in cmd.fields) {
        val v = values[f.name]?.trim().orEmpty()
        when (f.kind) {
            FieldKind.BOOL -> if (values.containsKey(f.name)) o.put(f.name, v == "true")
            FieldKind.NUMBER -> v.toDoubleOrNull()?.let { o.put(f.name, if (it % 1.0 == 0.0 && abs(it) < 1e15) it.toLong() else it) }
            FieldKind.TEXT -> if (v.isNotEmpty()) o.put(f.name, v)
            FieldKind.JSON -> if (v.isNotEmpty()) o.put(f.name, runCatching { JSONTokener(v).nextValue() }.getOrDefault(v))
        }
    }
    return o.toString()
}

/** Asks for a command's inputs; the fields come from the engine's own parameter description. */
@Composable
fun ParamDialog(cmd: Cmd, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val values = remember { mutableStateMapOf<String, String>() }
    val missing = cmd.fields.any { !it.optional && values[it.name].isNullOrBlank() && it.kind != FieldKind.BOOL }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(cmd.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cmd.fields.forEach { f ->
                    val label = f.name + if (f.optional) " (optional)" else ""
                    when (f.kind) {
                        FieldKind.BOOL -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f))
                            Switch(values[f.name] == "true", { values[f.name] = it.toString() })
                        }
                        else -> Column {
                            OutlinedTextField(
                                values[f.name].orEmpty(), { values[f.name] = it }, Modifier.fillMaxWidth(), label = { Text(label) },
                                singleLine = f.kind != FieldKind.JSON,
                                keyboardOptions = KeyboardOptions(keyboardType = if (f.kind == FieldKind.NUMBER) KeyboardType.Decimal else KeyboardType.Text),
                                supportingText = if (f.hint.isNotEmpty()) ({ Text(f.hint, fontSize = 11.sp) }) else null,
                            )
                            if (f.options.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                f.options.forEach { o -> AssistChip({ values[f.name] = o }, { Text(o) }) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onSubmit(buildJson(cmd, values)) }, enabled = !missing) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ResultDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { Text(text, Modifier.verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}

/** Word's File tab: a full-screen page of document-level commands. */
@Composable
fun Backstage(ui: EditorUiState, vm: EditorViewModel, d: Dispatcher, actions: EditorActions) {
    fun go(block: () -> Unit) { vm.showBackstage(false); block() }
    Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F7F7)) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().background(WordBlue).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ vm.showBackstage(false) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Text("File", color = Color.White, fontSize = 18.sp)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                BsItem("New", Icons.Default.Description) { go { d.id("file.new") } }
                BsItem("Open", Icons.Default.FolderOpen) { go(actions::openFile) }
                BsItem("Save", Icons.Default.Save) { go(actions::save) }
                BsItem("Save as Word document (.docx)", Icons.Default.Save) { go { actions.saveCopy("docx") } }
                BsSection("Export")
                listOf("PDF" to "pdf", "OpenDocument text (.odt)" to "odt", "Plain text (.txt)" to "txt", "Web page (.html)" to "html")
                    .forEach { (n, f) -> BsItem(n, Icons.Default.Share) { go { actions.saveCopy(f) } } }
                BsItem("Print (as PDF)", Icons.Default.Print) { go { actions.saveCopy("pdf") } }
                BsItem("Document info", Icons.Default.Info) { go { vm.openPanel("info") } }
                BsItem("Close document", Icons.Default.Close) { go(actions::close) }
                val handled = setOf("file.new", "file.open", "file.save", "file.saveAs", "file.print", "file.close", "file.exportPdf", "file.info", "file.properties", "file.printMarkup")
                ui.ribbon?.groups("File").orEmpty().forEach { g ->
                    val cmds = g.cmds.filter { it.id !in handled }
                    if (cmds.isNotEmpty()) {
                        BsSection(g.name)
                        cmds.forEach { c -> BsItem(c.label, null, c.shortcut.substringBefore(" / ")) { go { d(c) } } }
                    }
                }
            }
        }
    }
}

@Composable
private fun BsSection(title: String) =
    Text(title.uppercase(), Modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp), fontSize = 11.sp, color = WordBlue)

@Composable
private fun BsItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, hint: String = "", onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, tint = WordBlue, modifier = Modifier.size(22.dp)) else Spacer(Modifier.width(22.dp))
        Text(label, Modifier.padding(start = 16.dp).weight(1f), fontSize = 15.sp)
        if (hint.isNotEmpty()) Text(hint, fontSize = 11.sp, color = Color.Gray)
    }
}
