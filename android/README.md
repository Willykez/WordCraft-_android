# WordCraft for Android

Kotlin / Jetpack Compose UI on top of the unchanged Rust WordCraft engine.

```
Kotlin / Jetpack Compose   toolbars, menus, gestures, page canvas        app/src/main/kotlin
Generated Kotlin bindings  MobileSession + record types (UniFFI)         generated at build time
Rust bridge                libwordcraft_android.so                       rust/src/lib.rs
WordCraft engine           document model, commands, layout, DOCX/PDF    ../crates/*  (not modified)
```

Nothing under `crates/`, `apps/` or the root `Cargo.toml` was changed. `rust/` is a standalone Cargo
workspace so the root's `unsafe_code = "forbid"`, `cargo xtask ci` and the wasm build are unaffected.

## How it works

* **Everything is a command.** Kotlin calls `session.execute("format.bold", "{}")`; the same 650+
  command ids the desktop ribbon, CLI and MCP server use. `EditorViewModel.run()` is the only entry.
* **Pages come from the engine.** `render_page(i, scale)` rasterises WordCraft's own pagination.
  Compose draws the bitmap, then selection, caret and handles on top in page points.
* **Hit testing is the engine's.** Taps go to `caret.set {page,x,y}` in page points; handles use
  `select_between`.
* **Fonts.** The engine has no Android font path, so `register_fonts(["/system/fonts", ...])` runs at
  startup (`WordcraftApp`). Fonts the app should ship go in `filesDir/fonts` (not in this repo; see AGENTS.md).

## Build

```sh
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk
export ANDROID_NDK_HOME=/path/to/ndk          # r26+
cd android && gradle :app:assembleDebug          # or open `android/` in Android Studio (no wrapper is checked in: `gradle wrapper`)
```
`:app:preBuild` runs `cargo ndk` (builds the `.so`) and `uniffi-bindgen` (writes the Kotlin bindings into
`app/build/generated/uniffi/kotlin`). Run the bridge tests on the host: `cd rust && cargo test`.

## Status (v0.1): written against the engine's real API, not yet compiled or run

Implemented: home screen (new, templates, recents, open), A4/Letter page view with continuous scroll and
pinch zoom, tap / double-tap / long-press-drag selection with handles, soft + hardware keyboard input,
Home / Insert / Layout / Review / References / View tabs, contextual bars for selection, tables and pictures,
find & replace, undo/redo, copy/cut/paste, DOCX/ODT/PDF/TXT/HTML save and export, autosave on stop.

Known gaps: no IME composition (predictive text, CJK) — see `TypingSink`; no print/share sheet; page bitmaps
are capped at ~3.2 px/pt so deep zoom is slightly soft; no picture drag-move/handles (size via commands);
command params for Review/References buttons use engine defaults and may need dialogs.

## CI

`.github/workflows/android.yml` runs the bridge tests and builds the debug APK on every push or pull
request that touches `android/` or `crates/`, and uploads it as the `wordcraft-android-debug` artifact.
It is a new, separate workflow; the existing ones are untouched. (Upstream AGENTS.md asks that CI changes
go through craft-common-actions; that matters for PRs to storytold/wordcraft, not for your own repo or fork.)
