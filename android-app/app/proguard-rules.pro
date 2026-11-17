# Rust JNI Köprüsü metodlarını ve native sembolleri koru
-keep class net.mailx.android.core.MailxRustBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Google Tink (androidx.security:security-crypto) derleme zamanı anotasyon uyarılarını yoksay
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Google Tink protobuf / keyset sınıflarının R8 tarafından bozulmasını önle
-keep class com.google.crypto.tink.** { *; }
