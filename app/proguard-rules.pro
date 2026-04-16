# Keep Hilt (Dagger) classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.** { *; }
-keep class * extends dagger.hilt.android.internal.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

# Keep Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.Entity { *; }
-keep class * extends androidx.room.Dao { *; }
-keepclassmembers class * extends androidx.room.Entity { *; }
-keepclassmembers class * extends androidx.room.Dao { *; }

# Keep Compose (optional, but safe)
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }

# Keep ViewModel & LiveData
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory { *; }

# Keep widget, broadcast receiver, service, activity
-keep class * extends android.appwidget.AppWidgetProvider { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Application { *; }

# Keep our package classes
-keep class com.benhic.appdar.** { *; }
-keep class com.example.nearbyappswidget.** { *; }

# Keep resource IDs
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep parcelable CREATOR fields
-keep class * implements android.os.Parcelable {
  public static final ** CREATOR;
}

# Keep Kotlin metadata (for reflection)
-keep class kotlin.Metadata { *; }

# Keep annotations (optional)
-keep class * implements javax.annotation.processing.Processor { *; }

# Keep Google Play Services location (optional)
-keep class com.google.android.gms.location.** { *; }

# Keep logging statements for debugging
-keepattributes SourceFile,LineNumberTable
-keep class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}