-keep class com.merowall.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.firebase.* *;
}
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}
-keep,includedescriptorclasses class com.merowall.**$$serializer { *; }
-keepclassmembers class com.merowall.** {
    *** Companion;
}
-keepclasseswithmembers class com.merowall.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class androidx.room.** { *; }
-keep @androidx.room.Database class * extends androidx.room.RoomDatabase
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
