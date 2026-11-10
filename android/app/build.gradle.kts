plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.wordcraft.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.wordcraft.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

// ---- Rust engine -------------------------------------------------------------------------------
// 1. cargo-ndk builds libwordcraft_android.so for each ABI into src/main/jniLibs.
// 2. uniffi-bindgen reads the UniFFI metadata from that library and writes the Kotlin bindings.
// Needs: rustup targets (aarch64-linux-android, x86_64-linux-android), `cargo install cargo-ndk`,
// and ANDROID_NDK_HOME pointing at an installed NDK.
val rustDir = rootProject.layout.projectDirectory.dir("rust").asFile
val jniLibs = layout.projectDirectory.dir("src/main/jniLibs").asFile
val bindingsDir = layout.buildDirectory.dir("generated/uniffi/kotlin").get().asFile

val cargoNdk = tasks.register<Exec>("cargoNdk") {
    workingDir = rustDir
    commandLine("cargo", "ndk", "-t", "arm64-v8a", "-t", "x86_64", "-o", jniLibs.path, "build", "--release", "--lib")
}

val uniffiBindgen = tasks.register<Exec>("uniffiBindgen") {
    dependsOn(cargoNdk)
    workingDir = rustDir
    commandLine(
        "cargo", "run", "--release", "--bin", "uniffi-bindgen", "--",
        "generate", "--library", File(jniLibs, "arm64-v8a/libwordcraft_android.so").path,
        "--language", "kotlin", "--out-dir", bindingsDir.path,
    )
}

android.sourceSets.getByName("main").java.srcDir(bindingsDir)
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(uniffiBindgen) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    // UniFFI's generated Kotlin calls the Rust library through JNA.
    implementation("net.java.dev.jna:jna:5.15.0@aar")
}
