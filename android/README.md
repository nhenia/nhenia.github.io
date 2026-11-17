# Haint — Android app (formerly Overlie)

Camera overlay app: put a photo over the live camera, move / resize / rotate / fade it, torch, save.

- **Download (always the newest):** https://github.com/nhenia/nhenia.github.io/releases/latest/download/Haint.apk
- **Web version:** https://nhenia.github.io/haint/ (the old /overlie/ address forwards here)

## How it works
- The app itself is `static/haint/index.html`. The Android app bundles that same file and runs it offline in a WebView.
- `MainActivity.java` only does the Android-specific parts: camera permission, the photo picker, saving to `Pictures/Haint`, and sharing.
- Every push that touches `static/haint/` or `android/` rebuilds the APK automatically (`.github/workflows/android.yml`) and publishes it as the latest release.
- The Android package id stays `app.overlie` so new builds install over the existing app.

## To change the app
Edit `static/haint/index.html` and push. Both the website and the APK update.

## Signing key
`overlie.keystore` is committed on purpose so every build can install over the previous one.
It's a personal sideload key for an app with no accounts or data. Never install a Haint APK from anywhere except the link above.
