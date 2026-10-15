# PAICONS Self-Development: distribution

## Why Google Play is the way to share this app

Google Play Protect's **enhanced fraud protection** blocks any app that requests the **Accessibility service** when the app is installed from a browser, a chat app such as WhatsApp, or a file manager. The user sees:

> *"App blocked to protect your device — This app can request access to sensitive data…"*

This app needs Accessibility to count swipes, so **a downloaded APK will be blocked on many phones**. This is a Google security policy, not a bug. It must not be bypassed: no renamed packages, split installs, or telling users to turn off Play Protect. **Apps installed from Google Play are not blocked**, because Google reviews them first.

| File | Use |
|---|---|
| `google-play/PAICONS-Self-Development-v1.1.0-play.aab` | **Upload this to Play Console.** It's kept out of git (`*.aab`). |
| `PAICONS-Self-Development-v1.1.0.apk` | Website build, for **testers and developers via USB (`adb install`) only** |
| `PAICONS-Self-Development-v1.1.0.apk.sha256` | Checksum of the APK |
| `paicons-download-section.html` | paicons.com section, with a "Get it on Google Play" button |
| `paicons-self-development-icon-512.png` | Icon used by the section, and the Play Store hi-res icon |

## Fastest way to get it onto phones: Play internal testing (about one day)

1. Create a **Google Play Console** developer account at <https://play.google.com/console> (one-time US$25 fee, plus identity verification).
2. **Create app** → name it *PAICONS Self-Development*, choose **App** and **Free**.
3. Go to **Testing → Internal testing → Create new release**. Keep **Play App Signing** on and upload `google-play/PAICONS-Self-Development-v1.1.0-play.aab`.
4. Under **Testers**, add up to 100 Gmail addresses, or create an email list. Save, then roll out the release.
5. Copy the **opt-in link** and send it to your testers. Each tester opens it on their phone → **Become a tester** → **Install from Google Play**. No Play Protect block.
6. Play Console will ask for the app content forms (privacy policy, Data safety, **Accessibility API declaration and video**, content rating and target audience). The answers are written out in [`../reel-counter-android/docs/PLAY_RELEASE.md`](../reel-counter-android/docs/PLAY_RELEASE.md).

Then **closed testing**, with at least 12 testers for 14 days on a new personal account, followed by **production**. When the public listing is live, put its link in `paicons-download-section.html` and the README.

## Install on your own phone right now (USB, no Play Store)

`adb` installs aren't internet sideloading, so Play Protect doesn't block them.

1. On the phone, go to Settings → About phone and tap **Build number** 7 times. Then go to **Developer options** and turn on **USB debugging**.
2. Connect the phone to this PC by USB and accept **Allow USB debugging**.
3. From this folder, run:

```bash
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install PAICONS-Self-Development-v1.1.0.apk
```

If an older copy installed from WhatsApp is on the phone, uninstall it first.

## Coming in 2027: Android developer verification

Google is phasing in **developer verification** for all installs on certified Android phones. It already applies in Brazil, Indonesia, Singapore and Thailand, and is planned worldwide in 2027. A verified Play Console account covers this. Keep the same package name (`com.paicons.selfdevelopment`) and signing key.

## ⚠ Signing key: back it up

`C:\Users\salik\.paicons\paicons-release.jks` together with `keystore.properties` is the **upload key** for Play and the signing key for testers' APKs.

- Back up both files offline, for example in a password manager.
- Never commit them to git.
- If you lose the upload key, Play Console can reset it, but only if Play App Signing is on.

## Release a new version

1. In `reel-counter-android/gradle.properties`, raise `reelcounter.versionCode` and `reelcounter.versionName`.
2. From `reel-counter-android/`, run:

```bash
./gradlew bundlePlayRelease assembleWebsiteRelease
```

3. Upload the AAB from `%LOCALAPPDATA%\ReelCounterBuild\app\outputs\bundle\playRelease\` to Play Console.
4. Copy the tester APK from `...\apk\website\release\` into this folder and update its checksum.
