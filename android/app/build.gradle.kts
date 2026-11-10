plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// -PdevBuild=true builds arm64-v8a only (about half the Rust compile time); CI passes it.
val devBuild = providers.gradleProperty("devBuild").map { it.toBoolean() }.orElse(false).get()
val abis = if (devBuild) listOf("arm64-v8a") else listOf("arm64-v8a", "x86_64")

android {
    namespace = "org.wordcraft.android"
    compileSdk = 35
    ndkVersion = "27.2.12479018" // keep in sync with NDK_VERSION in .github/workflows/android.yml
    defaultConfig {
        applicationId = "org.wordcraft.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += abis }
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
        "--config", File(rustDir, "uniffi.toml").path, // pins package_name = org.wordcraft.bridge
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
