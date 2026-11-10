package org.wordcraft.android

import android.app.Application
import org.wordcraft.bridge.registerFonts
import uniffi.wordcraft_android.*
import java.io.File
import kotlin.concurrent.thread

/** Registers fonts once, before any document is laid out (the engine has no Android font path). */
class WordcraftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val userFonts = File(filesDir, "fonts").apply { mkdirs() }
        fontsReady = thread(name = "font-init") {
            runCatching { registerFonts(listOf("/system/fonts", "/product/fonts", userFonts.path)) }
        }
    }

    companion object {
        /** Join before the first layout so the first render never runs with an empty font set. */
        @Volatile var fontsReady: Thread? = null
    }
}
