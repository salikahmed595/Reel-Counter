# PAICONS Self-Development: website download

| File | What it is |
|---|---|
| `PAICONS-Self-Development-v1.1.0.apk` | Signed release APK for paicons.com (Android 10+) |
| `PAICONS-Self-Development-v1.1.0.apk.sha256` | Checksum, which is also shown on the download section |
| `paicons-self-development-icon-512.png` | App icon used by the download section |
| `paicons-download-section.html` | Download section you can paste into paicons.com |

## Publish it on paicons.com

1. Upload the APK and the icon PNG next to the page.
2. Paste `paicons-download-section.html` into the page.
3. Serve the APK with the MIME type `application/vnd.android.package-archive`. Most hosts do this automatically.

## Install it on your own phone

You can copy the APK to the phone and open it.

Or, with USB debugging turned on, run this from this folder:

```bash
adb install PAICONS-Self-Development-v1.1.0.apk
```

Then open the app, tap **Agree**, and turn on **PAICONS Self-Development tracking** in Accessibility settings.

If Android 13 or later says **"Restricted setting"**, go to App info → ⋮ → **Allow restricted settings**, then turn the tracking on again. The app shows this step itself when it's needed.

## ⚠ Signing key: back it up now

The APK is signed with `C:\Users\salik\.paicons\paicons-release.jks`. Its password is in `C:\Users\salik\.paicons\keystore.properties`.

- **Every future version must be signed with this same key.** If the key is lost, people who installed from your site can't update. They would have to uninstall, which deletes their data.
- Back up both files to a password manager or other offline storage. Don't keep the only copy on this PC, and don't put them in OneDrive or git.
- Use this same key as your Google Play **upload key**. Google re-signs Play installs with its own key, so a website install and a Play install of the same app are signed differently. Someone switching from the website version to the Play version has to uninstall first.

## Release a new version

1. In `reel-counter-android/gradle.properties`, raise `reelcounter.versionCode` (for example to 2) and `reelcounter.versionName` (for example to 1.0.1).
2. Run `./gradlew assembleWebsiteRelease` from `reel-counter-android/`.
3. Copy `%LOCALAPPDATA%\ReelCounterBuild\app\outputs\apk\website\release\app-website-release.apk` here as `PAICONS-Self-Development-vX.Y.Z.apk`.
4. Run `sha256sum` on the new APK, then update the version, size, date and checksum in the download section.

## About this build

- **No Pro purchases.** Google Play Billing can't sell to apps installed from a website, so subscriptions appear only in the Google Play version. Every core feature is free here.
- **Same privacy behaviour as the Play version.** Everything is processed on the device, there are no ads and no analytics.
