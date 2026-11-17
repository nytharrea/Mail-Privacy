# Rust JNI Köprüsü metodlarını ve native sembolleri koru
-keep class net.mailx.android.core.MailxRustBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Google Tink (androidx.security:security-crypto) opsiyonel/derleme zamanı uyarılarını yoksay
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
