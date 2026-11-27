# R8 runs to optimize code (inlining, dead code removal) for smoother Compose.
# Names stay as written so crash traces and user logs remain readable.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*

# yt-dlp, Python runtime and ffmpeg (youtubedl-android): loaded natively and parsed by reflection.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**

# gallery-dl runs as a Python process; nothing on the JVM side is looked up by name.

# NewPipe extractor and its JavaScript engine (Rhino builds classes and calls methods by reflection).
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class org.jsoup.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.jsoup.**

# Web pages call these bridges by name (PO tokens, sign-in pages).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
