# Walkthrough Fix, Improvements & Settings Additions — Design Spec

**Date:** 2026-04-17  
**Status:** Approved

---

## Overview

Three problems to solve in one pass:

1. **Fix the broken walkthrough** — the tap-target-compose tour is stuck on WELCOME forever due to two runtime bugs.
2. **Improve the walkthrough** — better steps, tighter copy, purple highlights on both themes, one new target.
3. **Add three Settings buttons** — Rate App, Send Feedback, Share App.

---

## Part 1 — Walkthrough Bug Fixes

### Bug 1: OSM fetch resets walkthrough every refresh

**Location:** `app/src/main/kotlin/com/benhic/appdar/MainActivity.kt` around line 248.

The `LaunchedEffect` collects from `fetchState` indefinitely. Every time the background refresh completes (`isLoading → false`) it resets `_walkthroughState.value = WalkthroughState()`, sending the user back to WELCOME mid-tour.

**Fix:** Replace `collect { }` with `first { !it.isLoading }`. Fires once, stops collecting.

### Bug 2: Step never advances — `onComplete` just logs

**Location:** `DashboardTab.kt` and `AddBusinessScreen.kt`.

`TapTargetCoordinator(onComplete = { Log.d(...) })` never calls `advanceWalkthroughStep()`. The user taps the highlighted target, the library fires `onComplete`, nothing happens.

**Fix:** Change to `onComplete = { onWalkthroughNext() }` in both files.

### Bug 3: Coordinator state stale between steps

`TapTargetCoordinator` is a stateful composable. When `showTapTargets` stays `true` across a step change (WELCOME → DASHBOARD_APPS_CARD, both shown on Dashboard), the coordinator thinks it already finished and won't re-trigger.

**Fix:** Wrap each `TapTargetCoordinator` in `key(walkthroughState.currentStep) { ... }`. Forces a fresh coordinator instance per step.

---

## Part 2 — Walkthrough Improvements

### New Step Sequence

| # | Enum Value | Screen | Target Element | Message |
|---|-----------|--------|---------------|---------|
| 1 | `WELCOME` | Dashboard | Center overlay | "Welcome to Appdar! Here's what's near you." |
| 2 | `DASHBOARD_APPS_CARD` | Dashboard | Apps list/card area | "These are local apps. Tap any to open it." |
| 3 | `DASHBOARD_HIDE_UNINSTALLED` | Dashboard | Hide button | "Tap Hide to remove apps you don't have installed." |
| 4 | `WIDGET_EXPLANATION` | Dashboard | Center overlay | "Add a widget — it updates as you move." |
| 5 | `PLACES_HIDE_UNINSTALLED` | Places tab | Hide toggle row | "Bulk-manage apps per location here." |
| 6 | `COMPLETE` | — | — | Snackbar: "Tour done! Restart anytime in Settings." |

`advanceWalkthroughStep()` chain: WELCOME → DASHBOARD_APPS_CARD → DASHBOARD_HIDE_UNINSTALLED → WIDGET_EXPLANATION → PLACES_HIDE_UNINSTALLED → COMPLETE.

Screen navigation in `advanceWalkthroughStep()`:
- → PLACES_HIDE_UNINSTALLED: navigate to "businesses"
- → WIDGET_EXPLANATION: navigate back to "dashboard"

### Purple Highlights

Replace yellow `tapTargetHighlightColor` in `WalkthroughTarget.style()`:

```kotlin
val appdarPurple = Color(0xFF7B2FBE)

TapTargetStyle(
    backgroundColor = Color.Black.copy(alpha = 0.85f),
    tapTargetHighlightColor = appdarPurple,
    backgroundAlpha = 0.85f
)
```

Same purple works on both dark and light themes — the dimmed backdrop provides contrast regardless of system theme. No conditional logic needed.

### Per-Step Coordinator Pattern

Each screen uses `key(currentStep)` to get a fresh coordinator per step, with a single `tapTarget` modifier on the relevant element:

```kotlin
key(walkthroughState.currentStep) {
    TapTargetCoordinator(
        showTapTargets = showTapTargets,
        onComplete = { onWalkthroughNext() }
    ) {
        // content with .tapTarget(...) on the relevant element
    }
}
```

---

## Part 3 — Settings Additions

Three new `OutlinedButton`s added to the **About** card in `feature-settings/src/main/kotlin/com/benhic/appdar/feature/settings/SettingsScreen.kt`, after the existing "Restart onboarding walkthrough" button.

| Button Label | Intent |
|-------------|--------|
| Rate Appdar | `Intent(ACTION_VIEW, Uri.parse("market://details?id=com.benhic.appdar"))` with `https://play.google.com/store/apps/details?id=com.benhic.appdar` fallback |
| Send Feedback | `Intent(ACTION_SENDTO, Uri.parse("mailto:ben.hickie@gmail.com?subject=Appdar Feedback"))` |
| Share Appdar | `Intent(ACTION_SEND)` type `"text/plain"` with text `"Check out Appdar — nearby apps on your home screen! https://play.google.com/store/apps/details?id=com.benhic.appdar"` |

No new parameters needed on `SettingsContent` or `SettingsCards` — all three intents are self-contained using `LocalContext.current`.

---

## Files Modified

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/benhic/appdar/Walkthrough.kt` | Add `DASHBOARD_APPS_CARD` to enum; update `message()`, `precedence()`, `style()` (purple) |
| `app/src/main/kotlin/com/benhic/appdar/MainActivity.kt` | Fix OSM `first{}` bug; update `advanceWalkthroughStep()` chain; add Snackbar on COMPLETE |
| `app/src/main/kotlin/com/benhic/appdar/DashboardTab.kt` | Add `DASHBOARD_APPS_CARD` target; fix `onComplete`; add `key()` wrapper |
| `app/src/main/kotlin/com/benhic/appdar/AddBusinessScreen.kt` | Fix `onComplete`; add `key()` wrapper |
| `feature-settings/src/main/kotlin/com/benhic/appdar/feature/settings/SettingsScreen.kt` | Add Rate/Feedback/Share buttons to About card |

No new files. No new dependencies.
