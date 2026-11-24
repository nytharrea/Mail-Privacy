-keep class androidx.credentials.** { *; }

-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-keep class com.sun.jna.** { *; }
-keep class net.java.dev.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }
-keep class net.mailx.android.core.uniffi.** { *; }
-dontwarn com.sun.jna.**

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
