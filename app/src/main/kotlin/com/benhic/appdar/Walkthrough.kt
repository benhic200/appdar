package com.benhic.appdar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.psoffritti.taptargetcompose.TapTargetStyle

enum class WalkthroughStep {
    WELCOME,
    DASHBOARD_APPS_CARD,
    DASHBOARD_HIDE_UNINSTALLED,
    WIDGET_EXPLANATION,
    NAV_DASHBOARD,
    NAV_PLACES,
    NAV_HOME,
    PLACES_HIDE_UNINSTALLED,
    PLACES_ADD_BUTTON,
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
        WalkthroughStep.WELCOME                    -> "Welcome to Appdar! Here's what's near you."
        WalkthroughStep.DASHBOARD_APPS_CARD        -> "These are nearby businesses. Tap any to open it."
        WalkthroughStep.DASHBOARD_HIDE_UNINSTALLED -> "Tap Hide to remove apps you don't have installed."
        WalkthroughStep.WIDGET_EXPLANATION         -> "Add a home screen widget — it updates as you move."
        WalkthroughStep.NAV_DASHBOARD              -> "Dashboard — your nearest businesses at a glance."
        WalkthroughStep.NAV_PLACES                 -> "Places — manage which apps show per location."
        WalkthroughStep.NAV_HOME                   -> "Home Apps — pick apps to show when you're at home."
        WalkthroughStep.PLACES_HIDE_UNINSTALLED    -> "Hide apps you don't have installed."
        WalkthroughStep.PLACES_ADD_BUTTON          -> "Tap here to add a new place with custom apps."
        WalkthroughStep.COMPLETE                   -> ""
    }

    fun precedence(step: WalkthroughStep): Int = 0

    @Composable
    fun style(step: WalkthroughStep): TapTargetStyle = TapTargetStyle(
        backgroundColor = Color.Black.copy(alpha = 0.85f),
        tapTargetHighlightColor = appdarPurple,
        backgroundAlpha = 0.85f
    )
}
