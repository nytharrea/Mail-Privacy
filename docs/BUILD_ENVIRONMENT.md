# Build environment

## Java runtime

The Android CI and Release workflows run Gradle on **Eclipse Temurin JDK 27**.
The project uses the committed Gradle Wrapper and Gradle 9.9.1. Gradle 9.8.1+
supports running on Java 27; Android Gradle Plugin 9.5.1 requires Gradle 9.6.0
or newer.

The Android Java source/bytecode compatibility remains Java 21 intentionally.
The JDK used to run Gradle and the bytecode level emitted for Android are separate
settings. Raising `sourceCompatibility`/`targetCompatibility` to 27 would require
verification across D8/R8, desugaring, Android runtime API availability, and all
Java/Kotlin dependencies; this project does not claim that validation.

## Wrapper

Use `android-app/gradlew` (or `gradlew.bat` on Windows) from the `android-app`
directory. The wrapper distribution is pinned to Gradle 9.9.1.

## Android SDK

- compileSdk / targetSdk: 36
- Build Tools: 36.0.0
- NDK: 30.0.16248370
- ABIs: arm64-v8a, armeabi-v7a, x86_64, x86
