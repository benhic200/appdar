# Walkthrough Fix, Improvements & Settings Additions — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the broken onboarding walkthrough (stuck on WELCOME forever), improve its steps/copy/colours, and add Rate/Feedback/Share buttons to Settings.

**Architecture:** Three isolated bugs in the existing tap-target-compose walkthrough are fixed first (OSM reset loop, missing `onWalkthroughNext()` call, stale coordinator state). Then the `WalkthroughStep` enum is extended with one new step and all messages/colours are updated. Finally three self-contained intent buttons are added to the Settings About card.

**Tech Stack:** Kotlin, Jetpack Compose, tap-target-compose 1.2.1, SharedPreferences, Android Intents

---

## File Map

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/benhic/appdar/Walkthrough.kt` | Add `DASHBOARD_APPS_CARD` enum value; update `message()`, `precedence()`, `style()` (purple) |
| `app/src/main/kotlin/com/benhic/appdar/MainActivity.kt` | Fix OSM `first{}` bug; update `advanceWalkthroughStep()` chain; add Toast on COMPLETE |
| `app/src/main/kotlin/com/benhic/appdar/DashboardTab.kt` | Add `DASHBOARD_APPS_CARD` target; fix `onComplete`; add `key()` wrapper |
| `app/src/main/kotlin/com/benhic/appdar/AddBusinessScreen.kt` | Fix `onComplete`; add `key()` wrapper |
| `feature-settings/src/main/kotlin/com/benhic/appdar/feature/settings/SettingsScreen.kt` | Add Rate/Feedback/Share buttons to About card |

---

## Task 1: Update `Walkthrough.kt` — new step + purple highlights

**Files:**
- Modify: `app/src/main/kotlin/com/benhic/appdar/Walkthrough.kt`

- [ ] **Step 1.1 — Replace the entire file contents**

Replace `Walkthrough.kt` with the following. Key changes: adds `DASHBOARD_APPS_CARD`, changes highlight colour to purple, shortens all messages.

```kotlin
package com.benhic.appdar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.psoffritti.taptargetcompose.TapTargetStyle
import androidx.compose.material3.MaterialTheme

enum class WalkthroughStep {
    WELCOME,
    DASHBOARD_APPS_CARD,
    DASHBOARD_HIDE_UNINSTALLED,
    WIDGET_EXPLANATION,
    PLACES_HIDE_UNINSTALLED,
    COMPLETE
}

data class WalkthroughState(
    val currentStep: WalkthroughStep = WalkthroughStep.WELCOME,
    val targetScreen: String = "",
    val targetAnchorId: String? = null
) {
    val isComplete: Boolean get() = currentStep == WalkthroughStep.COMPLETE
}

object WalkthroughTarget {
    private val appdarPurple = Color(0xFF7B2FBE)

    fun message(step: WalkthroughStep): String = when (step) {
        WalkthroughStep.WELCOME                   -> "Welcome to Appdar! Here's what's near you."
        WalkthroughStep.DASHBOARD_APPS_CARD       -> "These are local apps. Tap any to open it."
        WalkthroughStep.DASHBOARD_HIDE_UNINSTALLED -> "Tap Hide to remove apps you don't have installed."
        WalkthroughStep.WIDGET_EXPLANATION        -> "Add a widget — it updates as you move."
        WalkthroughStep.PLACES_HIDE_UNINSTALLED   -> "Bulk-manage apps per location here."
        WalkthroughStep.COMPLETE                  -> ""
    }

    fun precedence(step: WalkthroughStep): Int = when (step) {
        WalkthroughStep.WELCOME                   -> 100
        WalkthroughStep.DASHBOARD_APPS_CARD       -> 200
        WalkthroughStep.DASHBOARD_HIDE_UNINSTALLED -> 300
        WalkthroughStep.WIDGET_EXPLANATION        -> 400
        WalkthroughStep.PLACES_HIDE_UNINSTALLED   -> 500
        WalkthroughStep.COMPLETE                  -> 999
    }

    @Composable
    fun style(step: WalkthroughStep): TapTargetStyle = TapTargetStyle(
        backgroundColor = Color.Black.copy(alpha = 0.85f),
        tapTargetHighlightColor = appdarPurple,
        backgroundAlpha = 0.85f
    )
}
```

- [ ] **Step 1.2 — Build to confirm no compile errors**

```bash
cd /Host_Machine/root/.openclaw/Adroid_Dev/nearby-apps-widget/phase1
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` (or only the pre-existing deprecation warnings).

- [ ] **Step 1.3 — Commit**

```bash
git add app/src/main/kotlin/com/benhic/appdar/Walkthrough.kt
git commit -m "feat(walkthrough): add DASHBOARD_APPS_CARD step, purple highlights, shorter copy"
```

---

## Task 2: Fix `MainActivity.kt` — OSM reset bug + advance chain

**Files:**
- Modify: `app/src/main/kotlin/com/benhic/appdar/MainActivity.kt`

- [ ] **Step 2.1 — Add `kotlinx.coroutines.flow.first` import if missing**

Check line ~83 imports. Add if not present:

```kotlin
import kotlinx.coroutines.flow.first
```

- [ ] **Step 2.2 — Fix the OSM LaunchedEffect (the reset loop)**

Find this block (around line 248–261):

```kotlin
LaunchedEffect(onboardingComplete, walkthroughCompleted) {
    Log.d(TAG, "LaunchedEffect: onboardingComplete=$onboardingComplete, walkthroughCompleted=$walkthroughCompleted")
    if (onboardingComplete && !walkthroughCompleted) {
        Log.d(TAG, "Waiting for OSM download completion...")
        nearbyBranchFinder.fetchState.collect { fetchState ->
            Log.d(TAG, "fetchState: isLoading=${fetchState.isLoading}, branches.size=${fetchState.branches.size}, isOffline=${fetchState.isOffline}, status=${fetchState.statusMessage}")
            if (!fetchState.isLoading) {
                Log.d(TAG, "OSM download finished (isLoading=false), resetting walkthrough state. branches.size=${fetchState.branches.size}, isOffline=${fetchState.isOffline}")
                // Download complete (or failed), start walkthrough
                // Reset walkthrough state to first step
                _walkthroughState.value = WalkthroughState()
            }
        }
    }
}
```

Replace with:

```kotlin
LaunchedEffect(onboardingComplete, walkthroughCompleted) {
    if (onboardingComplete && !walkthroughCompleted) {
        nearbyBranchFinder.fetchState.first { !it.isLoading }
        _walkthroughState.value = WalkthroughState()
    }
}
```

This fires exactly once after OSM finishes and never resets the state again.

- [ ] **Step 2.3 — Update `advanceWalkthroughStep()` — new chain + Toast on complete**

Find `advanceWalkthroughStep()` (around line 155–180). Replace the entire function:

```kotlin
private fun advanceWalkthroughStep() {
    val current = _walkthroughState.value.currentStep
    val nextStep = when (current) {
        WalkthroughStep.WELCOME                    -> WalkthroughStep.DASHBOARD_APPS_CARD
        WalkthroughStep.DASHBOARD_APPS_CARD        -> WalkthroughStep.DASHBOARD_HIDE_UNINSTALLED
        WalkthroughStep.DASHBOARD_HIDE_UNINSTALLED -> WalkthroughStep.WIDGET_EXPLANATION
        WalkthroughStep.WIDGET_EXPLANATION         -> WalkthroughStep.PLACES_HIDE_UNINSTALLED
        WalkthroughStep.PLACES_HIDE_UNINSTALLED    -> WalkthroughStep.COMPLETE
        WalkthroughStep.COMPLETE                   -> WalkthroughStep.COMPLETE
    }
    when (nextStep) {
        WalkthroughStep.PLACES_HIDE_UNINSTALLED -> _currentScreen.value = "businesses"
        WalkthroughStep.WIDGET_EXPLANATION      -> _currentScreen.value = "dashboard"
        else -> {}
    }
    if (nextStep == WalkthroughStep.COMPLETE) {
        val prefs = getSharedPreferences("appdar_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("walkthrough_completed", true).apply()
        _walkthroughCompleted.value = true
        Toast.makeText(this, "Tour done! Restart anytime in Settings.", Toast.LENGTH_LONG).show()
    }
    _walkthroughState.value = _walkthroughState.value.copy(currentStep = nextStep)
}
```

- [ ] **Step 2.4 — Build**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2.5 — Commit**

```bash
git add app/src/main/kotlin/com/benhic/appdar/MainActivity.kt
git commit -m "fix(walkthrough): use first{} to stop OSM reset loop; update advance chain with DASHBOARD_APPS_CARD"
```

---

## Task 3: Fix `DashboardTab.kt` — onComplete, key(), new DASHBOARD_APPS_CARD target

**Files:**
- Modify: `app/src/main/kotlin/com/benhic/appdar/DashboardTab.kt`

- [ ] **Step 3.1 — Update `showTapTargets` set to include new step**

Find (around line 413):

```kotlin
val showTapTargets = !walkthroughCompleted && walkthroughState.currentStep in setOf(
    WalkthroughStep.WELCOME,
    WalkthroughStep.DASHBOARD_HIDE_UNINSTALLED,
    WalkthroughStep.WIDGET_EXPLANATION
)
```

Replace with:

```kotlin
val showTapTargets = !walkthroughCompleted && walkthroughState.currentStep in setOf(
    WalkthroughStep.WELCOME,
    WalkthroughStep.DASHBOARD_APPS_CARD,
    WalkthroughStep.DASHBOARD_HIDE_UNINSTALLED,
    WalkthroughStep.WIDGET_EXPLANATION
)
```

- [ ] **Step 3.2 — Wrap TapTargetCoordinator in `key()` and fix `onComplete`**

Find the `TapTargetCoordinator(` call and the line above it (around line 422). The current code looks like:

```kotlin
TapTargetCoordinator(
    showTapTargets = showTapTargets,
    onComplete = { Log.d(TAG, "TapTargetCoordinator completed (dismissed)") }
) {
```

Replace with:

```kotlin
key(walkthroughState.currentStep) {
TapTargetCoordinator(
    showTapTargets = showTapTargets,
    onComplete = { onWalkthroughNext() }
) {
```

And close the `key` block at the end of the `TapTargetCoordinator` lambda (after the closing `}` of `TapTargetCoordinator`), so it becomes:

```kotlin
key(walkthroughState.currentStep) {
    TapTargetCoordinator(
        showTapTargets = showTapTargets,
        onComplete = { onWalkthroughNext() }
    ) {
        // ... existing content ...
    }
}
```

- [ ] **Step 3.3 — Update `centeredTapTargetModifier` to include `DASHBOARD_APPS_CARD`**

Find (around line 449):

```kotlin
val centeredTapTargetModifier = if (showTapTargets && currentStep in setOf(
        WalkthroughStep.WELCOME,
        WalkthroughStep.WIDGET_EXPLANATION
    )) {
```

Replace with:

```kotlin
val centeredTapTargetModifier = if (showTapTargets && currentStep in setOf(
        WalkthroughStep.WELCOME,
        WalkthroughStep.DASHBOARD_APPS_CARD,
        WalkthroughStep.WIDGET_EXPLANATION
    )) {
```

- [ ] **Step 3.4 — Remove all Log.d debug statements from DashboardContent**

Remove these noisy debug logs that are no longer needed (they're inside `DashboardContent`):

```kotlin
Log.d(TAG, "showTapTargets=$showTapTargets, walkthroughCompleted=$walkthroughCompleted, currentStep=${walkthroughState.currentStep}")
Log.d(TAG, "About to call TapTargetCoordinator, showTapTargets=$showTapTargets, currentStep=$currentStep")
Log.d(TAG, "TapTargetCoordinator lambda entered, showTapTargets=$showTapTargets")
Log.d(TAG, "DEBUG creating tapTarget modifier for step: $currentStep")
Log.d(TAG, "DEBUG centeredTapTargetModifier computed")
Log.d(TAG, "DEBUG centeredTapTargetModifier computed: true, currentStep=$currentStep")
Log.d(TAG, "Adding centered tap target overlay")
```

Delete those lines. Keep none of the walkthrough debug logs in DashboardContent.

- [ ] **Step 3.5 — Build**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3.6 — Commit**

```bash
git add app/src/main/kotlin/com/benhic/appdar/DashboardTab.kt
git commit -m "fix(walkthrough): wire onComplete->onWalkthroughNext, add key() wrapper, add DASHBOARD_APPS_CARD target"
```

---

## Task 4: Fix `AddBusinessScreen.kt` — onComplete + key()

**Files:**
- Modify: `app/src/main/kotlin/com/benhic/appdar/AddBusinessScreen.kt`

- [ ] **Step 4.1 — Wrap TapTargetCoordinator in `key()` and fix `onComplete`**

Find (around line 290):

```kotlin
    TapTargetCoordinator(
    showTapTargets = showTapTargets,
    onComplete = { /* coordinator dismissed, nothing to do */ }
) {
```

Replace with:

```kotlin
key(walkthroughState.currentStep) {
    TapTargetCoordinator(
        showTapTargets = showTapTargets,
        onComplete = { onWalkthroughNext() }
    ) {
```

And add the closing `}` for the `key` block after `TapTargetCoordinator`'s closing `}`.

- [ ] **Step 4.2 — Add missing `key` import**

At the top of the file, ensure this import exists (it's part of `androidx.compose.runtime`):

```kotlin
import androidx.compose.runtime.key
```

- [ ] **Step 4.3 — Remove walkthrough debug logs from `AddBusinessScreen`**

Remove:

```kotlin
Log.d(TAG, "showTapTargets=$showTapTargets, walkthroughCompleted=$walkthroughCompleted, currentStep=${walkthroughState.currentStep}")
```

- [ ] **Step 4.4 — Build**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4.5 — Commit**

```bash
git add app/src/main/kotlin/com/benhic/appdar/AddBusinessScreen.kt
git commit -m "fix(walkthrough): wire AddBusinessScreen onComplete->onWalkthroughNext, add key() wrapper"
```

---

## Task 5: Add Rate / Feedback / Share buttons to Settings

**Files:**
- Modify: `feature-settings/src/main/kotlin/com/benhic/appdar/feature/settings/SettingsScreen.kt`

- [ ] **Step 5.1 — Add import for `Intent.ACTION_SEND` and `Intent.ACTION_SENDTO`**

These are already available via `android.content.Intent` which is imported. Confirm the import exists near the top of `SettingsScreen.kt`:

```kotlin
import android.content.Intent
import android.net.Uri
```

Both should already be present (the bug-report button uses them). No new imports needed.

- [ ] **Step 5.2 — Add the three buttons after "Restart onboarding walkthrough"**

Find (around line 548–554):

```kotlin
            OutlinedButton(
                onClick = onRestartWalkthrough,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restart onboarding walkthrough")
            }
        }
    }
```

Replace with:

```kotlin
            OutlinedButton(
                onClick = onRestartWalkthrough,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restart onboarding walkthrough")
            }
            Spacer(modifier = Modifier.padding(4.dp))
            OutlinedButton(
                onClick = {
                    val marketUri = Uri.parse("market://details?id=com.benhic.appdar")
                    val webUri = Uri.parse("https://play.google.com/store/apps/details?id=com.benhic.appdar")
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, marketUri)
                                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        )
                    } catch (e: android.content.ActivityNotFoundException) {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, webUri)
                                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rate Appdar ★")
            }
            Spacer(modifier = Modifier.padding(4.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("ben.hickie@gmail.com"))
                        putExtra(Intent.EXTRA_SUBJECT, "Appdar Feedback")
                        putExtra(Intent.EXTRA_TEXT, "Hi Ben,\n\nHere's my feedback on Appdar:\n\n")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send Feedback")
            }
            Spacer(modifier = Modifier.padding(4.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Check out Appdar — nearby apps on your home screen!\nhttps://play.google.com/store/apps/details?id=com.benhic.appdar"
                        )
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Appdar"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Share Appdar")
            }
        }
    }
```

- [ ] **Step 5.3 — Build**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5.4 — Commit**

```bash
git add feature-settings/src/main/kotlin/com/benhic/appdar/feature/settings/SettingsScreen.kt
git commit -m "feat(settings): add Rate Appdar, Send Feedback, and Share Appdar buttons"
```

---

## Task 6: Full build + APK

- [ ] **Step 6.1 — Full debug build**

```bash
./gradlew assembleDebug 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`. Note the APK path in output.

- [ ] **Step 6.2 — Verify walkthrough end-to-end (manual)**

Install the APK. Clear `appdar_prefs` (uninstall/reinstall or clear app data). Open the app. After OSM loads:

1. WELCOME tap target appears (purple highlight, centered) → tap → advances
2. DASHBOARD_APPS_CARD appears (purple, centered) → tap → advances
3. DASHBOARD_HIDE_UNINSTALLED appears on the Hide button → tap → advances
4. WIDGET_EXPLANATION appears (purple, centered) → tap → advances
5. App navigates to Places tab, PLACES_HIDE_UNINSTALLED appears on hide toggle → tap → advances
6. Toast "Tour done! Restart anytime in Settings." appears
7. Open Settings → About card — three new buttons visible: Rate Appdar ★, Send Feedback, Share Appdar
8. Tap "Restart onboarding walkthrough" → tour restarts at step 1

- [ ] **Step 6.3 — Final commit**

```bash
git add -A
git commit -m "chore: full walkthrough fix + improvements + settings additions (v+1)"
```

---

## Self-Review

**Spec coverage:**
- ✅ Bug 1 (OSM reset): Task 2, Step 2.2
- ✅ Bug 2 (onComplete not wired): Tasks 3 & 4
- ✅ Bug 3 (stale coordinator): Tasks 3 & 4 (`key()` wrapper)
- ✅ New `DASHBOARD_APPS_CARD` step: Tasks 1, 2, 3
- ✅ Purple highlights both themes: Task 1 (`TapTargetStyle`)
- ✅ Short copy: Task 1 (`WalkthroughTarget.message()`)
- ✅ COMPLETE toast: Task 2
- ✅ Rate App button: Task 5
- ✅ Send Feedback button: Task 5
- ✅ Share App button: Task 5

**Type consistency:**
- `WalkthroughStep.DASHBOARD_APPS_CARD` defined in Task 1, used in Tasks 2, 3 ✅
- `advanceWalkthroughStep()` chain matches enum order ✅
- `onWalkthroughNext` callback name matches existing signatures in `DashboardContent` and `AddBusinessScreen` ✅
- `key(walkthroughState.currentStep)` — `walkthroughState` is already in scope in both composables ✅

**No placeholders:** All code blocks are complete. ✅
