package com.example.nearbyappswidget

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class OnboardingStep(
    val icon: ImageVector,
    val title: String,
    val content: @Composable () -> Unit
)

@Composable
fun OnboardingScreen(
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onComplete: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    val totalSteps = 5

    val steps = listOf(
        OnboardingStep(
            icon = Icons.Filled.Star,
            title = "Welcome to Appdar"
        ) {
            WelcomeStepContent()
        },
        OnboardingStep(
            icon = Icons.Filled.LocationOn,
            title = "Location Access"
        ) {
            LocationStepContent(permissionState, onRequestPermission, onOpenAppSettings)
        },
        OnboardingStep(
            icon = Icons.Filled.Settings,
            title = "Keep Appdar Running"
        ) {
            BatteryStepContent(onOpenAppSettings, onOpenBatterySettings)
        },
        OnboardingStep(
            icon = Icons.Filled.Widgets,
            title = "Add the Widget"
        ) {
            WidgetStepContent()
        },
        OnboardingStep(
            icon = Icons.Filled.Check,
            title = "You're All Set!"
        ) {
            DoneStepContent()
        }
    )

    val current = steps[step]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Step dots
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(totalSteps) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (i == step) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (i <= step) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Animated page content
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "onboarding_step"
        ) { targetStep ->
            val targetCurrent = steps[targetStep]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                // Icon
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = targetCurrent.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = targetCurrent.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                targetCurrent.content()
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }) {
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            val isLast = step == totalSteps - 1
            Button(
                onClick = {
                    if (isLast) onComplete()
                    else step++
                },
                colors = if (isLast) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ) else ButtonDefaults.buttonColors()
            ) {
                Text(if (isLast) "Start Using Appdar" else "Next")
            }
        }
    }
}

@Composable
private fun WelcomeStepContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Appdar shows you the apps for businesses near you — right on your home screen widget.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "In the next few steps we'll set up:",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        SetupItem(icon = Icons.Filled.LocationOn, text = "Location permission for distance detection")
        SetupItem(icon = Icons.Filled.Settings, text = "Background permissions so the widget stays fresh")
        SetupItem(icon = Icons.Filled.Widgets, text = "The home screen widget")
    }
}

@Composable
private fun LocationStepContent(
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Appdar needs your location to calculate distances to nearby businesses and automatically show relevant apps.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(20.dp))

        when (permissionState) {
            PermissionState.GRANTED -> {
                StatusCard(
                    granted = true,
                    title = "Location granted",
                    body = "Fine + background location are active. The widget can now detect your position."
                )
            }
            PermissionState.DENIED -> {
                StatusCard(
                    granted = false,
                    title = "Location not granted",
                    body = "Tap below to open App Settings → Permissions → Location → set to \"Always\"."
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Location Permission")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tip: Android 10+ requires setting location to \"Allow all the time\" (not just \"While using\") for background updates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            PermissionState.UNKNOWN -> {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun BatteryStepContent(
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "On MIUI/Xiaomi devices, apps are aggressively killed in the background. Two settings are needed to keep Appdar running.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Step 1 — Enable Autostart", fontWeight = FontWeight.SemiBold)
                Text(
                    "Settings → Apps → Appdar → Autostart → ON",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open App Settings")
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Step 2 — Disable Battery Optimisation", fontWeight = FontWeight.SemiBold)
                Text(
                    "Battery & Performance → App Battery Saver → Appdar → No restrictions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Battery Settings")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Not on MIUI? You can skip this step — standard Android manages this automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WidgetStepContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "The widget lives on your home screen and shows nearby businesses as clickable app icons.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(20.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberedStep(number = "1", text = "Go to your home screen")
                NumberedStep(number = "2", text = "Long-press on an empty area")
                NumberedStep(number = "3", text = "Tap \"Widgets\"")
                NumberedStep(number = "4", text = "Find \"Appdar\" in the list")
                NumberedStep(number = "5", text = "Drag it to your home screen and resize as needed")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Tap the refresh button on the widget any time to update the list. It also refreshes automatically based on your settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DoneStepContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Appdar is ready to use. Here's a quick reminder of what you can do:",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(20.dp))
        SetupItem(icon = Icons.Filled.LocationOn, text = "Nearby tab — see businesses close to you now")
        SetupItem(icon = Icons.Filled.Home, text = "Location Profiles — pick apps for Home, Work, and custom locations")
        SetupItem(icon = Icons.Filled.Star, text = "My Businesses — add your own places")
        SetupItem(icon = Icons.Filled.Widgets, text = "Widget — live on your home screen")
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Access this guide any time from the drawer → Guide.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ── Small shared composables ───────────────────────────────────────────────

@Composable
private fun SetupItem(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NumberedStep(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatusCard(granted: Boolean, title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (granted) "✅  $title" else "❌  $title",
                fontWeight = FontWeight.SemiBold,
                color = if (granted) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
