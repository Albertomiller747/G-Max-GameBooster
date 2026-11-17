# Obfuscate and shrink application classes while generating R8 mapping.txt for Play Console
# Skip heavy bytecode optimization passes so release bundling completes within gateway timeouts
-dontoptimize

# Preserve line numbers and source filenames for readable deobfuscated stack traces
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Unity Ads SDK ships consumer rules for com.unity3d.ads.** and com.unity3d.services.**; keep Advertising ID client
-dontwarn com.unity3d.**
-keep class com.google.android.gms.ads.identifier.** { *; }
-dontwarn com.google.android.gms.ads.identifier.**

# Keep Jetpack Credential Manager Play Services provider and Google Identity Services classes
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**

# Keep Firebase Auth & Firestore reflection data models (explicitly scoped so repositories/ViewModels/UI are obfuscated)
-keep @com.google.firebase.firestore.IgnoreExtraProperties class * { *; }
-keep class com.example.data.repository.UserCloudProfile {
    public <init>(...);
    public <fields>;
    public <methods>;
}
-keep class com.example.data.repository.LeaderboardEntry {
    public <init>(...);
    public <fields>;
    public <methods>;
}
-keep class com.example.data.repository.UsernameClaim {
    public <init>(...);
    public <fields>;
    public <methods>;
}
-keep class com.example.data.repository.ReferralCodeEntry {
    public <init>(...);
    public <fields>;
    public <methods>;
}
-keep class com.example.data.repository.ReferralActivation {
    public <init>(...);
    public <fields>;
    public <methods>;
}
-keep class com.example.data.repository.DeviceReferralClaim {
    public <init>(...);
    public <fields>;
    public <methods>;
}
-keep class com.example.data.repository.TopGearProduct {
    public <init>(...);
    public <fields>;
    public <methods>;
}

# Keep Android manifest-registered components by name only
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
