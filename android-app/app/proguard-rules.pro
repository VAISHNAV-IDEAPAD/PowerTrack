# Proguard rules for PowerTrack
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
