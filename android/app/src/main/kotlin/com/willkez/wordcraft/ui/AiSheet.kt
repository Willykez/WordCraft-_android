@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.willkez.wordcraft.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willkez.wordcraft.ai.AiPlan
import com.willkez.wordcraft.ai.AiRunResult
import com.willkez.wordcraft.editor.EditorViewModel
import kotlinx.coroutines.launch

private val EDIT_EXAMPLES = listOf(
    "Make every heading dark blue and bold",
    "Add a box border to the first page only",
    "Insert a 3 by 4 table",
    "Center the title and make it 24 pt",
)
private val WRITE_EXAMPLES = listOf(
    "Write a short cover letter for a junior developer job",
    "Continue this text for one more paragraph",
    "Summarise the document in three sentences",
)

/**
 * The AI assistant. "Format and edit" asks the model for a plan of editor commands, shows it, and runs it only when
 * you press Apply, as steps you can undo in one tap. "Write text" streams new text into the page at the caret.
 * Nothing is sent until you press Run, and only the instruction and the document's text go to Google's Gemini API.
 */
@Composable
fun AiSheet(vm: EditorViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val saved = remember { vm.aiSettings() }
    var key by remember { mutableStateOf(saved.first) }
    var model by remember { mutableStateOf(saved.second) }
    var showSettings by remember { mutableStateOf(saved.first.isBlank()) }
    var mode by remember { mutableStateOf("edit") }
    var instruction by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var plan by remember { mutableStateOf<AiPlan?>(null) }
    var result by remember { mutableStateOf<AiRunResult?>(null) }
    var wrote by remember { mutableStateOf(false) }
    val streaming by vm.aiStreaming.collectAsState()
    val working = thinking || streaming

    DisposableEffect(Unit) { onDispose { vm.aiStop() } }

    fun run() {
        vm.saveAiSettings(key, model)
        error = null; plan = null; result = null; wrote = false
        if (mode == "edit") {
            thinking = true
            scope.launch {
                try { plan = vm.aiPlan(instruction) }
                catch (e: Exception) { error = e.message ?: "The AI request failed" }
                finally { thinking = false }
            }
        } else {
            vm.aiWrite(instruction) { err -> error = err; wrote = err == null }
        }
    }

    AppSheet(onDismiss) {
        SheetTitle("AI assistant", "Beta")
        SheetScroll {
            PillRow {
                Pill("Format and edit", mode == "edit") { if (!working) { mode = "edit"; plan = null; result = null; error = null; wrote = false } }
                Pill("Write text", mode == "write") { if (!working) { mode = "write"; plan = null; result = null; error = null; wrote = false } }
            }
            OutlinedTextField(
                instruction, { instruction = it }, Modifier.fillMaxWidth().padding(top = 10.dp), minLines = 3, maxLines = 6, enabled = !working,
                label = { Text(if (mode == "edit") "What should change in the document?" else "What should be written at the cursor?") },
            )
            PillRow { (if (mode == "edit") EDIT_EXAMPLES else WRITE_EXAMPLES).forEach { ex -> Pill(ex, false) { if (!working) instruction = ex } } }

            if (thinking) Text("Working out the steps…", Modifier.padding(top = 10.dp), fontSize = 13.sp, color = SheetMuted)
            if (streaming) Text("Writing into the document…", Modifier.padding(top = 10.dp), fontSize = 13.sp, color = SheetMuted)
            if (wrote && !streaming) Text("Text inserted at the cursor. Use Undo in the toolbar to take it back.", Modifier.padding(top = 10.dp), fontSize = 13.sp, color = SheetInk)

            plan?.let { p ->
                SectionLabel("Plan")
                Text(p.summary, fontSize = 14.sp, color = SheetInk)
                p.steps.forEachIndexed { i, st ->
                    val detail = st.params.toString().takeIf { it != "{}" }?.take(70)
                    Text("${i + 1}. ${vm.aiLabel(st.command)}${if (detail != null) "  $detail" else ""}", fontSize = 13.sp, color = SheetMuted, modifier = Modifier.padding(top = 3.dp))
                }
                if (p.steps.isEmpty()) Text("Nothing to apply.", fontSize = 13.sp, color = SheetMuted, modifier = Modifier.padding(top = 4.dp))
            }
            result?.let { r ->
                SectionLabel("Done")
                Text("${r.applied} step(s) applied.", fontSize = 14.sp, color = SheetInk)
                r.errors.forEach { Text("Skipped: $it", fontSize = 12.sp, color = Danger, modifier = Modifier.padding(top = 3.dp)) }
            }
            error?.let { Text(it, Modifier.padding(top = 10.dp), fontSize = 13.sp, color = Danger) }

            TextButton({ showSettings = !showSettings }, Modifier.padding(top = 8.dp)) { Text(if (showSettings) "Hide AI settings" else "AI settings") }
            if (showSettings) {
                OutlinedTextField(
                    key, { key = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Gemini API key") },
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true, label = { Text("Model") })
                Text(
                    "Uses your own Google AI Studio key, stored only on this device. Pressing Run sends your instruction and the document's text to Google's Gemini API. Nothing is sent before that.",
                    fontSize = 12.sp, color = SheetMuted, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        when {
            working -> PrimaryButton("Stop") { vm.aiStop(); thinking = false }
            result != null -> {
                PrimaryButton("Keep changes") { onDismiss() }
                TextButton({ vm.aiUndo(result!!.undoSteps); result = null; plan = null }, Modifier.fillMaxWidth()) { Text("Undo these changes") }
            }
            plan != null -> {
                PrimaryButton("Apply ${plan!!.steps.size} change(s)", enabled = plan!!.steps.isNotEmpty()) {
                    val p = plan!!
                    scope.launch { thinking = true; result = vm.aiApply(p); thinking = false }
                }
                TextButton({ plan = null }, Modifier.fillMaxWidth()) { Text("Discard") }
            }
            else -> PrimaryButton(if (key.isBlank()) "Add your API key in AI settings" else "Run", enabled = key.isNotBlank() && instruction.isNotBlank()) { run() }
        }
    }
}
