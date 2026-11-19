-keep class net.mailx.android.core.MailxRustBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class androidx.credentials.** { *; }

-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# UniFFI Kotlin bindings use JNA reflection to reach the Rust shared library.
-keep class com.sun.jna.** { *; }
-keep class net.java.dev.jna.** { *; }
-dontwarn com.sun.jna.**
