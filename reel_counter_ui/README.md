# Reel Counter — Premium Android UI Design Concept

A **self-contained, interactive HTML/CSS/JavaScript design prototype**, representing the Android application's complete primary user journey. It is not an APK, native Kotlin source, live tracking system, or production billing integration.

## Open

Open `index.html` in a desktop or mobile browser. Desktop view includes a screen navigator. Mobile view fills the viewport and uses a bottom navigation bar.

## Screens (12)

1. Welcome
2. Setup, disclosure & consent
3. Today dashboard
4. Analytics and time breakdown
5. Combined and platform-specific goals and reminders
6. Small, medium, large, and custom widget studio
7. Tracking health and individual platform controls
8. Optional floating reminder preview
9. Settings and language selection
10. Privacy, retention, and permissions
11. Pro plan display
12. Empty, error, and unavailable states

## Interactive behaviors

- Navigate with mobile tabs or desktop sidebar.
- Adjust the combined daily limit and per-platform limits.
- Toggle 50%, 75%, and 100% reminder milestones and quiet hours.
- Toggle tracking per platform and change balanced/responsive mode.
- Change widget size, theme and primary statistic.
- Preview monthly/yearly Pro selections, and explore sample English/Urdu headings.
- Clear illustrative sample activity and restore the demo.
- Switch example empty/error modes.

## Core design tokens

- Ink: `#23302B`
- Warm canvas: `#F7F7F2`
- White surfaces: `#FFFFFF`
- Sage: `#D9ECE0`
- Lavender: `#EEE7FA`
- Peach: `#FBE5D9`
- Sky: `#E7EDF9`
- Radius: 14–28px; phone preview 39px
- Generous spacing, large numeric hierarchy, low visual noise

## Production gaps and safeguards

- All displayed counts, statuses, charts, goals, and prices are illustrative.
- There is no real AccessibilityService, UsageStats integration, overlay permission, widget host, native notification, Play Billing, database, or external service.
- English/Urdu control in the prototype demonstrates direction switching and key sample labels, not a completed audited translation. Production requires full Urdu translations and RTL QA.
- Detected and estimated counts must remain separate. Counting is **unproven across all four apps** until tested on actual supported versions.
- Accessibility use and overlay permissions need legal/Play policy evaluation; do not assume approval.
- Premium pricing and recurring benefits must be defined and validated.
- This prototype is a design reference for a native Kotlin + Jetpack Compose Android implementation.

## Output

- `index.html`: standalone prototype.
- `gallery.png`: montage of all 12 views.
- `screens/*.png`: individual screen snapshots.
