package com.wordcraft.android

/** Android boundary. All document semantics stay in the original Rust engine. */
object NativeBridge {
    init {
        System.loadLibrary("wordcraft_android_bridge")
    }

    external fun engineStatus(): String
    external fun nativeGetText(): String
    external fun nativeGetSelection(): String
    external fun nativeSetText(text: String): String
    external fun nativeRunCommand(command: String, paramsJson: String = "{}"): String
    external fun nativeFormatAll(command: String): String
    external fun nativeUndo(): String
    external fun nativeRedo(): String
    external fun nativePageCount(): Int
    external fun nativeOpenDocument(name: String, bytes: ByteArray): String
    external fun nativeSaveDocument(name: String): ByteArray?
    external fun nativeGetLayoutSummary(): String
    external fun nativeCommandCatalog(): String

    /** Page/body sizes in points (JSON). */
    external fun nativeLayoutInfo(): String

    /** `[w:u32 LE][h:u32 LE][RGBA8 premultiplied pixels]` rendered by the engine, or null. */
    external fun nativeRenderPage(page: Int, scale: Float): ByteArray?

    /** Move caret / extend selection to the page point (points). JSON result. */
    external fun nativeHitTest(page: Int, x: Float, y: Float, extend: Boolean): String

    /** Caret + selection geometry in page coordinates (JSON). */
    external fun nativeCaretInfo(): String

    /** Desktop ribbon (tab/group/label/command id) joined with parameter info, as JSON. */
    external fun nativeRibbon(): String

    /** Every registered command (id, label, location, shortcut, params) as a JSON array. */
    external fun nativeCommandRegistry(): String

    /** Currently disabled commands with the engine's reason: {"id":"reason"}. */
    external fun nativeDisabledCommands(): String

    /** Body headings for the navigation pane (JSON with a jump position for each). */
    external fun nativeHeadings(): String
}
