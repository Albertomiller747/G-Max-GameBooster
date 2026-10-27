# Optimize and obfuscate application classes
-repackageclasses 'com.example.obf'
-allowaccessmodification

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Models and data classes
-keep class com.example.model.** { *; }
-keepclassmembers class com.example.model.** { *; }

# Android Architecture components and Services
-keep class * extends android.app.Service
-keep class * extends android.content.ContentProvider
-keep class * extends android.app.Activity

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

