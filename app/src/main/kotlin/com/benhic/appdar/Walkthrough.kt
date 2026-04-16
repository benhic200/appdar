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
