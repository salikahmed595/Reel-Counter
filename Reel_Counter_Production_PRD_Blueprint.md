# REEL COUNTER — Product Requirements Document and Implementation Blueprint

**Version:** 1.0 (engineering specification)  
**Prepared:** 14 October 2026  
**Working brand:** Reel Counter (final name, package ID, trademark clearance TBD)  
**Status:** Implementation-ready specification; platform detection and Play policy approval NOT validated.  
**Audience:** Product owner, Android engineers, QA, privacy reviewer, Google Play publisher.

## 0. Executive decision record

### Locked user requirements from Phases 1–5

- **Purpose:** Encourage healthier short-form scrolling habits, not maximize engagement in Reel Counter.
- **Platform:** Android native Kotlin + Jetpack Compose, Android 10+ (API 29 minimum), targeting Google Play policy's API 36+ requirement as of October 2026; recheck before submission.
- **Launch:** Pakistan; Instagram Reels, YouTube Shorts, Facebook Reels, TikTok all intended to be supported at public launch. Each platform requires separate validated adapter. Do not claim a platform is supported before passing its gates.
- **Event semantics:** Count once per qualifying transition *to a new short-form video* after entry into its feed, including a revisit; initial visible video is excluded. Not equivalent to full views or actual watch completion.
- **Automation:** No per-video user taps. Tracking requires relevant user-authorized Android special accesses and is not guaranteed to remain running.
- **Data:** Confirmed/verified detections separate from estimates; retention rolling 30 days for all activity and derived activity. No cloud account, media or message capture; counting and analytics operate offline.
- **Goals:** Combined across platforms and per-platform. Warnings at 50%, 75%, 100%; optional floating warnings; quiet hours off by default.
- **User control:** Pause all or individual app tracking; transparent detection health and troubleshooting; no forced permission grant just to see the dashboard (product-owner's preference overridden as compliance/usability recommendation).
- **Interface:** Exceptionally polished, wellbeing-oriented pastel design; primary dashboard, secondary detail views; primary widget metric customizable; small, medium, large, and configurable widget variants; responsive/balanced update preference.
- **Analytics:** Counts, trends, hourly breakdown, platform share, detected feed-session durations and whole-app foreground duration if permitted, plus clearly separated estimates; only rolling 30-day window.
- **Languages:** Full English and Urdu, including layout direction and manual override, default following device language.
- **Business:** Free core + optional monthly and annual Pro subscription; internet used only for billing/entitlement verification (and opening linked policies if user chooses). Core never locked behind paywall.
- **Testing:** >=20 device/OS/manufacturer configurations and real-user testing; proposed >=95% precision AND >=95% recall per platform on annotated qualifying transitions, plus robust false-positive and battery tests. Threshold is a release goal, not an achieved result.
- **Release:** Closed beta -> production, new Google Play developer account to be created.

### Important corrections and constraints

1. A visually observed swipe is **not** proof of a new video or a watch. The counter records *detected video transitions*, not video completion.
2. AccessibilityService is not a guaranteed cross-app video-tracking API. Some UIs expose insufficient accessible signals, and social apps change. Do not invent universal detectors or claim 95% before measurement.
3. A Play Store review may reject the proposed AccessibilityService use despite disclosure. Build a technical/policy feasibility gate early; actual formal review occurs on submission. Do not declare `isAccessibilityTool=true` for a generic digital wellbeing app.
4. UsageStatsManager can estimate *whole-app foreground time*, not identify Reels/Shorts. Feed-session duration can only be stated as observed when feed start/end is detected reliably.
5. An AccessibilityService is not guaranteed to survive force-stops, revocations, OEM limits, app updates, or restarts without gaps. Missed events cannot always be reconstructed.
6. Subscriptions conflict with absolute offline billing. Tracking remains fully offline; subscription purchase/verification needs intermittent network and backend. Never promise indefinitely verified offline Pro access.
7. Play subscription policies require recurring value. Static themes alone should be a one-time purchase. If recurring reports aren't genuinely valuable and maintained, reevaluate business model before launch.
8. Streaks spanning longer than retention must not be retained as a hidden proxy for deleted activity. Show streaks computed only over retained 30-day history, or explicitly ask user to revise retention policy.
9. If 95% gates cannot be met for all four launch targets, postpone public launch or obtain an explicit product scope change rather than mislabel unsupported platforms.

## 1. User personas and jobs to be done

- **Student/young professional:** Wants to know how much passive short-video consumption happens during study/work breaks; checks small widget.
- **Wellbeing-focused user:** Wants a gentle reminder at thresholds and actionable trend information, without shame or intrusive coercion.
- **Power user:** Wants per-platform breakdown, separate combined goals, accessible Urdu UI, and configurable widgets.
- **Privacy-sensitive user:** Wants counts on-device, no registration and no screenshots or social content saved.

**Primary job:** “Help me see how often I advance short videos today and stay within a daily limit.”

**North-star outcome (not simply usage):** Percent of opted-in users who reduce their 7-day average detected transitions from a personal baseline while retaining positive app satisfaction. This is an optional locally computed measure, not data uploaded by default.

## 2. Scope, priority, and acceptance

### Release 1.0 — Must-have (P0)

- Welcome / first-run explanation / separate prominent AccessibilityService disclosure / informed affirmative consent / permission onboarding and limited-mode dashboard.
- Per-app adapter for Instagram, YouTube, Facebook and TikTok passing agreed metrics on current tested versions, with clear “Detection unavailable” states.
- Correct transition counting semantics, no duplicate counting from repeated accessibility events, accurate boundaries across feed entry/exit, pause/resume and date changes.
- Confirmed event log (platform+timestamp+local UUID+minimal status), separately stored estimated buckets; Room, encryption/data minimization as appropriate, 30-day rolling deletion.
- Dashboard with confirmed count, estimate disclaimer, detected feed time, full-app foreground time if granted, goals, per-platform summary and 7/30-day trends.
- Local notifications at configured goal thresholds and repeat prevention; optional floating reminder with separate special permission; user-visible way to turn off.
- English and Urdu full translations; manually selectable language; RTL layout tests; accessible color contrast and dynamic font scales.
- Four widget presentation sizes/variants with per-widget configuration and user-selectable headline metric; widget states when service paused/unavailable.
- Free basic experience; monthly/yearly Pro subscription with Play Billing, server-side verification, restore and subscription status; no account creation needed for counting.
- Offline functioning, privacy policy, complete data safety declaration, signed AAB, automated tests, beta and release quality bar.

### Later or conditional (P1)

- Extra premium visual themes, more report templates, additional widget design presets; in-app contextual insights.
- Intelligent but entirely on-device reminders, after usability validation.

### Explicit non-goals

- Monitoring DMs, reading captions/comments, photographing or recording screens, running ads, selling browsing data, blocking other apps, bypassing Android security, social-login, cross-device synchronization, downloadable exports.
- Exact unique-video identities, cross-account deduplication, “fully watched” determination, reconstructing inaccessible historical Reel counts.

## 3. Screen-by-screen UX and navigation

The app has **one principal dashboard**, not an unusably giant one-screen experience. Secondary sheets/pages are allowed for advanced controls.

### S01 — Welcome / product story
- Screen: brand icon, “Understand your scrolling. Take back your time.” Short explanation of detected vs estimated counts and that platforms differ in trackability.
- Actions: `Get started`, `How tracking works`, `Privacy details`.
- No deceptive guarantee such as “Tracks every Reel with 100% accuracy.”

### S02 — Permissions and consent onboarding
- Separate in-app Accessibility disclosure before opening Android Accessibility settings. Explicit positive and negative actions; describe possible access to on-screen interface information, on-device processing, what is and isn't saved, and tracking purposes.
- Request AccessibilityService consent and settings action, and separately Usage Access if whole-app time is chosen. Ask notification permission only when activating reminders. Ask overlay permission only when enabling bubble.
- Declining still opens a demo/empty dashboard with “Enable automatic tracking.” User can revoke at any time.
- Consent changes and actual service status displayed distinctly.

### S03 — Home Dashboard
- Top: date, tracking health indicator (Active / Paused / Limited / Permission off / Detection issue), quick platform pause button.
- Hero: **128 detected transitions today**; estimates shown in adjacent clearly labeled line/card, not merged into hero unless a separate opted-in 'combined view' explains categories.
- Goal: progress vs combined daily limit; threshold markers at 50%,75%,100%.
- Four-platform cards show detected count, estimated count (if any), platform goal, active/paused/unavailable state.
- Time: ‘Detected short-video feed time’ distinct from ‘Total time in supported apps’; uncertain feed durations labelled estimated.
- Trends: compact 7-day chart, comparison with yesterday when present; no fabricated zero for missed tracking.
- Actions: Analytics, Goals, Widgets, Notifications, Settings, Pro. Secondary routes or sheets are permitted.
- States: first use/no data, no permissions, partial platform support, all paused, expired history, billing unavailable, RTL, large text, offline.

### S04 — Analytics detail
- Day / 7-day / rolling 30-day segments; combined or platform filter; hourly histogram, video transitions per session, platform share, detected and estimated categories separated.
- Confidence information and data completeness shown; do not claim trend reduction during missing tracking without annotation.
- Report generation fully local; Pro unlocks advanced chart layouts and comparison insights only; Free retains meaningful basic analytics.
- Only the previous rolling 30*24 hours of stored activity is available; no older charts or lifetime counts.

### S05 — Goals and reminders
- Set combined transitions/day and each platform transitions/day. Off toggle permitted; sensible editable suggested defaults (do not force arbitrary numbers).
- Notification thresholds: exactly 50%, 75%, 100%, each fired once per scope per local day; both combined/platform goals can trigger independently with configurable digest to avoid spam. A 100% event does not imply stopping access.
- Optional quiet hours initially off; skipped notifications expire, not flood the phone later.
- Weekly goals and under-limit streaks use only retained period. Reward reduction/consistency, never high viewing counts.

### S06 — Widget gallery and configuration
- Explain how to add widget from Android launcher (app cannot force-place widgets on every launcher).
- Widgets: Small: chosen single metric + platform/goal status; Medium: headline + progress + one comparison; Large: breakdown + mini trends + goals; Custom: chooser for metric, accent/theme, platform, readable layout preset, presentation size.
- Per-widget instance preferences: `appWidgetId`, `metric`, `platformFilter`, `stylePreset`, `showEstimates`, update preference; title and supporting copy localize.
- Optional quick-launch tap to Dashboard; widget shouldn't accidentally increase counts or claim true real-time when stale.

### S07 — Tracking Controls & Health
- Global and individual platform pause/resume.
- Detection status per integration (verified compatible app build, unknown version, not detected, user-paused, service disabled).
- Diagnostic guide: enable permission, OEM battery troubleshooting, social app UI/version changed, app force-stop explanation, private on-device sample test, support instructions; avoid promises of self-repair.

### S08 — Settings, Privacy & Language
- Follow system language (default) / English / Urdu, large text support, color scheme, reduced motion, widget mode, notification modes, manage permissions, data retention information, `Delete all local activity`, privacy policy, support and about.
- Permission revocation should immediately terminate tracking path and hide overlays; expiration of premium must not disable core.

### S09 — Pro Paywall / subscription
- Two subscription options: Monthly and Annual (local PKR pricing determined in Play Console, do not hardcode assumed rates).
- Show recurring price, billing interval, auto-renewal, cancellation instructions, benefits, and Free path. No coercion or confusing trials.
- Premium benefits: genuinely ongoing fresh rolling reports, advanced comparisons, enhanced widget layouts, evolving theme library. Avoid promising future content not resourced.
- Manage subscription opens appropriate Google Play management experience; restore/reconcile purchase safely.

### S10 — Floating reminder preview and controls
- Toggle off by default. Demonstration of restrained, dismissible, non-blocking overlay; overlay should never mask permissions, payment confirmations, system dialogs, or important controls.
- Disable control easy to reach. Requires separate opt-in and permission; hide during sensitive app contexts if detectable, and prefer standard notifications when not safe.

### S11 — Error / offline / no-data views
- Explicit “Tracking is unavailable” vs “No Reels detected.”
- “Estimated activity (not directly observed)” with methodology explanation.
- Billing offline uses last recently server-verified entitlement under a capped offline grace strategy (precise grace window TBD). Count features remain functional regardless.

## 4. Design system and bilingual UX

**Visual direction:** restrained pastel luxury, editorial typography, warm ivory backgrounds, sage/mist/lavender accents, dark charcoal text, generous spacing, curved cards, subtle graph animation, haptic confirmation sparingly, accessible light/dark adaptation. Do not use pale-on-pale unreadable text.

**Initial semantic palette (subject to accessible contrast audit):** Ivory `#F6F5F0`, Sage `#D7E9DF`, Mist `#DCE5F5`, Lavender `#E8DCF0`, Ink `#27342D`, Primary green `#386653`, muted content `#52635A`.

- Use semantic `ColorScheme`, dark variants, accessibility contrast audit (WCAG 2.2 AA as internal UI target), font scaling, switch/talkback semantics.
- Urdu is `ur-PK` with RTL layout, readable bundled/platform-supported Nastaliq or Naskh typeface selected from legal-distributable fonts (no raw font distribution outside app), numerals and date formatting thoughtfully tested. Icons/arrows mirror where meaningful; charts should remain semantically consistent.
- Follow device language using resource configuration; manual in-app override takes precedence and is persistent. Automatic detection means **device locale**, NOT reading language from social media content or messaging.
- Include English and Urdu disclosure/paywall/privacy copy; send both through fluent editorial review rather than machine translation without inspection.
- Native android widgets may have layout limitations relative to Compose; use Jetpack Glance with widget-specific components.

## 5. Counting semantics and event engine

### 5.1 Operational definition

Let a session be a continuous detectable period inside a supported short-form video feed. On feed entry, establish `BASELINE_VISIBLE_VIDEO` but add **zero**. For each later sufficiently evidenced transition to a different visible short-video item, emit **one** `CONFIRMED_TRANSITION` when the adapter can determine the transition changed the feed's displayed item. Returning from B to A counts again if it is a new qualifying transition. A repeated accessibility notification representing the *same* visible video does not count again. If identities cannot be resolved sufficiently, do not count. Video identifiers, URLs, captions or frames must not be persisted.

`CONFIRMED` is a product classification meaning **matched defined observable signals and passed adapter criteria**, not an independent guarantee that a person truly watched content.

### 5.2 Inputs (ephemeral only)

- Foreground app package and window event, for the four supported packages ONLY.
- Accessibility event type e.g. scroll/window state/content change; structural properties of accessible nodes only when necessary to distinguish short-video feed and transitions.
- Elapsed realtime, wall timestamp, session state, Android lifecycle/permission flags.
- UsageStatsManager only for **whole-app foreground time**, where separate usage access is granted and demonstrably accurate.
- No screenshots or MediaProjection; no reading/storing caption, comments, messages, video thumbnails, audio, creator names, or video IDs.

### 5.3 Adapter interface

Each adapter has a version-tested `recognizeFeed(observation)`, `recognizeTransition(previous, current, event)`, `confidenceAndReason()`, `reset()`, and a reliability state tied to observed app version/ruleset. Package matching must use exact allowlist and verified package signatures when feasible; platform app UI can vary by region and versions.

Suggested integration directory: `InstagramReelsAdapter`, `YoutubeShortsAdapter`, `FacebookReelsAdapter`, `TikTokFeedAdapter`.

**Do not invent selector IDs**. Discover UI accessibility structures through deliberate instrumented tests. Do not bypass platform security or use privileged/system-only APIs.

### 5.4 State machine

```
SERVICE_OFF / PERMISSION_DENIED
  -> WAITING_FOR_ALLOWED_APP
  -> ALLOWED_APP_FOREGROUND
  -> FEED_DETECTED_BASELINE (no count)
  -> TRANSITION_CANDIDATE
      -> VERIFIED_NEW_ITEM -> EMIT_CONFIRMED_EVENT -> FEED_DETECTED_BASELINE
      -> DUPLICATE_UI_EVENT -> discard -> baseline
      -> INSUFFICIENT_EVIDENCE -> UNCERTAIN (no confirmed count)
  -> FEED_EXIT / APP_BACKGROUND / SCREEN_LOCKED / PAUSED -> close session
```

A “confidence score” must not be used as magical certainty. Validate per signal and establish controlled false-positive rates. `TYPE_VIEW_SCROLLED` alone is insufficient: feeds can emit scroll events from comments, menus or UI chrome. No service can reliably recover all activity missed during force-stop/permission revocation.

### 5.5 Debounce, idempotency and boundaries

- Collapse repeated accessibility events tied to the same stable visible item and single transition. Require state change plus adapter-specific signals; time-only debounce cannot prove distinct items (fast swipes need support).
- Process ordered events in a single-threaded actor/channel or protected sequential queue; assign `eventId` local UUID, `sessionId` local UUID; unique idempotency key in Room for repeated callbacks where derivable without persistent content.
- If new app foreground, feed exit, orientation change, UI modal/comment overlay, screen lock, split-screen/PiP, or service reconnect: establish a fresh baseline before any count; define conservative transitions.
- For midnight rollover use the captured event instant plus local zone at event time; handle timezone/DST and manual clock changes deterministically. Don't double count during replay or widget updates.
- A deleted platform is not supported; pause is honored immediately.
- Adapter false-positive prevention is more important than aggressive counting when signals are uncertain.

### 5.6 Estimated counts: bounded and honest

- Store in separate `EstimateBucket` only if there is an observable uncovered period and a defensible, locally calibrated transition rate from *recent confirmed sessions* in the same platform; keep an estimate range, method/version, observation coverage and a clear “estimated” label.
- A rate calibrated from confirmed sessions may be biased; even interval bounds are heuristic until validated in testing. Start with **estimate unavailable** when evidence is insufficient.
- Never silently add estimates into detected totals, goals, streaks, or primary widget counts. If product chooses an optional 'estimated activity' view, explain uncertainty explicitly.
- Optional estimation formula for validated periods: `estimatedTransitions = usableUnobservedFeedMinutes * recentPlatformRatePerMinute`. **Do not** use total Instagram foreground time as a proxy for Reels minutes; time in chats/photos/posts can dominate.
- If tracker is force-stopped and no feed time is observed, show a gap, not a fabricated number.
- Reports record `detected`, `estimated`, and `unobserved/unknown` categories separately. Display “~N” only for actual computed estimates, not for missing data.

### 5.7 Observed time distinctions

1. `Feed session time` — time spent in a recognized short-form feed, between observed entry and exit; if exit missing, mark incomplete / uncertain.
2. `Whole-app foreground time` — from Usage Access-derived foreground events, not short-video time.
3. `Estimated feed time` — can be shown only if independently justified; separate from observed times.

Do not subtract one from the other to infer precise video time, and do not equate app foreground with attention/watching.

## 6. Engineering architecture

### 6.1 Stack

- Kotlin, Coroutines/Flow, Jetpack Compose + Material 3 design primitives, AndroidX Lifecycle, Navigation Compose, Hilt (or Koin, decide once), Room, DataStore, Jetpack Glance, WorkManager, NotificationManager, Google Play Billing Library (currently documented 9.x; pin the stable version at implementation time).
- `minSdk = 29` (Android 10), target Google Play's API 36 or later current policy, compile SDK aligned.
- Server: minimal purchase/entitlement verification endpoint + RTDN subscriber + minimal secure entitlement ledger, **no Reel activity** or analytics transmission. Backend runtime vendor TBD; use environment-backed server credentials, TLS, signed/validated purchase tokens and audit logs that exclude activity.

### 6.2 Logical architecture

```
External social app UI (no platform API contract)
       |
User-authorized Android AccessibilityService
       |
Package allowlist -> per-platform Adapter -> Event Sequencer
       |                           |
       |                    Service Health State
       v
Transition Repository -----> Room (confirmed transitions, sessions, estimates)
       |                         | 
       +--> daily aggregations ->+--> Dashboard / Analytics / Goals
       |                         +--> Glance widget (read-only rendering)
       +--> goal threshold evaluator --> Local Android notifications
       +--> optional overlay controller (separate permission)

UsageStatsManager (separate authorization) --> Whole-app-time aggregator

DataStore --> preferences / consent version / pause / goals / widget config

Google Play Billing Client <--> Entitlement repository <--> Minimal billing backend
                                                           <--> Google Play API + RTDN
```

### 6.3 Suggested modular Gradle layout

```
app/
core/model/ core/database/ core/datastore/ core/designsystem/
core/time/ core/permissions/ core/localization/ core/testing/
feature/onboarding/ feature/dashboard/ feature/analytics/
feature/goals/ feature/widgets/ feature/settings/
feature/tracking-health/ feature/reminders/ feature/billing/
tracking/service/ tracking/engine/ tracking/estimate/
tracking/adapters/instagram/ tracking/adapters/youtube/
tracking/adapters/facebook/ tracking/adapters/tiktok/
billing/backend/          # separate backend repository in production
```

- MVVM + unidirectional UI state flow; repositories expose `Flow<...>`; background service must never import UI layer.
- Treat AccessibilityService as system-managed lifecycle component. Do **not** rely on WorkManager or an always-on foreground service to capture screen transitions; neither substitutes for actual events.
- Use WorkManager for eventual history cleanup, derived daily aggregation repair, entitlement rechecks while eligible, **not per-Reel detection**.
- The widget must update from durable repository state and never be a counter authority itself.
- Avoid requesting broad package visibility or any permission not justified in feature contract.

### 6.4 Permission inventory

| Access | Why | When | If denied |
|---|---|---|---|
| AccessibilityService (user-enabled special service) | Recognize supported feed UI transitions | Separate first-run disclosure and explicit opt-in, then OS settings | Dashboard still usable in limited mode; no confirmed automatic tracking |
| Usage Access / PACKAGE_USAGE_STATS | Whole-app foreground usage time only | When user enables whole-app time metric | Hide/label app-time unavailable; do not infer video time |
| POST_NOTIFICATIONS (API 33+) | Goals alerts/status notification | When user enables alerts | In-app progress still works; no system alerts |
| SYSTEM_ALERT_WINDOW (if TYPE_APPLICATION_OVERLAY) | Optional bubble reminder | Only on deliberate overlay activation | Standard notifications instead |
| INTERNET | Subscription purchase/verifications; privacy/support links on user action | Billing/link interactions | Core tracking/offline dashboards still function |
| BILLING | Google Play payments | Pro purchase/restore | Free tier always works |

Other manifest capabilities, such as boot receivers or FGS, require a separately documented justified use and Android-version-specific policy review; do not add by default.

### 6.5 Operating system resilience

- `AccessibilityService.onServiceConnected` restore state from Room/DataStore, no backfill of missed transitions.
- `onInterrupt` or service disconnect -> health state; notify next time it's determinably detectable; avoid a background watchdog that promises impossible continuity.
- Device reboot: if user authorization persists and OS restarts service, create new session baseline; no speculative replay.
- OEM restrictions: show accurate targeted help for Samsung, Xiaomi, Oppo, Vivo etc; never instruct users to disable all security protections.
- Device battery mode setting controls widget update coalescing, work scheduling and visual smoothness; **should not** reduce confirmed event counting fidelity when the service is delivering events.

## 7. Room data model and retention

Only *video-detection records* must be restricted to platform, counts and timestamps; settings, goal configuration, minimal session state and purchase entitlements are distinct necessary app metadata. Do not save social-video identifiers/content.

### Tables

1. `confirmed_transition` — `id TEXT PRIMARY KEY`, `platform TEXT`, `occurredAtUtcMs LONG`, `zoneIdAtEvent TEXT`, `sessionId TEXT`, `dedupeKey TEXT UNIQUE NULL`, `detectorVersion INT`. The detector can save a local event UUID but must not store content fingerprints.
2. `tracking_session` — `sessionId TEXT PK`, `platform TEXT`, `startedAtUtcMs`, `endedAtUtcMs NULL`, `endReason TEXT`, `status TEXT` (`OBSERVED`, `INCOMPLETE`); no video content.
3. `estimate_bucket` — `id TEXT PK`, `platform TEXT`, `intervalStartUtcMs`, `intervalEndUtcMs`, `estimateValue DOUBLE`, `lowerBound DOUBLE NULL`, `upperBound DOUBLE NULL`, `method TEXT`, `methodVersion INT`, `evidenceQuality TEXT`; no estimated count if evidence insufficient.
4. `daily_platform_aggregate` — `localDay TEXT`, `zoneId TEXT`, `platform TEXT`, `confirmedCount INT`, `estimatedCount DOUBLE NULL`, `observedFeedSeconds LONG`, `appForegroundSeconds LONG NULL`, `coverageState TEXT`; unique key day-zone-platform. This is a materialized cache, reconciled from source events and sessions.
5. `goal_threshold_delivery` — `id TEXT PK`, `localDay TEXT`, `zoneId TEXT`, `scope TEXT`, `goalRevision INT`, `thresholdPercent INT`, `sentAtUtcMs LONG`, `deliveryState TEXT`; unique(scope,day,zone,revision,threshold). Prevents duplicates.
6. `tracking_operational_state` — durable settings/status with `platform`, `paused`, `lastSuccessfulEventAtUtcMs NULL`, `adapterStatus`, `permissionStatus`; aggregate status alone, no content logs. Can reside in DataStore.
7. `widget_configuration` — instance ID, metric selection, platform scope, size/preset, theme, showEstimates, updateMode. Remove on widget deletion. DataStore or dedicated Room table.
8. `user_preferences` / DataStore — per-platform and total goals, quiet hours, consent version/timestamp, locale override, notification and overlay settings, battery mode, onboarding completion. No user name/phone/account.
9. `entitlement_cache` — product tier, last verified time, expiry/grace state, opaque server reference (NOT raw developer API credentials); store only what's necessary, securely.

### Integrity and retention

- Transaction: persist confirmed event and invalidate/recompute the affected aggregate atomically or through idempotent processing. A widget refresh failure must not roll back recorded counts.
- All activity-bearing tables and cached aggregates older than **30 rolling days** are purged, including old notification history and estimates. Prevent older activity from leaking via backups, exported debug logs or analytics SDKs.
- Cleanup at launch, after first session of day, and periodic WorkManager when scheduler allows. For strict max retention, **queries MUST always filter to rolling 30-day cutoff** even before the cleanup job executes; physical purge can be delayed by Android scheduling.
- Rolling 30 days is 30 x 24h from current instant, not always “30 calendar dates.” Charts must label if displaying clipped first/last days. Streaks and 30-day reports are limited by retention.
- Purge on user request immediately using a transaction; remove summary caches, widget snapshot, operational timestamps as relevant. Settings retained unless user chooses “Reset app.”
- Use Android backup exclusions or disable cloud auto-backup for activity data, consistent with user promise of no cloud sync. Backups must not resurrect expired 30-day history.
- Persist UTC instants, zone IDs at event time, and report local day keys with explicit timezone handling. On device-time tampering, don't silently fabricate changes.
- Room foreign keys/indexes: `(platform, occurredAtUtcMs)`, `(intervalStartUtcMs, platform)`, `(localDay, zoneId, platform)`. Migration tests mandatory.

## 8. Analytics specifications and formulas

- `confirmedToday = COUNT(confirmed_transition where occurredAt in current local day and within retention cutoff)`.
- `confirmed7Day = sum retained counts across 7 complete/relevant local date buckets)`; under-observed days must show incomplete coverage.
- `dailyGoalProgress = confirmedCount / configuredGoal`, capped visually at 100% with overage text beyond goal. **Never include estimates** in goal attainment unless a separately user-chosen policy is specified (default is confirmed only).
- `detectedFeedDuration = sum completed observed feed intervals`, except incomplete sessions flagged; never claim 100% coverage.
- `appForegroundDuration = UsageStats derived with known scope; clarify it includes stories, chats and other content`.
- `deltaVsYesterday = (today - yesterday) / yesterday`, handle zero denominator with “No comparable data”. When yesterday missing, show “Comparison unavailable”.
- `sessionRate = transitions / observedFeedMinute` displayed only if duration quality adequate.
- Reports should display non-overlapping categories; no averaging daily counts over days with tracking disabled without a coverage warning.
- `underLimitStreak` is number of consecutive sufficiently observed days with confirmed counts under goal within the **retained window**, and exclude unobserved days rather than rewarding zero due to disabled tracking. If tracking coverage is unknown, streak is “unverifiable” rather than continued.

## 9. Widget definitions and update strategy

- Small (approx 2x2 launcher cells): one large detected count or user selected metric, small label, tiny status icon.
- Medium (approx 4x2): metric, goal progress, chosen platform, simple comparison.
- Large (approx 4x4): total, four platform counts, goal, tiny trend visual, yesterday comparison.
- Custom: choose scope, main metric, color scheme, data density, estimate visibility; size ultimately depends on launcher dimensions.
- Glance `GlanceAppWidget` state from repository; `appWidgetId` config must be durable. Widget tap launches appropriate deep link.
- Event-driven `updateAll` or specific-widget updates **when app/service awake**, throttle burst refreshes. Periodic WorkManager/widget OS refresh as supported. Android `updatePeriodMillis` cannot request more frequent delivery than every 30 minutes; WorkManager periodic minimum typically 15 minutes; neither guarantees real-time refresh. Never promise per-Reel animation in widget.
- `Balanced`: coalesce updates (e.g. 1–5 minutes when active subject to OS), scheduled background refresh.
- `Responsive`: request a best-effort refresh after significant count changes when service is already awake, bounded by measured battery and launcher behavior.
- Show “Updated at…” or tracking stale indicator when recency is known; after history deletion show empty state.

## 10. Notifications, overlays, and privacy-safe nudges

- Thresholds 50%, 75%, 100% for combined and per-platform daily goals; individual threshold fires once per local day/scope/goal revision. Define edited goals: reset threshold delivery for that specific new goal revision without spam; preferably only fire on a **new crossing after edit**.
- Coalesce simultaneous combined/platform notifications into one digest where appropriate. Respect silent/notification-denied/quiet hours settings, OS DND, and per-day safety cap (initially max 6, configurable after user research).
- Suggested respectful copy: “You’ve reached 75% of your daily Reels limit. Want to take a short break?” No shame, fear, or judgmental labels.
- Optional overlay: only after separate OS special-permission request, opt-in, preview and dismiss. Should not intercept touches outside bubble or obscure important system UI; turn off when paused. Overlay eligibility and Play approval reviewed independently.
- Do not use AccessibilityService to simulate scrolling/block other apps, change system settings, or override user security controls.

## 11. Free/Pro requirements and minimal billing backend

**Free (permanent):** automatic tracking when enabled and technically supported, 4 platform counts, detected vs estimated separation, 30-day basic history, basic goals/thresholds, one usable style per widget size, full privacy controls, language selection, pause/resume, tracking health.

**Pro subscription:** additional widget styles/layout presets, animated aesthetic options where Android allows, advanced 7/30-day comparisons, deeper hourly patterns, configurable report cards, advanced goal insights, ongoing additions of report templates/themes. Never paywall privacy, accurate labels, permission repair, or counting reliability.

**Subscription viability gate:** prove continued value is genuinely recurring. Monthly/yearly store products `pro_monthly`, `pro_yearly` with exact store price localization and auto-renew terms. If Pro is mainly fixed themes, seek a product-owner decision to switch to one-time purchase instead of risking subscription policy conflict.

**Billing workflow:** Google Play BillingClient -> purchase token -> HTTPS server -> Google Play Developer API validation -> entitlement stored server-side -> client receives short-lived verified entitlement -> acknowledge promptly and idempotently -> RTDN listener updates lifecycle -> client checks on startup/connectivity and on billing events. Support pending, active, grace, on-hold, canceled-but-active, paused, expired, refund/chargeback, restore after reinstall, changes in Play account, no network. Do not grant Pro for unverified PENDING state. Never ship service account JSON, secrets, or developer private keys in APK. Never send activity history to billing backend.

**Offline entitlement:** use a documented limited cached-verification policy with a fair grace window, not unlimited forever and not immediate revocation during a network outage. Exact maximum grace period (e.g. 72 hours or 7 days) remains a business/security decision. Core Free features unaffected.

**Backend minimal tables:** entitlement key (pseudonymous per installation or server-issued stable id), Play purchase token securely stored, subscription product/status, expiration/grace flags, verifiedAt and RTDN event id dedupe; server access logs avoid personal identifiers where possible. Reinstall without accounts relies on Google Play restoring purchases and re-verification; design for stable association without collecting activity.

## 12. QA: 95% target, 20+ configurations, release gates

### 12.1 Define measurable accuracy

On a consented ground-truth test, observers label each qualifying **transition after feed entry**, separately for each platform. Do not include the initial video. Also annotate non-events: comments scrolling, regular feed posts, settings screen, ads/stories/lives, scroll bounce, overlays, split screen, return/reconnect.

- **TP:** observed qualifying transition matched one confirmed counted event.
- **FP:** confirmed counted event with no matching qualifying transition (including duplicate).
- **FN:** qualifying transition with no matched count.
- **Precision:** TP/(TP+FP) >=0.95 on each platform.
- **Recall:** TP/(TP+FN) >=0.95 on each platform.
- **Reported reliability:** include sample size, confidence intervals and false positives per hour. Raw observed ratio alone is not sufficient to generalize across app versions.
- Require 95% precision/recall on each key Android family and platform where statistically estimable; a single overall aggregate can hide disastrous failures on one platform.
- Suggested evidence: at least 1,000 independently annotated transitions per platform across >20 configurations, >30 real users, multi-speed scrolling, in-feed ad interruptions, network variability and app UI version variants. These are proposed QA plan numbers subject to feasibility and statistical power assessment.
- Require coverage and uncertainty metrics: percent of observed feed time confidently classified, missed tracking periods; don't label a detector good merely because it counts few and produces no false positives.
- Set a date of supported social-app builds. Run regression against updates before production and during maintenance.

### 12.2 20+ configuration matrix

Baseline test cells spanning:
1. Pixel / Android 10 emulator/reference; 2. Pixel Android 11; 3. Pixel Android 12; 4. Pixel Android 13; 5. Pixel Android 14; 6. Pixel Android 15; 7. Pixel Android 16;
8. Samsung mid-range Android 11; 9. Samsung mid-range Android 13; 10. Samsung flagship Android 14; 11. Samsung flagship Android 16;
12. Xiaomi MIUI Android 10; 13. Xiaomi Android 12; 14. Xiaomi HyperOS Android 14; 15. Xiaomi HyperOS Android 16;
16. Oppo Android 11; 17. Oppo Android 14; 18. Vivo Android 12; 19. Vivo Android 15; 20. OnePlus Android 15;
21. low-RAM device Android 10; 22. tablet/foldable Android 14+; 23. 120Hz device; 24. RTL Urdu + largest font size.

**Important:** This is a *target matrix*, not a claim that specific device/OS combinations are currently available. Confirm exact models/OS builds before purchasing/recruiting; emulators do not replace real accessibility UI/device behavior.

### 12.3 Automated tests

- Unit: event state transitions, initial exclusion, deduping, repeat A-B-A, transition after resume, threshold calculations, retention cutoff, rollover across midnight, timezone changes, input event ordering, estimation confidence and null paths, locale formatting, subscription state reducer.
- Instrumented: Room CRUD/migrations/transaction consistency, service config and lifecycle on approved test apps, permissions/offline, widget configuration/resize/delete, notifications/overlay, RTL with large fonts, process recreation.
- Integration: Google Play Billing sandbox/licensed tests for pending purchase, cancellation, grace, hold, refund, yearly plan and restore.
- End-to-end: manual ground truth on real social apps using consented observer tally/recording restricted to QA and promptly deleted; raw social content must not be collected in production telemetry.
- Security: no sensitive Accessibility logs/screenshots/video IDs, payment credentials out of APK, HTTPS only to billing, no insecure export provider, minimal permissions, audit libraries, app backups, debug flag off.

### 12.4 Battery / performance plan

- Benchmark on unplugged devices vs control run with identical social-app sessions and screen brightness, radio, OS battery mode. Report incremental mAh/24h, CPU wake time, resident memory, widget update attempts/success, background restrictions, crash/ANR rates.
- Set numerical battery gate **after initial measurement**, per user's instruction. Example aspirational targets must not be passed off as demonstrated results.
- User experience target: scrolling performance not visibly degraded, no continuous polling, no repeated full accessibility tree scans on every unrelated event, responsive dashboard under normal storage conditions.
- Beta exit: no P0 bugs, all four adapters pass gate, permission disclosures complete, Play Console declaration prepared, functional release signing and billing verification.

## 13. Security and privacy compliance

- In-app prominent AccessibilityService disclosure, separate from privacy policy, affirmative consent and deny option. Explicitly state possible access to content displayed in supported apps and exact on-device restrictions, not merely “we read nothing.” The API may expose sensitive UI text; implementation must deliberately discard content and allowlist packages.
- Do not set `isAccessibilityTool=true` unless the app is genuinely primarily a disability accessibility tool, which the present product is not.
- Play Console Accessibility declaration with recorded real user flows: app opening, disclosure, decline, approve, OS settings, actual counting feature.
- Usage Access, notification, and overlay requested contextually, not at once; Play permission declarations as applicable.
- Privacy policy public HTTPS webpage, within app and store listing, covers on-device access, local retention/deletion, billing server data and third parties. No data safety claim of “zero collection” until auditing SDKs and billing telemetry.
- Restrict third-party SDKs. Crash collection is optional; avoid SDK analytics that silently upload social usage metadata.
- Activity history excluded from backups or encrypted/offline policy; user delete-all; update app's Data Safety and privacy text whenever behavior changes.
- Confirm notification/floating overlay are not used deceptively and stay user-controlled.

## 14. Google Play launch and operational plan

1. Establish brand/trademark and choose package name before Play Console creation.
2. Open Google Play developer account (Google lists US$25 one-time fee); verify identity and choose personal vs organization appropriately.
3. Create app signing keys and Play App Signing; `applicationId`, versioning, reproducible CI signed AAB, release notes, support channel.
4. Minimum Android 10 `minSdk=29`, target API >=36 as of 2026-10-14; check policy at upload.
5. Prepare original icon, screenshots, feature graphic, bilingual store description, transparent statements about compatible platforms and detection limitations.
6. Prepare linked public privacy policy, within-app policy, Data Safety form, app-access instructions if needed, content rating, audience/ads declarations, country availability Pakistan, subscription Play products in PKR if supported by your merchant settings.
7. Prepare accessibility declaration and demo evidence; formal approval NOT assumed. Investigate policy risk before heavy production work.
8. Test internal builds first even though planned distribution is closed beta -> public. Internal track does not invalidate closed-beta-first *public* strategy.
9. For a new personal developer account, current published policy requires **at least 12 opted-in testers continuously for 14 days** before applying for production access; recruiting 20+ real test configurations is a separate product QA requirement, not a replacement for the official rule.
10. Closed beta: collect private in-app/offline QA surveys or direct bug reports, not silent raw Accessibility data; fix crashes/detection/billing/RTL.
11. Apply for production access when eligible; satisfy declarations and review; staged release with crash/ANR monitoring that respects privacy.
12. Monthly maintenance: test current social app versions against adapters, update to new Android policy/SDK, handle billing and developer support.

### Hard release-blockers

- No verified adapter on **any** of four platforms, because scope requires all four.
- 95% precision+recall release gate unmet on independent ground-truth dataset for one of the four.
- Required AccessibilityService use appears noncompliant, cannot be declared correctly, or is rejected by Play and no approved substitute is available.
- Estimates merged into verified counts without labels, or fabricated during offline blind periods.
- Missing cancel/resume controls, permission revocation handling, invalid consent, missing privacy policy, insecure billing, or data retention exceeding contract.
- Untranslated critical Urdu screens or unreadable RTL/large-text screens.
- Subscription gives only one-time value without sustained benefit, or paywall obscures core free functionality.

## 15. Delivery milestones and acceptance evidence

**M0 Feasibility + compliance:** small Android service prototype, four feed-adapter discovery reports, accessibility permissions behavior, short privacy/disclosure draft, risk sign-off. If unfeasible, stop; do not build an unsupported promise.

**M1 Foundation:** native app shell, offline Room/DataStore, design tokens, localization, navigation, tests, error states.

**M2 Tracking:** four platform adapters, sequential detector, event persistence, estimate labeling, observed-time distinctions, service health, pause/resume, rollover and retention.

**M3 Consumer experience:** dashboard, analytics, goals, notifications, optional overlay, all widgets, settings, accessibility usability polish.

**M4 Pro:** billing UI, backend purchase verification, RTDN lifecycle, recurring subscription differentiation, billing sandbox tests.

**M5 QA & beta:** 20+ configurations, manual independently scored accuracy, battery benchmark, RTL audit, Play declarations, signed beta AAB, closed testing.

**M6 Production:** approval checks, production eligibility, Pakistan rollout, post-release platform version compatibility cadence.

No invented delivery dates: estimate engineering duration only after M0 feasibility and team size have been scoped.

## 16. Open decisions and risks — do not treat as answered

| Item | Status / default recommendation |
|---|---|
| Official brand, app icon, package name, domain | TBD; trademark and Play search before freezing. |
| Android social-app package versions and feed recognition signatures | Unknown; discovered during device testing, maintained per version. |
| Four-platform 95% precision+recall | Not validated, high feasibility risk. |
| Google Play acceptance of AccessibilityService for general digital wellbeing count | Unverified until formal Play review; must satisfy declarations and consent. |
| User-selected “Build fully before policy review” | Engineering strongly recommends M0 feasibility/compliance gate; formal Play review at submission. |
| Exact estimated count calibration/confidence bounds | Must be designed and validated; default unavailable unless sufficient evidence. |
| Whether to retain estimation method metadata | Necessary for transparency, no user content; confirm with privacy brief. |
| Goals triggered by confirmed counts only | Recommended default; changing requires explicit approval. |
| Battery update modes' exact intervals and battery benchmarks | Measure on real OEM devices, then set SLA. |
| Final Pro features and sustained ongoing value | Define a recurring editorial/product roadmap; static themes alone insufficient. |
| Monthly/yearly price and offers | Determine in PKR after actual market and billing tests. |
| Billing verification backend vendor and offline grace window | TBD; no activity data on backend. |
| Beta sample sizes, ground truth stats power, confidence intervals | Set with QA and actual device availability. |
| Notification cap / goal edits / DND behavior | Proposed defaults require usability test and approval. |
| Urdu typography style and full localization review | Language expert review and RTL screen QA. |
| Terms of Service, ownership entity and support email | TBD before store listing. |

## 17. Sample user acceptance criteria (Given/When/Then)

- **AC01 First-entry exclusion:** Given tracking is active and the user opens Instagram's supported Reels feed, when the first video appears without transitioning, then confirmed counter remains unchanged.
- **AC02 Transition count:** Given the first Reel is baseline, when user moves to a distinct second Reel and adapter independently confirms a visible item transition, then confirmed count increments exactly once.
- **AC03 Duplicate UI notifications:** Given the same second Reel emits 10 AccessibilityEvents, when processed, then only one confirmed transition is stored.
- **AC04 Repeat viewed:** Given the sequence A->B->A with both transitions verifiable, then two increments are recorded (the initial A remains zero).
- **AC05 Inconclusive:** Given a comments panel scroll, when a scroll event occurs, then count stays unchanged.
- **AC06 App switch:** Given user leaves the Reels feed for messaging/regular Instagram, when app usage resumes later, then baseline resets; no false transition for initial re-entry video.
- **AC07 Permission denied:** Given user rejects tracking access, then app is still navigable, displays limited tracking explanation, and no event is captured.
- **AC08 Pause app:** Given TikTok tracking paused, when TikTok events arrive, then no confirmed TikTok transitions stored; other enabled platforms still work.
- **AC09 Estimate gap:** Given service missed 10min without independently observed feed time, then “Tracking gap” shown and no fabricated estimate added.
- **AC10 Alerts:** Given 100 count goal and earlier count 49, when a valid transition yields 50, then 50% threshold sends no more than once that day, subject to quiet hours/permission.
- **AC11 Rollover:** Given last detected transition at 23:59 and next at 00:01 local time, then separate daily aggregates updated without duplicates.
- **AC12 Deletion:** Given activity older than rolling 30d, then queries never expose it even if WorkManager physical purge delayed; eventually disk purge occurs; delete-all immediately clears it.
- **AC13 Widget status:** Given Android throttles widget, then widget does not show fabricated real-time count and eventually renders latest persisted confirmed count after allowed refresh.
- **AC14 Urdu:** Given `ur-PK`, then critical screens, thresholds, notifications, widget text and paywall display proper Urdu/RTL formatting without truncated numbers.
- **AC15 Offline:** Given no network, then confirmed counting, history, goals, and widgets continue functioning; billing cannot purchase/verify until network returns.
- **AC16 Billing:** Given Play reports pending purchase, Pro features remain locked until verified PURCHASED; on expiration core counting remains functional.
- **AC17 Privacy:** Given user opens any unrelated app containing private messages, the tracking service neither persists content nor counts its events.

## 18. Official references checked 14 October 2026

- Google Play AccessibilityService policy: https://support.google.com/googleplay/android-developer/answer/10964491
- Google Play permissions policy: https://support.google.com/googleplay/android-developer/answer/16558241
- Google Play User Data / privacy policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play new personal developer testing: https://support.google.com/googleplay/android-developer/answer/14151465
- Target API policy (from 31 Aug 2026): https://developer.android.com/google/play/requirements/target-sdk
- Android AccessibilityService: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Android UsageStatsManager: https://developer.android.com/reference/android/app/usage/UsageStatsManager
- Android Glance widget updates: https://developer.android.com/develop/ui/compose/glance/glance-app-widget
- Android widget configuration: https://developer.android.com/develop/ui/compose/glance/configuration
- Android per-app language settings: https://developer.android.com/guide/topics/resources/app-languages
- Google Play Billing integration: https://developer.android.com/google/play/billing/integrate
- Google Play Billing backend: https://developer.android.com/google/play/billing/backend
- Subscription policy: https://support.google.com/googleplay/android-developer/answer/9900533
- Play Console account setup: https://support.google.com/googleplay/android-developer/answer/6112435

---
**Specification conclusion:** The surrounding Android app, widgets, local data and commerce can be engineered. The product's defining proposition—accurate automatic counting inside all four *other companies’ apps*—has not been verified. Treat that as an explicit P0 feasibility gate and do not promise a Play launch until it passes technical testing and the required platform review.
