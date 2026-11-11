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
import androidx.activity.compose.setContent
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
        if (savedInstanceState == null) intent?.data?.let { vm.openDocument(it) }
        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) { Root() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { vm.openDocument(it) }
    }

    /** Leaving the app saves a document that came from a file, so typing is never lost. */
    override fun onStop() {
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
            override fun close() = vm.closeDocument()
        }

        if (ui.open) {
            BackHandler { vm.closeDocument() }
            EditorScreen(vm, actions)
        } else {
            HomeScreen(
                recents = vm.recents,
                onNew = vm::newDocument,
                onOpenFile = { openDoc.launch(arrayOf("*/*")) },
                onOpenRecent = { vm.openDocument(Uri.parse(it.uri)) },
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
