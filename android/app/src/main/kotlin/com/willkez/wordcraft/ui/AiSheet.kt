package com.willkez.wordcraft.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willkez.wordcraft.ai.AgentPlan
import com.willkez.wordcraft.ai.AgentRunResult
import com.willkez.wordcraft.ai.AiAssistant
import com.willkez.wordcraft.ai.AiConfig
import com.willkez.wordcraft.ai.AiPrompts
import com.willkez.wordcraft.ai.AiProviders
import com.willkez.wordcraft.ai.ProviderKind
import com.willkez.wordcraft.ai.AiPhase
import com.willkez.wordcraft.ai.AiPlans
import com.willkez.wordcraft.ai.AiState
import com.willkez.wordcraft.ai.TextTask
import com.willkez.wordcraft.editor.EditorViewModel

/** Home › AI Assistant: write onto the page, rewrite, fix, tables, charts and headings. Everything is one Undo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSheet(vm: EditorViewModel, onDismiss: () -> Unit) {
    val ai = vm.ai
    val st by ai.state.collectAsState()
    val cfg = ai.config.collectAsState().value
    var setup by remember { mutableStateOf(!cfg.ready) }
    var prompt by remember { mutableStateOf("") }
    var applyError by remember { mutableStateOf<String?>(null) }
    var agent by remember { mutableStateOf(false) }

    // Closing the sheet while a list of changes is being prepared ends that request, as Stop does.
    DisposableEffect(Unit) { onDispose { if (ai.state.value.phase == AiPhase.Running || ai.state.value.phase == AiPhase.Answering) ai.stop() } }

    // Live writing happens on the page itself, so the sheet gets out of the way as soon as it starts.
    fun write(start: () -> Unit) { applyError = null; start(); onDismiss() }

    AppSheet(onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SheetTitle("AI Assistant", "Home › AI") }
            IconButton({ setup = !setup }) { Icon(Icons.Default.Settings, "AI settings", tint = SheetMuted) }
        }

        when {
            setup -> SetupPane(ai, cfg, onSaved = { setup = false })
            agent -> AgentPane(vm, onBack = { agent = false }, onDismiss = onDismiss)
            st.phase == AiPhase.Running -> RunningPane(st.title, st.streamed, onStop = ai::stop)
            st.phase == AiPhase.Review -> ReviewPane(vm, st, applyError, onApplyError = { applyError = it }, onDone = { ai.reset(); onDismiss() })
            st.phase == AiPhase.Writing -> RunningPane(st.title, "${st.written} characters written to the page", onStop = ai::stop)
            st.phase == AiPhase.Written -> WrittenPane(vm, st, onDone = { ai.reset(); onDismiss() })
            st.phase == AiPhase.Answering || st.phase == AiPhase.Answered -> AnswerPane(vm, st, onDone = onDismiss)
            else -> {
                OutlinedTextField(
                    prompt, { prompt = it }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                    label = { Text("Ask for anything") },
                    placeholder = { Text("e.g. Write a page about network topologies with a table comparing them") },
                )
                PrimaryButton("Write on the page", prompt.isNotBlank()) { write { vm.aiAsk(prompt) } }
                OutlinedButton({ applyError = null; vm.aiQuestion(prompt) }, Modifier.fillMaxWidth().padding(top = 6.dp), enabled = prompt.isNotBlank()) {
                    Text("Ask about the document (answer here, nothing changes)")
                }
                Text(
                    "It writes at the cursor, or replaces the selected text. It can write headings, lists, tables and charts.",
                    Modifier.padding(top = 6.dp), fontSize = 12.sp, color = SheetMuted,
                )

                SectionLabel("Selected text")
                PillRow {
                    AiPrompts.selectionTasks.forEach { t ->
                        Pill(t.label, false) { write { vm.aiText(t) } }
                    }
                }
                SectionLabel("Document")
                PillRow {
                    Pill(TextTask.Continue.label, false) { write { vm.aiText(TextTask.Continue) } }
                    Pill("Fix whole document", false) { applyError = null; vm.aiFixDocument() }
                    Pill("Format headings", false) {
                        applyError = null
                        vm.aiReview("Apply Title and Heading styles to the paragraphs that are really titles or section headings. Change nothing else.")
                    }
                    Pill("Add a table…", false) { prompt = "Add a table of " }
                    Pill("Add a chart…", false) { prompt = "Add a chart of " }
                }
                SectionLabel("Formatting")
                PillRow { Pill("Change formatting… (borders, themes, fonts, links)", false) { agent = true } }
                st.error?.let { Text(it, Modifier.padding(top = 12.dp), fontSize = 13.sp, color = Danger) }
                Text(
                    "One Undo reverts everything the assistant writes. Your request${if (cfg.shareDocument) " and the document text" else " and the selection"} go to ${cfg.info.label} (${cfg.model}).",
                    Modifier.padding(top = 14.dp), fontSize = 12.sp, color = SheetMuted,
                )
            }
        }
        DoneButton("Close", onDismiss)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupPane(ai: AiAssistant, current: AiConfig, onSaved: () -> Unit) {
    var provider by remember { mutableStateOf(current.provider) }
    var cfg by remember(provider) { mutableStateOf(if (provider == current.provider) current else ai.configFor(provider)) }
    val info = AiProviders.byId(provider)
    SheetScroll(0.78f) {
        Text("AI provider", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetInk)
        Text("Pick who answers. Each provider keeps its own key and model, so you can switch any time.", Modifier.padding(top = 2.dp, bottom = 6.dp), fontSize = 12.sp, color = SheetMuted)
        PillRow { AiProviders.all.forEach { p -> Pill(p.label, provider == p.id) { provider = p.id } } }

        Text(info.keyHelp, Modifier.padding(top = 10.dp), fontSize = 12.sp, color = SheetMuted)
        OutlinedTextField(
            cfg.key, { cfg = cfg.copy(key = it) }, Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true,
            label = { Text(if (info.keyOptional) "API key (optional)" else "API key") }, visualTransformation = PasswordVisualTransformation(),
        )
        if (info.customUrl) {
            OutlinedTextField(
                cfg.baseUrl, { cfg = cfg.copy(baseUrl = it) }, Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true,
                label = { Text("Server address") }, placeholder = { Text("https://…/v1") },
            )
        }
        if (info.presets.isNotEmpty()) {
            SectionLabel("Model")
            PillRow { info.presets.forEach { (label, id) -> Pill(label, cfg.model == id) { cfg = cfg.copy(model = id) } } }
        }
        OutlinedTextField(
            cfg.model, { cfg = cfg.copy(model = it) }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
            label = { Text("Model name") }, supportingText = { Text("Names change often. If one is rejected, copy the exact name from the provider's dashboard.") },
        )
        if (info.kind == ProviderKind.Gemini) SwitchRow("Use a backup model when the free quota runs out", cfg.autoFallback) { cfg = cfg.copy(autoFallback = it) }
        SwitchRow("Send the document's text with requests", cfg.shareDocument) { cfg = cfg.copy(shareDocument = it) }
        Text(
            if (cfg.shareDocument) "On: the assistant sees the document (up to a size limit) and answers better."
            else "Off: only the selected text and your instruction are sent.",
            fontSize = 12.sp, color = SheetMuted,
        )
        if (info.note.isNotBlank()) Text(info.note, Modifier.padding(top = 8.dp), fontSize = 12.sp, color = SheetInk)
        Text("Your key is stored only on this device and is sent only to the provider you chose.", Modifier.padding(top = 8.dp), fontSize = 12.sp, color = SheetMuted)
    }
    PrimaryButton("Save", cfg.copy(provider = provider).ready) { ai.saveConfig(cfg.copy(provider = provider)); onSaved() }
}

@Composable
private fun AnswerPane(vm: EditorViewModel, st: AiState, onDone: () -> Unit) {
    val ai = vm.ai
    val clipboard = LocalClipboardManager.current
    if (st.phase == AiPhase.Answering) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(st.title.ifBlank { "Reading the document…" }, Modifier.padding(start = 10.dp), fontSize = 14.sp, color = SheetInk)
        }
    }
    SheetScroll(0.55f) { SelectionContainer { Text(st.streamed, Modifier.padding(top = 8.dp), fontSize = 14.sp, color = SheetInk) } }
    st.error?.let { Text(it, Modifier.padding(top = 8.dp), fontSize = 13.sp, color = Danger) }
    if (st.phase == AiPhase.Answering) {
        PrimaryButton("Stop", true, ai::stop)
    } else {
        PrimaryButton("Insert at the cursor", st.streamed.isNotBlank()) { vm.aiInsertAnswer(st.streamed); ai.reset(); onDone() }
        OutlinedButton({ clipboard.setText(AnnotatedString(st.streamed)) }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = st.streamed.isNotBlank()) { Text("Copy") }
        OutlinedButton({ ai.reset() }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Ask something else") }
    }
}

@Composable
private fun RunningPane(title: String, detail: String, onStop: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(title.ifBlank { "Working…" }, Modifier.padding(start = 10.dp), fontSize = 14.sp, color = SheetInk)
    }
    if (detail.isNotBlank()) Text(detail, Modifier.padding(top = 8.dp), fontSize = 13.sp, color = SheetMuted)
    PrimaryButton("Stop", true, onStop)
}

@Composable
private fun WrittenPane(vm: EditorViewModel, st: AiState, onDone: () -> Unit) {
    Text("Written to the page.", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetInk)
    st.error?.let { Text(it, Modifier.padding(top = 6.dp), fontSize = 13.sp, color = Danger) }
    st.warning?.let { Text("Some steps were skipped: $it", Modifier.padding(top = 6.dp), fontSize = 13.sp, color = SheetMuted) }
    if (st.canContinue) OutlinedButton({ vm.aiContinue(); onDone() }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Continue writing") }
    PrimaryButton("Keep", true, onDone)
    OutlinedButton({ vm.aiUndoWrite(); onDone() }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Undo everything it wrote") }
}

@Composable
private fun ReviewPane(vm: EditorViewModel, st: AiState, applyError: String?, onApplyError: (String?) -> Unit, onDone: () -> Unit) {
    val ai = vm.ai
    val plan = st.plan
    var picked by remember(plan) { mutableStateOf(plan?.ops?.indices?.toSet() ?: emptySet()) }

    if (plan != null) {
        Text(plan.summary.ifBlank { "Proposed changes" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetInk)
        if (plan.ops.isEmpty()) {
            Text("Nothing to change.", Modifier.padding(top = 8.dp), fontSize = 13.sp, color = SheetMuted)
        } else {
            SheetScroll(0.7f) {
                plan.ops.forEachIndexed { i, op ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                        Checkbox(i in picked, { on -> picked = if (on) picked + i else picked - i })
                        Text(AiPlans.describe(op), Modifier.padding(top = 12.dp), fontSize = 13.sp, color = SheetInk)
                    }
                }
            }
            if (plan.skipped > 0) Text("${plan.skipped} suggestion(s) could not be used and were left out.", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = SheetMuted)
            PrimaryButton(if (picked.size == plan.ops.size) "Accept all" else "Accept ${picked.size}", picked.isNotEmpty()) {
                val chosen = plan.ops.filterIndexed { i, _ -> i in picked }
                vm.aiApply(AiPlans.commands(chosen).toString()) { err -> if (err == null) onDone() else onApplyError(err) }
            }
        }
    }
    applyError?.let { Text(it, Modifier.padding(top = 8.dp), fontSize = 13.sp, color = Danger) }
    OutlinedButton({ onApplyError(null); ai.reset() }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Reject") }
}

/**
 * Over the page while the assistant writes: a Stop button, and afterwards Keep / Undo. While it writes, touches on the
 * page are ignored, so nothing the user types or taps can land in the text being written.
 */
@Composable
fun AiLiveBar(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val st by vm.ai.state.collectAsState()
    if (st.phase != AiPhase.Writing && st.phase != AiPhase.Written) return

    if (st.phase == AiPhase.Writing) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() } })
    }
    Surface(modifier.padding(12.dp), shape = RoundedCornerShape(28.dp), color = SurfaceWhite, contentColor = RibbonInk, shadowElevation = 8.dp, tonalElevation = 0.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (st.phase == AiPhase.Writing) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Writing… ${st.written}", fontSize = 13.sp)
                TextButton({ vm.ai.stop() }) { Text("Stop") }
            } else {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = WordBlue)
                Text(
                    st.error ?: st.warning?.let { "Written, but: $it" } ?: "Written", fontSize = 12.sp, maxLines = 3,
                    color = if (st.error != null) Danger else RibbonInk, modifier = Modifier.widthIn(max = 220.dp),
                )
                if (st.canContinue) TextButton({ vm.aiContinue() }) { Text("Continue") }
                TextButton({ vm.ai.reset() }) { Text("Keep") }
                TextButton({ vm.aiUndoWrite() }) { Text("Undo") }
            }
        }
    }
}


/**
 * "Change formatting": the model chooses editor commands (page borders, themes, fonts, strikethrough, links, table
 * styling and the rest of the ribbon), you review the steps, and one Undo takes the whole run back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentPane(vm: EditorViewModel, onBack: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var instruction by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var plan by remember { mutableStateOf<AgentPlan?>(null) }
    var picked by remember(plan) { mutableStateOf(plan?.steps?.indices?.toSet() ?: emptySet()) }
    var result by remember { mutableStateOf<AgentRunResult?>(null) }
    val examples = listOf("Add a box border to the first page only", "Make every heading dark blue", "Strike through the selected text", "Use the Darkroom theme")

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onBack) { Text("‹ Back") }
        Text("Change formatting", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SheetInk)
    }
    if (result == null) {
        OutlinedTextField(
            instruction, { instruction = it }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, enabled = !thinking && plan == null,
            label = { Text("What should change?") },
        )
        if (plan == null) PillRow { examples.forEach { ex -> Pill(ex, false) { if (!thinking) instruction = ex } } }
    }
    if (thinking) Text("Working out the steps…", Modifier.padding(top = 10.dp), fontSize = 13.sp, color = SheetMuted)

    plan?.let { p ->
        if (result == null) {
            SectionLabel(p.summary)
            if (p.steps.isEmpty()) Text("Nothing to apply.", fontSize = 13.sp, color = SheetMuted)
            SheetScroll(0.5f) {
                p.steps.forEachIndexed { i, st ->
                    val detail = st.params.toString().takeIf { it != "{}" }?.take(80)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Checkbox(i in picked, { on -> picked = if (on) picked + i else picked - i })
                        Text(
                            vm.agentLabel(st.command) + if (detail != null) "  $detail" else "",
                            Modifier.padding(top = 12.dp), fontSize = 13.sp, color = SheetInk,
                        )
                    }
                }
            }
        }
    }
    result?.let { r ->
        SectionLabel("Done")
        Text("${r.applied} step(s) applied.", fontSize = 14.sp, color = SheetInk)
        r.errors.forEach { Text("Skipped: $it", Modifier.padding(top = 3.dp), fontSize = 12.sp, color = Danger) }
    }
    error?.let { Text(it, Modifier.padding(top = 10.dp), fontSize = 13.sp, color = Danger) }

    when {
        thinking -> {}
        result != null -> {
            PrimaryButton("Keep changes", true, onDismiss)
            OutlinedButton({ vm.undo(); result = null; plan = null }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Undo these changes") }
        }
        plan != null -> {
            PrimaryButton("Apply ${picked.size} step(s)", picked.isNotEmpty()) {
                val p = plan!!
                val chosen = AgentPlan(p.summary, p.steps.filterIndexed { i, _ -> i in picked })
                scope.launch { thinking = true; result = vm.agentApply(chosen); thinking = false }
            }
            OutlinedButton({ plan = null }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Discard") }
        }
        else -> PrimaryButton("Plan the changes", instruction.isNotBlank()) {
            error = null
            thinking = true
            scope.launch {
                try { plan = vm.agentPlan(instruction) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "The AI request failed" }
                finally { thinking = false }
            }
        }
    }
}

/** Selection toolbar entry: rewrite, fix, shorten, expand, formalise or summarise the selected text in one tap. */
@Composable
fun AiQuickMenu(vm: EditorViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }, Modifier.size(34.dp)) { Icon(Icons.Default.AutoAwesome, "AI actions for the selection", Modifier.size(20.dp), tint = PalettePurple) }
        DropdownMenu(open, { open = false }) {
            AiPrompts.selectionTasks.forEach { t ->
                DropdownMenuItem(text = { Text(t.label) }, onClick = { open = false; vm.aiText(t) })
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Ask the AI…") }, onClick = { open = false; vm.openPanel("ai") })
        }
    }
}
