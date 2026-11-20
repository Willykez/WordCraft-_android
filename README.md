<div align="center">

# WordCraft

### A native Android word processor powered by the WordCraft document engine

**Write. Format. Organize. Create.**

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](#)
[![Language](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](#)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](#)
[![Engine](https://img.shields.io/badge/Engine-Rust-000000?logo=rust&logoColor=white)](#)
[![Status](https://img.shields.io/badge/status-in%20development-orange)](#-project-status)

*A mobile-first editing experience built with Kotlin, Jetpack Compose, Rust, and UniFFI.*

</div>

---

## About

WordCraft Android is the native Android application for the WordCraft document editor. It combines a Kotlin and Jetpack Compose interface with the WordCraft Rust document engine through a UniFFI bridge.

The project aims to bring a capable, document-focused editing experience to Android, including a paginated editing surface, formatting controls, document navigation, and tools for working with document content.

> **Project status:** This project is under active development. Features and workflows may change, and a successful build does not necessarily mean every editing feature is complete or production-ready.

## Highlights

The current codebase includes the following application areas:

| Area | What is in the project |
|---|---|
| Editing interface | Native Android UI built with Jetpack Compose |
| Document engine | Rust engine connected to Android through UniFFI |
| Page display | Page canvas and rendered-page support |
| Formatting interface | Ribbon, curated tools, style gallery, and formatting panels |
| Navigation | Document headings, page navigation, and text search UI |
| Document actions | Undo, redo, selection, clipboard integration, and engine commands |
| Document content | Engine bridge methods for pictures and document operations |
| Local data | Android document storage components |
| App identity | WordCraft launcher icon and Android theme resources |

The Rust bridge exposes document operations and metadata to the Android layer. The exact availability of a command depends on the underlying engine state and the command registry.

## Technology stack

| Technology | Purpose |
|---|---|
| **Kotlin** | Android application code |
| **Jetpack Compose** | Declarative Android UI |
| **Material 3** | UI components and visual foundations |
| **Rust** | Document-engine integration |
| **UniFFI** | Kotlin bindings for the Rust library |
| **Cargo / cargo-ndk** | Rust dependency and Android native-library builds |
| **Android Gradle Plugin** | Android build configuration |
| **Gradle** | Build orchestration |

### Key project versions

These values are taken from the current Android project configuration.

| Component | Version / setting |
|---|---|
| Android Gradle Plugin | `8.7.3` |
| Kotlin | `2.0.21` |
| Java target | `17` |
| Compile SDK | `35` |
| Target SDK | `35` |
| Minimum SDK | `26` |
| Android NDK | `27.2.12479018` |
| Android app version | `0.1.0` (`versionCode 1`) |
| Rust edition | `2024` |
| UniFFI | `0.29` |

The Rust Android bridge declares Rust `1.95` as its minimum version.

## Repository layout

The Android application lives in the `android/` directory. The Rust bridge is a separate crate and links to the WordCraft engine and fonts crates in the repository.

```text
.
├── crates/
│   ├── engine/                 # WordCraft document engine
│   └── fonts/                  # Font assets and font support
│
├── android/
│   ├── app/
│   │   └── src/main/
│   │       ├── kotlin/com/willkez/wordcraft/
│   │       │   ├── data/       # Document storage
│   │       │   ├── editor/     # Editor state, canvas, ruler and handles
│   │       │   └── ui/         # Compose screens, ribbon and panels
│   │       ├── res/            # Android resources and launcher assets
│   │       └── AndroidManifest.xml
│   │
│   ├── rust/
│   │   ├── src/                # UniFFI bridge
│   │   ├── Cargo.toml
│   │   └── uniffi.toml
│   │
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradle.properties
│
└── README.md
```

**Important:** `android/rust/Cargo.toml` references `../../crates/engine` and `../../crates/fonts`. Build the Android app from the complete repository with those directories present. An Android-only source export is not a self-contained Rust build.

## Getting started

### 1. Requirements

Install and configure:

- Android Studio with Android SDK Platform 35.
- Android SDK Build-Tools compatible with your Android Gradle Plugin.
- Android NDK `27.2.12479018`.
- JDK 17.
- Rust toolchain `1.95` or newer.
- Android Rust targets for the ABIs you intend to build.
- `cargo-ndk`.

Install `cargo-ndk`:

```bash
cargo install cargo-ndk
```

Install the Rust Android targets:

```bash
rustup target add aarch64-linux-android
rustup target add x86_64-linux-android
```

Make sure the Android SDK and NDK are installed and that your environment exposes the NDK path. Android Studio's SDK Manager can be used to install the required SDK and NDK versions.

### 2. Clone the repository

```bash
git clone https://github.com/storytold/wordcraft.git
cd wordcraft
```

The Android project expects the repository's Rust engine and font crates to be available at the paths shown in the repository layout.

### 3. Build a debug APK

From the repository root:

```bash
cd android
./gradlew :app:assembleDebug
```

The generated APK is normally located at:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected Android device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. Faster development build

The Android Gradle configuration supports a development mode that builds only the `arm64-v8a` native ABI. This is useful for testing on most modern ARM-based Android phones:

```bash
./gradlew :app:assembleDebug --no-daemon -PdevBuild=true
```

The normal configuration builds `arm64-v8a` and `x86_64`.

> The development build is ABI-limited. Use the normal build when you need both configured ABIs.

## How the Android and Rust layers work

At a high level, the application is organized into three layers:

```text
┌──────────────────────────────────────────────┐
│             Android presentation             │
│       Kotlin · Jetpack Compose · Material 3  │
├──────────────────────────────────────────────┤
│             Application / editor             │
│  Screens · ViewModel · document interaction  │
├──────────────────────────────────────────────┤
│                Native bridge                 │
│                 UniFFI / JNI                 │
├──────────────────────────────────────────────┤
│              WordCraft engine                │
│              Rust document logic             │
└──────────────────────────────────────────────┘
```

1. **Presentation layer:** Compose screens render the editor, toolbars, panels, and navigation UI.
2. **Application layer:** Kotlin editor components coordinate UI state, user actions, document storage, and calls to the bridge.
3. **Bridge layer:** UniFFI generates Kotlin bindings for the Rust Android library.
4. **Engine layer:** The Rust engine handles document sessions, commands, state, and page-related operations.

The bridge is configured to generate Kotlin bindings in:

```text
com.willkez.wordcraft.bridge
```

Generated bindings are written into the Android build directory. They should not be edited manually.

## Rust bridge build

The Android Gradle build includes tasks that compile the Rust library and generate UniFFI Kotlin bindings.

The relevant tasks are wired into Kotlin compilation and native-library merging. In a normal app build, Gradle invokes the Rust build and binding-generation steps automatically.

The Rust bridge crate is located at:

```text
android/rust/
```

Its native library is named:

```text
libwordcraft_android.so
```

If the Rust build fails, check the Rust toolchain, Android targets, NDK configuration, `cargo-ndk` installation, and the presence of the referenced engine and font crates.

## Release builds and signing

Debug builds can be installed for development and testing. For a distributable release, configure a private signing key.

### Local signing

Create a local copy of the example file:

```bash
cd android
cp keystore.properties.example keystore.properties
```

Fill in the required values:

```properties
storeFile=release-key.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

Keep the keystore and `keystore.properties` private. Do not commit signing credentials or upload them to public issue trackers.

Then build:

```bash
./gradlew :app:assembleRelease
```

### CI signing

The Gradle configuration also accepts these environment variables:

- `WORDCRAFT_KEYSTORE_FILE`
- `WORDCRAFT_KEYSTORE_PASSWORD`
- `WORDCRAFT_KEY_ALIAS`
- `WORDCRAFT_KEY_PASSWORD`

Store sensitive values in your CI provider's encrypted secrets. Never put passwords or keystore files directly in workflow source code.

**Release-signing note:** If no release signing configuration is supplied, the current Gradle configuration falls back to the debug signing key. That is useful for local build verification, but it is not suitable for publishing an app update to users. Configure a secure, persistent release key before distribution.

## Development notes

- Keep Android UI code in Kotlin and Jetpack Compose.
- Keep document-engine behavior in the Rust engine wherever that is the established architecture.
- Treat generated UniFFI bindings as build outputs, not hand-maintained source files.
- Test changes on a real Android device as well as an emulator where possible.
- When changing native dependencies or Rust code, verify both configured Android ABIs.
- Avoid committing local signing files, build outputs, or machine-specific configuration.

### Useful commands

Run a debug build:

```bash
cd android
./gradlew :app:assembleDebug
```

Run the ARM64 development build:

```bash
./gradlew :app:assembleDebug --no-daemon -PdevBuild=true
```

Run a release build:

```bash
./gradlew :app:assembleRelease
```

List available Gradle tasks:

```bash
./gradlew tasks
```

## Troubleshooting

| Symptom | What to check |
|---|---|
| `cargo: command not found` | Install Rust and ensure Cargo is on `PATH`. |
| `cargo-ndk` is missing | Run `cargo install cargo-ndk`. |
| Android target is unavailable | Install the required Rust target with `rustup target add`. |
| NDK build cannot find the toolchain | Install NDK `27.2.12479018` and check Android SDK/NDK environment configuration. |
| Rust crate path cannot be found | Build from the complete repository and verify `crates/engine` and `crates/fonts` exist. |
| UniFFI generates no Kotlin files | Check that the native library was built correctly and retains the metadata required by `uniffi-bindgen`. |
| Kotlin compilation fails | Read the first `:app:compileDebugKotlin` error; later messages may be follow-on errors. |
| `libjnidispatch.so` cannot be stripped | This may be a packaging warning rather than the cause of a failed build. Check the actual failing Gradle task. |
| Release signing fails | Verify the keystore path, alias, and passwords; keep the release key secure. |

## Contributing

Contributions, bug reports, and thoughtful suggestions are welcome.

Before submitting a change:

1. Keep changes focused and explain the reason for them.
2. Preserve the existing editor architecture and user experience.
3. Build the Android app after changing Kotlin or Gradle code.
4. Verify Rust changes against the Android targets you support.
5. Include clear reproduction steps for bug reports.

For bug reports, include:

- Device model and Android version.
- Steps to reproduce the issue.
- Expected and actual behavior.
- Relevant logs or screenshots.
- Whether the issue occurs on a device, emulator, or both.

## Roadmap

The following are project direction areas, not a claim that every item is already complete:

- [ ] Stabilize Android debug and release builds.
- [ ] Improve the paginated editing experience on phone screens.
- [ ] Expand and refine formatting and document-structure controls.
- [ ] Improve document import, export, and file workflows.
- [ ] Strengthen editing, selection, and object-manipulation interactions.
- [ ] Improve accessibility, performance, and device compatibility.
- [ ] Add broader automated testing and release verification.

## Acknowledgements

WordCraft Android uses the WordCraft Rust engine and its supporting font crate from the project repository. It also relies on the Android, Kotlin, Jetpack Compose, Rust, and UniFFI ecosystems.

## License

The Android Rust bridge crate declares `MIT OR Apache-2.0`. Review the licenses of the complete repository, its engine, fonts, dependencies, and assets before redistributing the application. Do not assume that this README alone establishes the license for every component.

---

<div align="center">

**WordCraft — a better writing experience, built for Android.**

<sub>Built with Kotlin · Jetpack Compose · Rust · UniFFI</sub>

</div>
