# Keep data classes for serialization
-keep class com.macci.kaAlerto.data.** { *; }

# MapLibre
-keep class org.maplibre.** { *; }

# Compose
-keep class androidx.compose.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Nearby Connections
-keep class com.google.android.gms.nearby.** { *; }
