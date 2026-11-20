package com.willkez.wordcraft

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.willkez.wordcraft.data.DeviceDocs
import com.willkez.wordcraft.data.DocumentStore
import com.willkez.wordcraft.ui.AppTheme
import com.willkez.wordcraft.ui.ExplorerScreen
import com.willkez.wordcraft.ui.SaveFolderDialog
import com.willkez.wordcraft.ui.ThemeDialog
import kotlinx.coroutines.flow.MutableStateFlow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import com.willkez.wordcraft.editor.EditorViewModel
import com.willkez.wordcraft.ui.EditorActions
import com.willkez.wordcraft.ui.EditorScreen
import com.willkez.wordcraft.ui.HomeScreen

/** "Save as" picker whose MIME type matches the format, so the system never renames `file.pdf` to `file.pdf (1)`. */
private class CreateTyped : ActivityResultContracts.CreateDocument("application/octet-stream") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).setType(DocumentStore.mimeFor(input.substringAfterLast('.', "")))
}

class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()
    /** Bumped whenever the app returns to the front (after the "All files access" settings page, say). */
    private val resumeTick = MutableStateFlow(0)

    override fun onResume() {
        super.onResume()
        resumeTick.value++
    }

    /** View a file in another app (PDFs and anything the editor cannot open). */
    private fun openExternally(uriString: String, name: String) {
        runCatching {
            var uri = Uri.parse(uriString)
            if (uri.scheme == "file") uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", java.io.File(uri.path!!))
            startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, DocumentStore.mimeFor(name.substringAfterLast('.', "")))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { vm.say("No app can open $name") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blue bars at the top get light status-bar icons; the white bottom bar gets dark navigation icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) intent?.data?.let { vm.openDocument(it) }
        AppTheme.index = getSharedPreferences("settings", MODE_PRIVATE).getInt("theme", 0)
        setContent {
            val dark = isSystemInDarkTheme()
            val ctx = LocalContext.current
            // One branded palette everywhere (wallpaper colours made dialogs and chips clash with the Word-blue UI).
            val accent = com.willkez.wordcraft.ui.WordBlue
            val scheme = if (dark) darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF8AB4F8))
            else lightColorScheme(
                primary = accent, onPrimary = androidx.compose.ui.graphics.Color.White,
                secondary = com.willkez.wordcraft.ui.PaletteTeal, tertiary = com.willkez.wordcraft.ui.PalettePink,
                primaryContainer = com.willkez.wordcraft.ui.tint(accent, 0.18f), onPrimaryContainer = com.willkez.wordcraft.ui.WordBlueDark,
                secondaryContainer = com.willkez.wordcraft.ui.tint(accent, 0.16f), onSecondaryContainer = com.willkez.wordcraft.ui.WordBlueDark,
                tertiaryContainer = com.willkez.wordcraft.ui.tint(com.willkez.wordcraft.ui.PalettePink, 0.16f),
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
        var pendingFolderAction by remember { mutableStateOf<((Uri) -> Unit)?>(null) }
        var folderDialog by remember { mutableStateOf(false) }
        var themeDialog by remember { mutableStateOf(false) }
        var explorer by remember { mutableStateOf(false) }
        val saveFolder by vm.saveFolder.collectAsState()
        val askEveryTime by vm.askEveryTime.collectAsState()
        val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val then = pendingFolderAction; pendingFolderAction = null
            if (uri != null) {
                runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                vm.setSaveTree(uri)
                then?.invoke(uri)
            }
        }
        val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.scanDevice() }
        val createDoc = rememberLauncherForActivityResult(remember { CreateTyped() }) { uri ->
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
            /** The permanent folder when one is chosen; otherwise pick it once now; or the system picker if "ask every time" is on. */
            private fun askLocation(fmt: String) {
                val copyOnly = fmt !in setOf("docx", "odt")
                val tree = if (askEveryTime) null else vm.saveTree()
                when {
                    tree != null -> vm.saveToFolder(tree, fmt, copyOnly)
                    askEveryTime -> { pendingFormat = fmt; createDoc.launch(vm.suggestedName(fmt)) }
                    else -> { pendingFolderAction = { t -> vm.saveToFolder(t, fmt, copyOnly) }; pickFolder.launch(null) }
                }
            }
            override fun chooseSaveFolder() { folderDialog = true }
            override fun chooseTheme() { themeDialog = true }
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
        } else if (explorer) {
            val device by vm.device.collectAsState()
            val scanning by vm.scanning.collectAsState()
            val resume by resumeTick.collectAsState()
            val fullAccess = remember(resume) { DeviceDocs.hasFullAccess(this@MainActivity) }
            BackHandler { explorer = false }
            LaunchedEffect(resume) { vm.scanDevice() }
            ExplorerScreen(
                docs = device, scanning = scanning, fullAccess = fullAccess,
                onBack = { explorer = false }, onRefresh = vm::scanDevice,
                onGrantAccess = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))) }
                            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
                    } else storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                },
                onOpen = { d -> if (d.editable) { explorer = false; vm.openDocument(Uri.parse(d.uri)) } else openExternally(d.uri, d.name) },
                onBrowse = { openDoc.launch(arrayOf("*/*")) },
            )
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
                    if (r.editable) vm.openDocument(Uri.parse(r.uri)) else openExternally(r.uri, r.name)
                },
                onPin = { vm.pinRecent(it.uri) },
                onRemove = { vm.forgetRecent(it.uri) },
                onClear = vm::clearRecents,
                thumbFor = vm::thumbnail,
                onExplore = { explorer = true },
                onSaveFolder = { folderDialog = true },
                onTheme = { themeDialog = true },
                saveFolderName = saveFolder,
            )
        }

        if (folderDialog) {
            SaveFolderDialog(
                folder = saveFolder, askEveryTime = askEveryTime,
                onChoose = { pendingFolderAction = null; pickFolder.launch(null) },
                onAskEveryTime = vm::setAskEveryTime, onDismiss = { folderDialog = false },
            )
        }
        if (themeDialog) {
            ThemeDialog(
                selected = AppTheme.index,
                onPick = { AppTheme.index = it; getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("theme", it).apply() },
                onDismiss = { themeDialog = false },
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
