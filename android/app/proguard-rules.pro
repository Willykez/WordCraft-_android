# WordCraft release rules. AGP's own proguard-android-optimize.txt (applied alongside this file)
# already covers Activity/Application/View etc.; everything below is for the pieces R8 can't see
# through on its own: the UniFFI<->JNA bridge to the Rust engine, and Compose/Kotlin reflection.

# ---- Crash reports still worth reading -----------------------------------------------------
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ---- JNA (loads native methods and struct fields by reflection) ----------------------------
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure { public *; }
-keepclassmembers class * implements com.sun.jna.Library { public *; }
-keepclassmembers class * implements com.sun.jna.Callback { public *; }
-dontwarn com.sun.jna.**
-dontwarn java.awt.**

# ---- UniFFI-generated Kotlin bindings to the Rust engine ------------------------------------
# Generated into com.willkez.wordcraft.bridge at build time (see build.gradle.kts's
# uniffiBindgen task): FFI structs, callback interfaces and the native lib loader all get called
# into from native code or via reflection, so names and members must survive unchanged.
-keep class com.willkez.wordcraft.bridge.** { *; }
-keepclassmembers class com.willkez.wordcraft.bridge.** { *; }
-dontwarn com.willkez.wordcraft.bridge.**

# ---- Kotlin / coroutines / Compose ----------------------------------------------------------
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*, Signature, Exceptions, InnerClasses, EnclosingMethod
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**
# Compose's own consumer-rules.pro (bundled in its AARs) covers the compiler-generated
# $stable/$default fields etc.; nothing extra is needed here for it.

# ---- org.json (platform-provided; only listed so R8 doesn't warn about the API stub) --------
-dontwarn org.json.**
