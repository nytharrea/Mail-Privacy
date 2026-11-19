# Android SDK API 37 (Android 17 preview)

The Android 17 preview SDK repository exposes the platform package as
`platforms;android-37.0`, and installs it under
`$ANDROID_HOME/platforms/android-37.0`. Gradle's `compileSdk = 37` lookup expects
`$ANDROID_HOME/platforms/android-37`.

Both CI and Release workflows therefore:
1. install `platforms;android-37.0` from SDK Manager channel 3 (Canary);
2. install Android Build Tools 37.0.0 and NDK 30.0.16248370;
3. verify `android-37.0/android.jar`;
4. create `platforms/android-37 -> android-37.0` if the expected AGP path does
   not already exist; and
5. verify `android-37/android.jar` before invoking Gradle.

The app remains on `compileSdk = 37` and `targetSdk = 36`. This avoids opting
into Android 17 target-SDK behavior changes during this SDK installation fix.
