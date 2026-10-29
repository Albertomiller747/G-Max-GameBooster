# Optimize and obfuscate application classes
-repackageclasses 'com.example.obf'
-allowaccessmodification

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep only Firestore reflection data models
-keep @com.google.firebase.firestore.IgnoreExtraProperties class * { *; }
-keepclassmembers class com.example.data.repository.UserCloudProfile { *; }
-keepclassmembers class com.example.data.repository.LeaderboardEntry { *; }
-keepclassmembers class com.example.data.repository.UsernameClaim { *; }

# Keep Android manifest-registered components by name only (allow internal method obfuscation)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
