package org.wordcraft.android

import android.app.Application
import org.wordcraft.bridge.registerFonts
import java.io.File
import kotlin.concurrent.thread

/** Registers fonts once, before any document is laid out (the engine has no Android font path). */
class WordcraftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        val userFonts = File(filesDir, "fonts").apply { mkdirs() }
        fontsReady = thread(name = "font-init") {
            runCatching { registerFonts(listOf("/system/fonts", "/product/fonts", userFonts.path)) }
        }
    }

    /** Keeps the last crash on the device (never sent anywhere) so it can be shared on purpose next launch. */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                File(filesDir, "crash.log").writeText("WordCraft crash $stamp on thread ${t.name}\n${android.util.Log.getStackTraceString(e)}".take(20_000))
            }
            previous?.uncaughtException(t, e)
        }
    }

    companion object {
        /** Join before the first layout so the first render never runs with an empty font set. */
        @Volatile var fontsReady: Thread? = null
    }
}
