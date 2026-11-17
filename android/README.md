# Overlie — Android app

Camera overlay app: put a photo over the live camera, move / resize / rotate / fade it, torch, save.

- **Download (always the newest):** https://github.com/nhenia/nhenia.github.io/releases/latest/download/Overlie.apk
- **Web version:** https://nhenia.github.io/overlie/

## How it works
- The app itself is `static/overlie/index.html`. The Android app bundles that same file and runs it offline in a WebView.
- `MainActivity.java` only does the Android-specific parts: camera permission, the photo picker, saving to `Pictures/Overlie`, and sharing.
- Every push that touches `static/overlie/` or `android/` rebuilds the APK automatically (`.github/workflows/android.yml`) and publishes it as the latest release.

## To change the app
Edit `static/overlie/index.html` and push. Both the website and the APK update.

## Signing key
`overlie.keystore` is committed on purpose so every build can install over the previous one.
It's a personal sideload key for an app with no accounts or data. Never install an Overlie APK from anywhere except the link above.
