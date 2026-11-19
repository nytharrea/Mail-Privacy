# Android SDK API 37 setup

The app compiles against API 37 (`compileSdk = 37`) while retaining
`targetSdk = 36`.

Both GitHub Actions workflows now:
- install `platforms;android-37` using sdkmanager channel 3, so preview-channel
  packages are discoverable if API 37 is not yet on the stable channel;
- install Build Tools 36.0.0 and NDK 30.0.16248370;
- verify that `platforms/android-37/android.jar` exists before Gradle runs;
- normalize a repository directory named `android-37.0` to the directory name
  expected by Android Gradle Plugin, if that is the directory actually
  installed by the SDK repository.

If Google has not published the `platforms;android-37` package in any enabled
channel, the workflow fails at SDK installation with an explicit error rather
than silently falling back to API 36.
