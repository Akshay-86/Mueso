# Add project specific ProGuard rules here.

# Room Database Keep Rules
-keep class * extends androidx.room.RoomDatabase
-keep class com.akshay.musicplayer.data.db.** { *; }
-keep class **.*_Impl {
    public <init>();
    public <init>(...);
    *;
}
-dontwarn androidx.room.paging.**

# ─── Moshi & Backup Data Classes ───
-keepparameternames
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keep class com.akshay.musicplayer.data.backup.** { *; }
-keepclassmembers class com.akshay.musicplayer.data.backup.** {
    <fields>;
    <init>(...);
}

# Moshi core & generated adapters keep rules
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter { *; }

# Keep Kotlin metadata & annotations for Moshi's KotlinJsonAdapterFactory reflection
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,LocalVariableTable,LocalVariableTypeTable,MethodParameters
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# ─── Google Sign-In & Auth ───
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# ─── OkHttp ───
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ─── UpdateManager data class ───
-keep class com.akshay.musicplayer.ui.viewmodel.managers.UpdateInfo { *; }

# Chaquopy Python Integration Rules
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# FFmpegKit Rules
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.smartexception.java.Exceptions

# ExoPlayer / Media3 Rules
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Preserve Line Numbers for Debugging Stack Traces
-keepattributes SourceFile,LineNumberTable