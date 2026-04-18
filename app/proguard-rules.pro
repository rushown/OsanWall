-keep class com.osanwall.data.model.** { *; }
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
-keep,includedescriptorclasses class com.osanwall.**$$serializer { *; }
-keepclassmembers class com.osanwall.** {
    *** Companion;
}
-keepclasseswithmembers class com.osanwall.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class androidx.room.** { *; }
-keep @androidx.room.Database class * extends androidx.room.RoomDatabase
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
