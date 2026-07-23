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

# Chaquopy Python Integration Rules
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# ExoPlayer / Media3 Rules
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Preserve Line Numbers for Debugging Stack Traces
-keepattributes SourceFile,LineNumberTable