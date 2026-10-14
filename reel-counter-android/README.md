# PAICONS Self-Development — Android app

Native Kotlin + Jetpack Compose implementation of `../Reel_Counter_Production_PRD_Blueprint.md`, styled after the approved prototype in `../reel_counter_ui/`.

- minSdk 29 (Android 10), target/compile SDK 37
- AGP 9.4.1 · Kotlin 2.4 · Compose BOM 2026.09 · Room 2.8 · DataStore · Glance 1.2 · WorkManager · Play Billing 9.1
- English + Urdu (RTL), light + dark themes

## Going live on Google Play

Follow **[docs/PLAY_RELEASE.md](docs/PLAY_RELEASE.md)**. It covers signing, Data safety, the Accessibility declaration and video, the store listing, subscriptions and testing tracks. The privacy policy to host is [docs/privacy-policy.html](docs/privacy-policy.html), and the billing server is in [../reel-counter-billing-backend](../reel-counter-billing-backend).

Build variants:

| Variant | Use |
|---|---|
| `debug` | Development. Includes diagnostics logging and sample data. |
| `staging` | **Pre-release QA**: the same R8/minified code as release, debug-signed, with structural diagnostics logs (`adb logcat -s ReelCounterAdapter`). Use it on real devices to validate detection. |
| `release` | The Play upload. Signed with your upload key from `keystore.properties`. Build it with `./gradlew bundlePlayRelease`. |

## Build & run

Open this folder in Android Studio, or from a terminal:

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # 30 unit tests (engine, analytics, goals, estimates)
./gradlew assembleWebsiteRelease  # signed APK for paicons.com
```

When the project sits inside a OneDrive folder, build outputs go to `%LOCALAPPDATA%\ReelCounterBuild\` (OneDrive sync locks files mid-build). See `build.gradle.kts`.

Debug builds include **Settings → Load sample activity (debug)**, which fills the database with synthetic data for reviewing screens. Release builds don't include it.

## What's implemented (by PRD section)

| PRD | Where |
|---|---|
| S01 Welcome, S02 prominent Accessibility disclosure + explicit consent / decline, contextual permissions | `ui/screens/Onboarding.kt` |
| S03 Dashboard: health indicator, quick pause sheet, confirmed hero, separate estimate card, feed time vs whole-app time, per-platform cards, 7-day trend, goal ring, limited mode | `ui/screens/HomeScreen.kt` |
| S04 Analytics: Today/7d/30d, platform filter, hourly histogram, coverage annotations, platform share, sessions, estimates, retained-window streak; Pro hourly pattern + week-over-week comparison | `ui/screens/InsightsScreen.kt` |
| S05 Goals: combined + per-platform, 50/75/100% thresholds, quiet hours, notification permission on demand | `ui/screens/GoalsScreen.kt`, `reminders/` |
| S06 Widgets: Small / Medium / Large / Custom (Glance), per-instance config, pin request, "Updated at" | `widget/`, `ui/screens/WidgetsScreen.kt` |
| S07 Tracking health: global/per-platform pause, service/consent/version status, private detection check, OEM battery tips | `ui/screens/TrackingScreen.kt` |
| S08 Settings, language override, theme, reduced motion, update mode, reset | `ui/screens/MoreScreens.kt` |
| S09 Pro paywall: Play prices, auto-renew terms, restore, manage | `ui/screens/MoreScreens.kt`, `billing/` |
| S10 Floating reminder (off by default, separate permission, auto-hides, cooldown) | `reminders/AndroidReminders.kt` |
| S11 Empty / unavailable / estimated states | `ui/screens/MoreScreens.kt` |
| §5 Counting engine + state machine, dedupe, rebaselining, pause, idle/screen-off handling | `tracking/engine/` |
| §5.3 Per-platform adapters | `tracking/adapters/Adapters.kt` |
| §5.6 Bounded estimates (unavailable without evidence) | `data/TrackingRepository.kt` (`EstimateMath`) |
| §7 Room schema, 30-day rolling cutoff on every query, purge worker, delete-all, backup exclusion | `data/db/`, `work/`, `res/xml/*rules.xml` |
| §8 Formulas (delta vs yesterday, streak, session rate, coverage) | `data/StatsCalculator.kt` |

## Verified on the emulator (Android 17 / API 37)

- Onboarding, consent, limited mode, dashboard, insights, goals, widgets, tracking health and Urdu RTL all render as designed.
- **Real YouTube Shorts detection works end to end:** 4 real swipes produced exactly 4 confirmed transitions, and a swipe that bounced back to the same video was not counted.

## Verified results (Oct 2026, emulator API 37, YouTube 20.10.41)

- **Release (R8) pipeline counts exactly.** In a measured run, logs showed 2 bounces and 1 real page change, and the count went from 6 to 7.
- **Bounces are never counted.** Several adb swipes snapped back to the same video (net 0px), and the count did not move.
- **YouTube's event delivery is intermittent.** In about 3 of 12 sessions, YouTube sent no scroll events at all, even though the service was bound and videos changed. Nothing was counted in those sessions; nothing was invented. The cause is inside YouTube, and the pattern wasn't reproducible enough to work around safely. **Measure this on real devices** with the staging build before launch.
- **Play Billing telemetry:** the Google Play Billing library made its own network calls to `firebaselogging.googleapis.com`. Account for this in Data safety (see PLAY_RELEASE §4).

## Detection status — read this before launch

Adapter discovery (PRD M0) on YouTube build 20.10.41 found that its Shorts list reports **no item indices**, only per-frame scroll deltas. Two structural rules are implemented (no view IDs or text are read):

1. **Index rule:** a full-screen vertical container showing exactly one item; the index changes → transition.
2. **Offset rule (YouTube discovery):** net vertical offset settles within 8% of a whole container height → page change.

Both are **EXPERIMENTAL**. Instagram, Facebook and TikTok have **not** been tested on real devices. Instagram Stories (horizontal) are ignored, but other false-positive cases still need ground-truth testing. Nothing reaches the PRD's 95% precision/recall gate until §12 testing is done. The app labels every platform "Experimental · not validated".

## Open items (need your decision or input)

- **Billing backend:** built and tested in `../reel-counter-billing-backend` (Cloud Run + Firestore + Pub/Sub RTDN). Deploy it, then set `reelcounter.billingBackendUrl`. Until then, Pro stays locked by design.
- **Offline grace window:** placeholder is 72h (`EntitlementRepository.OFFLINE_GRACE_MS`).
- **Privacy policy:** fill the placeholders in `docs/privacy-policy.html`, host it, and set `reelcounter.privacyPolicyUrl`.
- **Play Billing telemetry:** Billing 9.x creates a `com.google.android.datatransport` database. Declare it in the Data Safety form; don't claim "zero collection".
- **Urdu copy** is a draft. It needs fluent editorial review (PRD §4).
- **Package name / brand** `com.reelcounter.app` is a working ID.
- **Play policy:** AccessibilityService approval is not assumed. `isAccessibilityTool=false` is set.
- A single Gradle module with package-level separation was used instead of the suggested multi-module layout. Manual DI instead of Hilt (the PRD allows "decide once").
