# PAICONS Self-Development — Google Play release guide

This is the step-by-step path from this repo to a live Play listing. Items marked **☐ YOU** need your decision, your accounts or your physical devices.

> **Approval is not guaranteed.** Google reviews every app that uses the AccessibilityService API by hand. This app follows the published rules: in-app prominent disclosure, affirmative consent, declaration and video, no autonomous actions, and `isAccessibilityTool=false`. Reviewers can still reject it. If that happens, read the rejection email carefully, fix exactly what it names, and resubmit. Don't argue the policy.

---

## 0. Decisions to make first (they can't change after the first upload)

| | Decision | Recommendation |
|---|---|---|
| ☐ YOU | **Package name** (currently `com.reelcounter.app`) | Use a domain you own, for example `com.quixoticcreatives.reelcounter`. Set it with `reelcounter.applicationId` in `gradle.properties`. |
| ☐ YOU | **App name** | "Reels" is a Meta product name. A name like **"Scroll Counter: Mindful Reels"** or **"Reel Count – Screen Habits"** is lower risk than one that looks official. Never use any platform's logo in the icon or screenshots. |
| ☐ YOU | **Developer account type** | **Personal** accounts must run a closed test with **≥12 testers opted in for 14 continuous days** before production. **Organization** accounts (needs a D-U-N-S number) skip that requirement. |
| ☐ YOU | **Support email + privacy policy URL** | Required (§2). |

## 1. Signing key (do this once, keep it forever)

Run this in a terminal (Android Studio → Terminal) from `reel-counter-android/`:

```bash
mkdir ../keys
```

```bash
keytool -genkeypair -v -keystore ../keys/reel-counter-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

Then copy `keystore.properties.example` to `keystore.properties` and fill in the passwords.

- **Back up** `reel-counter-upload.jks` and the passwords somewhere safe outside OneDrive, such as a password manager. Never commit them to git.
- When you create the app in Play Console, keep **Play App Signing** on (the default). Google holds the real app signing key, and yours is only the *upload* key, so it can be reset if lost.

## 2. Configure and build

In `gradle.properties`:

```properties
reelcounter.versionCode=1          # +1 for every upload
reelcounter.versionName=1.0.0
reelcounter.billingBackendUrl=https://YOUR-CLOUD-RUN-URL
reelcounter.privacyPolicyUrl=https://YOUR-SITE/privacy-policy.html
reelcounter.supportEmail=support@yourdomain.com
```

1. **Host the privacy policy.** Fill the `[PLACEHOLDERS]` in `docs/privacy-policy.html`, then host it over HTTPS. GitHub Pages works: create a public repo, upload the file, then Settings → Pages.
2. **Deploy the billing backend.** Follow `../reel-counter-billing-backend/README.md`.
3. **Build the release bundle.** In Android Studio: **Build → Generate Signed App Bundle / APK → Android App Bundle**. Or from a terminal:

```bash
./gradlew bundlePlayRelease
```

The AAB is written to `%LOCALAPPDATA%\ReelCounterBuild\app\outputs\bundle\release\app-release.aab`. This path applies because the project is inside OneDrive.

## 3. Create the app in Play Console

Go to Play Console → **Create app**:
- Default language: English (United States). Add **Urdu (ur)** as a store listing translation.
- App or game: **App**. Free or paid: **Free**, since Pro is an in-app subscription.
- Accept the declarations.

### 3.1 App content (Policy → App content)

| Section | Answer |
|---|---|
| **Privacy policy** | Your hosted URL. |
| **Ads** | **No**, the app contains no ads. |
| **App access** | **All functionality is available without special access.** Add this reviewer note: *"No login. To test automatic counting: open the app → Get started → Agree → Open Accessibility settings → enable 'Reel Counter tracking'. Then open YouTube → Shorts and swipe to the next video. The count appears on Home. Detection is experimental and may vary by app version."* |
| **Content rating** | Category **Utility, productivity, communication, or other**. Answer **No** to violence, sexuality, language, controlled substances, gambling and user-to-user communication. It doesn't share location, and it doesn't let users buy digital goods other than the subscription. The expected result is **Everyone / PEGI 3**. |
| **Target audience** | **13–15, 16–17, 18+**. Do **not** select any age under 13. That keeps the app out of the Families program, which restricts the Accessibility API. |
| **News app** | No. |
| **Health apps** | Select **none** of the health categories. Don't make medical or mental-health claims in the listing. |
| **Financial features** | None. |
| **Government app** | No. |
| **Data safety** | See §4. |
| **Permissions → Accessibility API** | See §5. |

## 4. Data safety form

Under Play's definitions, data that is processed only on the device is **not "collected"**. Only purchase verification leaves the phone.

**Does your app collect or share any of the required user data types?** → **Yes**

| Data type | Collected | Shared | Ephemeral | Required? | Purposes |
|---|---|---|---|---|---|
| **Financial info → Purchase history** | Yes | No | No | Optional (only if the user buys Pro) | App functionality |

Answer every other data type **not collected**. That includes location, personal info, messages, photos, audio, contacts, app activity (counts stay on the device), web browsing and device IDs.

**Security practices:**
- Data is encrypted in transit: **Yes** (HTTPS only).
- Users can request deletion: **Yes**. In-app "Delete all local activity" covers device data. Uninstalling removes everything. For server subscription records, contact support.
- Committed to the Families Policy: **No** (not targeting children).
- Independent security review: **No**.

> ☐ YOU — **Observed during testing:** the Google Play Billing library sent its own diagnostic requests to `firebaselogging.googleapis.com`. **Check the Google Play Billing Library entry** in Google's SDK data-disclosure guidance before you submit. The library processes purchase data and writes a local diagnostics database (`com.google.android.datatransport`). If Google's guidance for this version lists additional data types (such as diagnostics), add them.

## 5. Accessibility API declaration

Go to Policy → App content → **Sensitive app permissions → Accessibility API**.

- **Is your app an accessibility tool?** → **No.** The manifest declares `isAccessibilityTool="false"`.
- **Why does your app use the AccessibilityService API?** → **App functionality**
- **Description**, which you can paste:

> Reel Counter is a digital-wellbeing app that helps users see how often they move to a new short video in Instagram Reels, YouTube Shorts, Facebook Reels and TikTok, and set daily limits. With the user's explicit in-app consent, the AccessibilityService receives scroll and window-change events **only from those four apps** (restricted by `packageNames` in the service config). It reads only the position, size and scroll movement of scrolling containers to detect a transition to a new video. It does not read, store or transmit text, captions, comments, messages, video IDs or screenshots. It never performs gestures, taps, scrolls, blocks apps or changes settings, and it doesn't act autonomously. Counts are stored only on the device and deleted after 30 days. Users can turn the service off at any time.

- **Do you collect or share personal or sensitive data through accessibility capabilities?** → **No.** Data is processed on the device to produce counts, and nothing derived from it leaves the device.

### Required video (unlisted YouTube link, 30–90 s)

Record the screen of a real phone or emulator. Show each of the following, in order:

1. Open Reel Counter from the launcher.
2. **Welcome → Get started → the full disclosure screen.** Scroll slowly so every line is readable. Pause on "What the service can access", "What we save", "What we never save or send" and "Who it's shared with".
3. **Consent flow.** Tap **Agree** → Open Accessibility settings → Reel Counter tracking → turn it on and accept Android's dialog → return to the app. The status shows "Tracking active".
4. **Non-consent flow.** Use Reset app, or a fresh install. Tap **Not now**. The dashboard opens in limited mode with "Automatic tracking is off". Tap **Enable automatic tracking** and show that it returns to the same disclosure screen.
5. **The core feature.** Open YouTube → Shorts and swipe to 2–3 new videos. Return to Reel Counter. The YouTube Shorts count has gone up. Show **Tracking health → Last detected transition**.

Add captions: "Counting happens on-device. No content is read or stored."

## 6. Subscriptions (Monetize with Play → Products → Subscriptions)

You need a merchant payments profile first: Payments profile → set up for Pakistan, with bank details.

| Product ID | Base plan | Billing period | Price | Renewal |
|---|---|---|---|---|
| `pro_monthly` | `monthly` | 1 month | ☐ YOU set the PKR price | Auto-renewing |
| `pro_yearly` | `yearly` | 1 year | ☐ YOU set the PKR price | Auto-renewing |

The IDs must match exactly. The app shows the base plan only, with no trial offers.

- **Grace period:** 7 days. **Account hold:** on. Both are handled by the backend.
- **Real-time developer notifications:** add the Pub/Sub topic from the backend README and send a test notification.
- **License testing:** Settings → License testing → add your testers' Gmail addresses. Their purchases are free test purchases.
- **Subscription policy:** Pro must keep providing ongoing value (new report cards and themes over time, PRD §11). If Pro ever becomes static themes only, switch it to a one-time product.

## 7. Testing tracks → production

1. **Internal testing.** Upload the AAB. Add yourself and test on real devices: consent, tracking in each app, reminders, widgets, Urdu, and a purchase plus a cancellation with a license tester.
2. **Closed testing.** Use the same AAB or a higher versionCode. Recruit **at least 12 testers**, ideally 20+, across Samsung, Xiaomi, Oppo, Vivo and Pixel. They must **stay opted in for 14 consecutive days**. Ask them to use the app daily and report miscounts.
3. **Apply for production.** Play Console asks about the test: who your testers were, what feedback you got, and what you changed. Answer specifically.
4. **Production.** Countries: start with **Pakistan**. Use a staged rollout (20% → 50% → 100%) and watch Android vitals for crashes and ANRs.

## 8. Store listing

**App icon:** 512×512 PNG, made from `res/drawable/ic_launcher_*`. **Feature graphic:** 1024×500.

**Screenshots:** 4–8 phone screenshots with an aspect ratio no more extreme than 2:1. Use 1080×1920. Don't use real platform logos or real creators' videos.

**Short description, English (≤80 chars):**
> Count short-video scrolling privately on your phone. Set gentle daily limits.

**Full description, English:**
> Reel Counter helps you notice how often you move to a new short video in Instagram Reels, YouTube Shorts, Facebook Reels and TikTok, and set daily goals that feel good.
>
> • Detected transitions counted automatically, on your phone
> • Separate per-app counts, a 7- and 30-day trend and an hourly pattern
> • Gentle 50%, 75% and 100% reminders with quiet hours, and nothing gets blocked
> • Beautiful home-screen widgets in four sizes
> • English and اردو
>
> PRIVATE BY DESIGN
> No account. No ads. No analytics. Counting runs offline, and activity is deleted automatically after 30 days. We never read or store captions, comments, messages or screenshots.
>
> HOW IT WORKS
> With your permission, Reel Counter uses Android's Accessibility service to notice when a supported feed moves to a new video. A detected transition is not proof that a video was watched. Detection accuracy varies by app and app version, and is shown honestly in Tracking health.
>
> PRO (OPTIONAL)
> Extra widget themes, 30-day hourly patterns and week-over-week comparisons. Auto-renewing monthly or yearly subscription; cancel anytime in Google Play. Core features are always free.
>
> Reel Counter is independent and is not affiliated with Meta, Instagram, Facebook, Google, YouTube or TikTok.

**Short description, Urdu:**
> مختصر ویڈیو اسکرولنگ کو اپنے فون پر نجی طور پر گنیں۔ نرم روزانہ حدیں مقرر کریں۔

**Full description, Urdu:** translate the English text above. Have a fluent speaker review it, since PRD §4 requires editorial review.

## 9. Final pre-submission checklist

- [ ] Package name, app name and icon are final.
- [ ] `versionCode` has been incremented.
- [ ] Privacy policy is hosted, and its URL is set both in `gradle.properties` and in Play Console.
- [ ] Billing backend is deployed, `billingBackendUrl` is set, and an RTDN test notification was received.
- [ ] Subscriptions `pro_monthly` and `pro_yearly` are active, a license-tester purchase unlocks Pro, and cancellation and refund behave correctly.
- [ ] Release AAB is built with your upload key, and Play App Signing is on.
- [ ] Real-device tests pass on at least 5 manufacturers: tracking, reminders, widgets, Urdu RTL, dark mode, large font.
- [ ] Detection is tested in each of the four apps with the **staging** build (`./gradlew assembleWebsiteStaging`, logs via `adb logcat -s ReelCounterAdapter`). On the emulator, YouTube occasionally sent no scroll events for a whole session, so measure how often that happens on real phones. If a platform doesn't detect, the listing must not imply that it does (PRD §0.9).
- [ ] Data safety, Accessibility declaration and video, content rating and target audience are all complete.
- [ ] Urdu copy has been reviewed by a fluent speaker.
- [ ] Closed test: ≥12 testers for 14 days (personal accounts).
