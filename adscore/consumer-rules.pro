# ========== AdMob Mediation Library ProGuard Rules ==========
# These rules are automatically applied to apps using this library

# ========== AdMob SDK ==========
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# User Messaging Platform (UMP) SDK
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# ========== Firebase Remote Config ==========
-keep class com.google.firebase.remoteconfig.** { *; }
-dontwarn com.google.firebase.remoteconfig.**

-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ========== Meta Audience Network ==========
-keep class com.facebook.ads.** { *; }
-dontwarn com.facebook.ads.**

# Shimmer (for loading effects)
-keep class com.facebook.shimmer.** { *; }
-dontwarn com.facebook.shimmer.**

# ========== AppLovin SDK ==========
-keep class com.applovin.** { *; }
-dontwarn com.applovin.**

-keep class com.google.ads.mediation.applovin.** { *; }
-dontwarn com.google.ads.mediation.applovin.**

# ========== Library Public API ==========
# Keep all public API classes and methods
-keep public class io.ads.mediation.AdsManager { *; }
-keep public class io.ads.mediation.RemoteConfigManager { *; }
-keep public class io.ads.mediation.AdmobAppIdOverride { *; }
-keep public class io.ads.mediation.NativeAdBinder { *; }
-keep public class io.ads.mediation.AdUnits { *; }

# ========== Native Ad View IDs ==========
# Don't obfuscate R.id.ad_* view IDs used in native ad layouts
-keepclassmembers class **.R$id {
    public static <fields>;
}

# Keep native ad view classes
-keep class * extends com.google.android.gms.ads.nativead.NativeAdView { *; }

# ========== AndroidX Lifecycle ==========
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# ========== General Android ==========
# Keep annotations
-keepattributes *Annotation*

# Keep line numbers for debugging crashes
-keepattributes SourceFile,LineNumberTable

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# ========== Kotlin ==========
# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }

# Keep Kotlin companion objects
-keepclassmembers class **$Companion {
    <fields>;
    <methods>;
}

# ========== Serialization ==========
# If using Kotlin serialization or Gson
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
