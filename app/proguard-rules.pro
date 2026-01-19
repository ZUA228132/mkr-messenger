# Pioneer ProGuard Rules

# Keep crypto classes
-keep class com.pioneer.messenger.data.crypto.** { *; }

# Keep models for serialization
-keep class com.pioneer.messenger.domain.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Signal Protocol
-keep class org.signal.** { *; }
-keep class org.whispersystems.** { *; }

# WebRTC
-keep class org.webrtc.** { *; }

# OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
