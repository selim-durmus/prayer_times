# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# --- App rules ---
# Saved prayer days, logs, schedule cache and follow-up reminders are JSON read by Moshi, partly
# through reflection (KotlinJsonAdapterFactory) and enum field lookups. Renaming these classes,
# properties or enum constants would make data saved by earlier versions unreadable.
-keep class com.tuttoposto.prayertimes.data.models.** { *; }
-keep class com.tuttoposto.prayertimes.data.api.** { *; }
-keep class com.tuttoposto.prayertimes.data.repository.StoredPrayerDay { *; }
-keep class com.tuttoposto.prayertimes.notifications.ReminderFollowUp { *; }
-keep class com.tuttoposto.prayertimes.**JsonAdapter { *; }
-keep class kotlin.Metadata { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
