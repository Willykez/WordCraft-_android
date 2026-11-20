import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// -PdevBuild=true builds arm64-v8a only (about half the Rust compile time); CI passes it.
val devBuild = providers.gradleProperty("devBuild").map { it.toBoolean() }.orElse(false).get()
val abis = if (devBuild) listOf("arm64-v8a") else listOf("arm64-v8a", "x86_64")

// ---- Release signing ----------------------------------------------------------------------
// Real secrets never live in this file or in git. Locally, copy keystore.properties.example to
// keystore.properties (already in .gitignore) and fill it in; in CI, set the four WORDCRAFT_*
// env vars instead (e.g. from repo secrets) and write the keystore file out before the build.
// Neither present -> releaseSigning stays null and the release build falls back to the debug
// key, so `assembleRelease` still works locally without any of this set up.
data class ReleaseSigning(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

val keystorePropsFile = rootProject.layout.projectDirectory.file("keystore.properties").asFile
val releaseSigning: ReleaseSigning? = if (keystorePropsFile.exists()) {
    val props = Properties().apply { keystorePropsFile.inputStream().use { load(it) } }
    fun need(k: String): String = props.getProperty(k) ?: throw GradleException("keystore.properties is missing \"$k\"")
    val storeFilePath: String = need("storeFile")
    ReleaseSigning(rootProject.layout.projectDirectory.file(storeFilePath).asFile, need("storePassword"), need("keyAlias"), need("keyPassword"))
} else {
    val storeFilePath = System.getenv("WORDCRAFT_KEYSTORE_FILE")
    val storePassword = System.getenv("WORDCRAFT_KEYSTORE_PASSWORD")
    val keyAlias = System.getenv("WORDCRAFT_KEY_ALIAS")
    val keyPassword = System.getenv("WORDCRAFT_KEY_PASSWORD")
    if (storeFilePath != null && storePassword != null && keyAlias != null && keyPassword != null) {
        ReleaseSigning(File(storeFilePath), storePassword, keyAlias, keyPassword)
    } else {
        null
    }
}
if (releaseSigning == null) {
    logger.lifecycle("No release signing configured (no keystore.properties and no WORDCRAFT_* env vars) — release builds will be signed with the debug key.")
}

android {
    namespace = "com.willkez.wordcraft"
    compileSdk = 35
    ndkVersion = "27.2.12479018" // keep in sync with NDK_VERSION in .github/workflows/android.yml
    defaultConfig {
        applicationId = "com.willkez.wordcraft"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += abis }
    }
    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = releaseSigning.storeFile
                storePassword = releaseSigning.storePassword
                keyAlias = releaseSigning.keyAlias
                keyPassword = releaseSigning.keyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseSigning != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    sourceSets {
        getByName("main") {
            kotlin.srcDir(layout.buildDirectory.dir("generated/uniffi/kotlin"))
        }
    }
}

// ---- Rust engine -------------------------------------------------------------------------------
val rustDir = rootProject.layout.projectDirectory.dir("rust").asFile
val jniLibs = layout.projectDirectory.dir("src/main/jniLibs").asFile
val bindingsDir = layout.buildDirectory.dir("generated/uniffi/kotlin").get().asFile

val cargoNdk = tasks.register<Exec>("cargoNdk") {
    group = "rust"
    workingDir = rustDir
    commandLine(
        buildList {
            add("cargo"); add("ndk")
            abis.forEach { add("-t"); add(it) }
            addAll(listOf("-o", jniLibs.path, "build", "--release", "--lib"))
        },
    )
    // cc-rs / cargo-ndk warn when the two variables disagree (the runner image sets ANDROID_NDK_ROOT).
    System.getenv("ANDROID_NDK_HOME")?.let { environment("ANDROID_NDK_ROOT", it) }
    outputs.dir(jniLibs)
    outputs.upToDateWhen { false } // cargo does its own incremental builds
}

val uniffiBindgen = tasks.register<Exec>("uniffiBindgen") {
    group = "rust"
    dependsOn(cargoNdk)
    workingDir = rustDir
    commandLine(
        "cargo", "run", "--release", "--bin", "uniffi-bindgen", "--",
        "generate", "--library", File(jniLibs, "arm64-v8a/libwordcraft_android.so").path,
        "--config", File(rustDir, "uniffi.toml").path, // pins package_name = com.willkez.wordcraft.bridge
        "--language", "kotlin", "--out-dir", bindingsDir.path,
    )
    outputs.dir(bindingsDir)
    outputs.upToDateWhen { false }
    doFirst { // never compile stale bindings
        bindingsDir.deleteRecursively()
        bindingsDir.mkdirs()
    }
    doLast {
        // uniffi-bindgen exits 0 without writing anything when the .so has no metadata (e.g. stripped).
        if (bindingsDir.walkTopDown().none { it.isFile && it.extension == "kt" }) {
            throw GradleException("uniffi-bindgen wrote no Kotlin to $bindingsDir; is libwordcraft_android.so stripped?")
        }
    }
}

// The bindings must exist before Kotlin/Java compile, and the .so before JNI libs are merged.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { dependsOn(uniffiBindgen) }
tasks.withType<JavaCompile>().configureEach { dependsOn(uniffiBindgen) }
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }.configureEach { dependsOn(cargoNdk) }

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
    implementation("net.java.dev.jna:jna:5.15.0@aar")
}
