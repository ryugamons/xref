# --- XTERM PROJECT PROGUARD RULES ---

# 1. Keep JNI methods (essential for NDK)
-keepclasseswithmembernames class * {
    native <methods>;
}

# 2. Keep Security classes
-keep class id.xterm.core.security.** { *; }

# 3. Hilt / Dagger rules
-keep class dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel
-keep class * {
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <init>(...);
}

# 4. Kotlin Serialization
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keep class kotlinx.serialization.json.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# 5. Room
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Entity
-keep class * extends androidx.room.Dao

# 6. OkHttp / Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**

# 7. Timber
-dontwarn timber.log.**

# 8. Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-dontwarn kotlinx.coroutines.**
