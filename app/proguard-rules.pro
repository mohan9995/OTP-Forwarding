# Proguard / R8 configuration for OTP Forwarding

# Keep Room entities and DAOs
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep DataStore preference keys and serializable models
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# Keep Compose preview and viewmodel reflection if needed
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
