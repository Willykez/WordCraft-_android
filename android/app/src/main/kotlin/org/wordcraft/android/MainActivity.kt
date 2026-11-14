package org.wordcraft.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import org.wordcraft.android.editor.EditorViewModel
import org.wordcraft.android.ui.EditorActions
import org.wordcraft.android.ui.EditorScreen
import org.wordcraft.android.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blue bars at the top get light status-bar icons; the white bottom bar gets dark navigation icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) intent?.data?.let { vm.openDocument(it) }
        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            // One branded palette everywhere (wallpaper colours made dialogs and chips clash with the Word-blue UI).
            val scheme = if (dark) darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF8AB4F8))
            else lightColorScheme(
                primary = org.wordcraft.android.ui.WordBlue, onPrimary = androidx.compose.ui.graphics.Color.White,
                secondaryContainer = androidx.compose.ui.graphics.Color(0xFFDCE6F7), onSecondaryContainer = org.wordcraft.android.ui.WordBlueDark,
            )
            MaterialTheme(colorScheme = scheme) { Root() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { vm.openDocument(it) }
    }

    /** Leaving the app saves a document that came from a file, so typing is never lost. */
    override fun onStop() {
        vm.autosaveNow()
        vm.saveIfDirty()
        super.onStop()
    }

    @androidx.compose.runtime.Composable
    private fun Root() {
        val ui by vm.ui.collectAsState()
        var pendingFormat by remember { mutableStateOf<String?>(null) }
        var password by remember { mutableStateOf("") }

        val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                vm.openDocument(it)
            }
        }
        // One launcher; the mime type only seeds the picker, the extension we pass decides the format.
        val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val fmt = pendingFormat
            if (uri != null && fmt != null) {
                runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                // Save-as to docx/odt becomes the document's new home; pdf/txt/html are copies.
                vm.saveAs(uri, fmt, copyOnly = fmt !in setOf("docx", "odt"))
            }
            pendingFormat = null
        }
        val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            uri?.let { contentResolver.openInputStream(it)?.use { s -> vm.insertPicture(s.readBytes()) } }
        }

        val actions = object : EditorActions {
            override fun save() = vm.save { askLocation("docx") }
            override fun saveCopy(format: String) = askLocation(format)
            private fun askLocation(fmt: String) { pendingFormat = fmt; createDoc.launch(vm.suggestedName(fmt)) }
            override fun openFile() = openDoc.launch(arrayOf("*/*"))
            override fun pickPicture() = pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            override fun copy(cut: Boolean) = vm.copy(cut) { text ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("WordCraft", text))
            }
            override fun paste() {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()?.let(vm::paste)
            }
            override fun close() = vm.requestClose()
        }

        if (ui.open) {
            BackHandler { vm.requestClose() }
            EditorScreen(vm, actions)
        } else {
            val recents by vm.recents.collectAsState()
            val recovery by vm.recovery.collectAsState()
            HomeScreen(
                recents = recents,
                recovery = recovery,
                onRecover = vm::recoverDocument,
                onDiscardRecovery = vm::discardRecovery,
                onNew = vm::newDocument,
                onOpenFile = { openDoc.launch(arrayOf("*/*")) },
                onOpenRecent = { r ->
                    if (r.editable) vm.openDocument(Uri.parse(r.uri))
                    else runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { vm.say("No app can open ${r.name}") }
                },
                onPin = { vm.pinRecent(it.uri) },
                onRemove = { vm.forgetRecent(it.uri) },
                onClear = vm::clearRecents,
                thumbFor = vm::thumbnail,
            )
        }

        // Last launch ended in a crash: offer the report, which only leaves the device if the user shares it.
        var crash by remember { mutableStateOf(java.io.File(filesDir, "crash.log").takeIf { it.exists() }?.readText()) }
        crash?.let { report ->
            AlertDialog(
                onDismissRequest = { java.io.File(filesDir, "crash.log").delete(); crash = null },
                title = { Text("WordCraft closed unexpectedly") },
                text = { Text("Your work is safe: it is autosaved while you type. You can share the crash report to help get it fixed.") },
                confirmButton = {
                    TextButton({
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Share crash report"))
                        java.io.File(filesDir, "crash.log").delete(); crash = null
                    }) { Text("Share report") }
                },
                dismissButton = { TextButton({ java.io.File(filesDir, "crash.log").delete(); crash = null }) { Text("Dismiss") } },
            )
        }

        if (ui.needsPassword) {
            AlertDialog(
                onDismissRequest = { vm.dismissPassword() },
                title = { Text("Password required") },
                text = { OutlinedTextField(password, { password = it }, singleLine = true, label = { Text("Password") }) },
                confirmButton = { TextButton({ val u = pendingOpenUri; vm.dismissPassword(); u?.let { vm.openDocument(it, password) } }) { Text("Open") } },
                dismissButton = { TextButton({ vm.dismissPassword() }) { Text("Cancel") } },
            )
        }
    }

    private val pendingOpenUri: Uri? get() = vm.pendingPasswordUri
}
