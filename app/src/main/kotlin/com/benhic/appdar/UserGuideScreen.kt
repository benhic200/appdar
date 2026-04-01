package com.benhic.appdar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun UserGuideScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Appdar Guide",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Everything you need to know, tap a section to expand.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        // ── What is Appdar? ────────────────────────────────────────────────
        GuideSection(title = "What is Appdar?", defaultExpanded = true) {
            GuideText(
                "Appdar shows you the right apps for wherever you are. " +
                "Walk past a Costa Coffee and the Costa app appears in the widget. " +
                "Arrive at work and your work apps appear. Leave the gym and the gym apps disappear — " +
                "automatically, without you doing anything."
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuideText(
                "Everything runs on-device. Your location is never sent to any server — " +
                "all distance calculations happen locally using your GPS."
            )
        }

        // ── Getting Started ────────────────────────────────────────────────
        GuideSection(title = "Getting Started") {
            GuideBullet("Grant location permission — tap Setup in the drawer and follow the steps.")
            GuideBullet("On first launch, Appdar automatically downloads branch location data for your region (UK or US). A progress bar shows how the download is going — this only happens once.")
            GuideBullet("Add the widget to your home screen — long-press home screen → Widgets → Appdar → drag to place.")
            GuideBullet("Open the Dashboard to see nearby apps right away — no widget needed.")
            Spacer(modifier = Modifier.height(8.dp))
            GuideText("That's it. Appdar will start showing the closest places from the moment location is granted.")
        }

        // ── Dashboard ─────────────────────────────────────────────────────
        GuideSection(title = "Dashboard") {
            GuideText(
                "The Dashboard is the main in-app view. It shows the same list the widget shows, " +
                "refreshed live every time you open it."
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuideBullet("If you're at a saved profile location (Home, Work, Gym etc.) it shows those profile apps with a banner at the top.")
            GuideBullet("Otherwise it shows the nearest places from the business database, sorted by distance.")
            GuideBullet("Distances update every 10 seconds automatically while the tab is open.")
            GuideBullet("Green dot = app is installed. Grey dot = not installed — tap to open the Play Store listing.")
            GuideBullet("Tap the refresh icon in the top bar to reload with your latest location.")
        }

        // ── The Widget ─────────────────────────────────────────────────────
        GuideSection(title = "The Widget") {
            GuideText("Appdar has several widget sizes — Android picks the right layout based on how large you make it.")
            Spacer(modifier = Modifier.height(8.dp))
            GuideSubheading("Widget sizes")
            GuideSettingRow("Appdar (list)",
                "The default widget. Resize it wider for 2 or 3 columns. " +
                "Shows names, distances, status dots and app icons. " +
                "Use the ‹ › arrows to page through results if there are more than fit on screen.")
            GuideSettingRow("Nearby — Single Place",
                "A compact 1×1 or 2×1 widget showing just the single closest place. " +
                "At very small sizes (2×1) it uses a side-by-side layout with the icon on the left.")
            GuideSettingRow("Nearby — Quick Strip",
                "A thin horizontal strip (4×1). Shows 2–4 places side by side — icon and name only. " +
                "Also works as a vertical strip when placed in a taller column. " +
                "Tap the small refresh button (top-right) to update.")
            GuideSettingRow("Nearby — Grid",
                "A wider grid layout showing icon tiles with no text — " +
                "fits more places in less vertical space.")
            Spacer(modifier = Modifier.height(8.dp))
            GuideSubheading("Controls on every widget")
            GuideBullet("Refresh button — tap to pull fresh location data immediately.")
            GuideBullet("Settings gear — opens the app.")
            GuideBullet("‹ › page arrows — browse pages when results don't all fit.")
            GuideBullet("Tap an app tile — launches the app, or opens the Play Store if not installed.")
            Spacer(modifier = Modifier.height(8.dp))
            GuideText("Widget theme can be set independently of the app — go to Settings → Widget Theme.")
        }

        // ── Nearby Apps ───────────────────────────────────────────────────
        GuideSection(title = "Nearby Apps") {
            GuideText(
                "The Nearby Apps screen (and the widget when you're not at a saved profile location) " +
                "shows all businesses from your Places list sorted by distance from where you are now."
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuideBullet("Distances are calculated from your live GPS position.")
            GuideBullet("Branch locations come from a local database downloaded from OpenStreetMap on first launch. No live network lookup is needed after that.")
            GuideBullet("The local database covers UK and US branches and is automatically refreshed every 30 days.")
            GuideBullet("Distances update every 10 seconds automatically while the tab is open.")
            GuideBullet("Opening the Nearby Apps tab always refreshes your position — the same as tapping the refresh button.")
            GuideBullet("Green dot = app installed. Grey dot = not installed. Tap to open the Play Store.")
            GuideBullet("Tap the refresh button to force an update at any time.")
        }

        // ── Places ────────────────────────────────────────────────────────
        GuideSection(title = "Places") {
            GuideText(
                "The Places screen is where you manage which businesses appear in the widget and Dashboard."
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuideSubheading("Built-in businesses")
            GuideText(
                "Appdar comes pre-loaded with a UK & Ireland and US database covering supermarkets, fast food, " +
                "coffee shops, hotels, pharmacies and more — including Tesco, Sainsbury's, Morrisons, " +
                "Aldi, Lidl, Waitrose, M&S, Co-op, Iceland, Dunnes Stores, SuperValu, Centra, " +
                "Costa, Starbucks, Caffè Nero, Pret, Greggs, " +
                "McDonald's, Burger King, KFC, Nando's, Subway, Five Guys, Domino's, Pizza Hut, " +
                "Boots, WHSmith, Wetherspoons, Premier Inn, Travelodge, Hilton, Marriott, " +
                "Walmart, Target, Costco, Walgreens, CVS, Dunkin', Chick-fil-A, Taco Bell, Chipotle and more."
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuideBullet("Toggle the switch next to any built-in business to hide it from the widget and Dashboard.")
            GuideBullet("Toggled-off businesses stay in the list — you can re-enable them any time.")
            Spacer(modifier = Modifier.height(8.dp))
            GuideSubheading("Adding a custom place")
            GuideBullet("Tap \"Add Place\" at the top of the Places screen.")
            GuideBullet("Enter the place name and an optional category.")
            GuideBullet("Select the app associated with that place from your installed apps.")
            GuideBullet("Toggle \"Use current location\" to save your GPS position as the activation point.")
            GuideBullet("Set a detection radius — how close you need to be for it to appear (default 200 m).")
            Spacer(modifier = Modifier.height(8.dp))
            GuideText("Custom places show a delete button instead of a toggle — tap the bin icon to remove them.")
        }

        // ── Location Profiles (Free) ───────────────────────────────────────
        GuideSection(title = "Home Apps (Free)") {
            GuideText(
                "Home Apps is a free location profile. When you're within 300 m of your saved home location, " +
                "the widget and Dashboard switch from the nearby businesses list to your chosen home apps."
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuideSubheading("How to set it up")
            GuideBullet("Step 1 — Go home, open Home Apps in the drawer, and tap \"Set to Current Location\".")
            GuideBullet("Step 2 — Tap \"Choose Apps\" and tick the apps you want to see when you're at home.")
            Spacer(modifier = Modifier.height(8.dp))
            GuideText(
                "The step badges on the screen turn filled once each step is complete. " +
                "A green confirmation card appears at the bottom once both steps are done."
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuideText(
                "To change your home location later, go back to Home Apps and tap \"Update Location\". " +
                "Tap \"Clear\" to remove the location entirely."
            )
        }

        // ── Appdar Pro ────────────────────────────────────────────────────
        GuideSection(title = "Appdar Pro") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(Icons.Filled.Star, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    "One-time purchase — £1.67",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            GuideText(
                "Pro unlocks three additional location profiles — Work, Gym, Custom Location 1 and Custom Location 2. " +
                "Each works exactly like Home Apps: set a location, pick your apps, and Appdar switches to them automatically."
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuideSettingRow("Work Apps",
                "Save your workplace location and pin your work apps there — calendar, email, Slack, Teams, etc.")
            GuideSettingRow("Gym Apps",
                "Save your gym and add your fitness apps — they appear the moment you walk in.")
            GuideSettingRow("Custom Location 1 & 2",
                "Any two places you choose — a coffee shop you visit regularly, a relative's house, a co-working space.")
            Spacer(modifier = Modifier.height(8.dp))
            GuideSubheading("How to upgrade")
            GuideBullet("Tap any locked Pro item in the drawer (Work, Gym, Custom 1, Custom 2).")
            GuideBullet("Or tap the \"Unlock Appdar Pro\" bar at the bottom of the drawer.")
            GuideBullet("Review the features on the upgrade screen, then tap \"Unlock Pro — £1.67\".")
            GuideBullet("The Google Play purchase sheet will appear. Complete the payment there.")
            Spacer(modifier = Modifier.height(8.dp))
            GuideSubheading("Restoring a purchase")
            GuideText(
                "If you change phone or reinstall the app, go to Setup or Settings and tap " +
                "\"Restore Pro Purchase\". Appdar will check your Google Play account and " +
                "restore Pro automatically if it finds an existing purchase."
            )
        }

        // ── Permissions Explained ─────────────────────────────────────────
        GuideSection(title = "Permissions Explained") {
            GuideText("Appdar needs the following permissions to work correctly:")
            Spacer(modifier = Modifier.height(8.dp))
            GuidePermission(
                name = "Fine Location — Allow all the time",
                why = "Required to calculate accurate distances and activate location profiles in the background. " +
                      "Must be set to \"Allow all the time\" (not just \"While using\") so the widget updates " +
                      "when the screen is off."
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuidePermission(
                name = "Background Location (Android 10+)",
                why = "Android 10 and above separates background location from foreground location. " +
                      "Without it the widget can only refresh when you have the app open."
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuidePermission(
                name = "Notifications (Android 13+)",
                why = "Android 13 requires explicit permission to show notifications. " +
                      "Appdar uses a brief notification during the initial branch data download. " +
                      "Grant it in App Settings → Notifications → Appdar."
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuidePermission(
                name = "Battery Optimisation — All devices",
                why = "Android may pause background apps to save battery. Disable battery optimisation for Appdar " +
                      "in Settings → Apps → Appdar → Battery → Unrestricted."
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuidePermission(
                name = "Autostart — Xiaomi / MIUI",
                why = "MIUI aggressively kills background apps. Enable Autostart: " +
                      "Settings → Apps → Appdar → Autostart → ON."
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuidePermission(
                name = "Never sleeping apps — Samsung / One UI",
                why = "Samsung's Device Care can restrict background apps. Add Appdar to the never-sleeping list: " +
                      "Settings → Device Care → Battery → Background usage limits → Never sleeping apps → Add Appdar."
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuidePermission(
                name = "App Launch — Huawei / EMUI",
                why = "EMUI restricts background launches by default. Go to Settings → Apps → Appdar → App Launch " +
                      "→ Manage manually → enable Auto-launch, Secondary launch, and Run in background."
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuidePermission(
                name = "Battery optimisation — OnePlus / OxygenOS",
                why = "Go to Settings → Battery → Battery optimisation → All apps → Appdar → Don't optimise."
            )
            Spacer(modifier = Modifier.height(8.dp))
            GuidePermission(
                name = "Startup manager — Oppo / Realme / ColorOS",
                why = "Go to Phone Manager → Privacy Permissions → Startup manager → enable Appdar."
            )
        }

        // ── Settings Reference ─────────────────────────────────────────────
        GuideSection(title = "Settings Reference") {
            GuideSettingRow("Detection Radius",
                "How far out Appdar looks for nearby businesses (500 m – 10 km, default 5 km). " +
                "Businesses further than this are hidden from the widget and Dashboard.")
            GuideSettingRow("Distance Unit",
                "Choose how distances are displayed: kilometres or miles.")
            GuideSettingRow("Widget Background Refresh",
                "How often the home screen widget automatically updates (1–60 min, default 5 min). " +
                "Lower intervals keep the widget more up to date but use slightly more battery. " +
                "The in-app Dashboard and Nearby Apps tabs always refresh live while open — this setting only affects the widget. " +
                "Overridden by Low Power Mode.")
            GuideSettingRow("Low Power Mode",
                "Disables automatic widget background refresh entirely. " +
                "The widget only updates when you tap the refresh button manually.")
            GuideSettingRow("Branch Data",
                "Branch locations are downloaded once on first launch and refreshed every 30 days. " +
                "Use \"Force Re-download Branch Data\" if you want to trigger a fresh download immediately.")
            GuideSettingRow("App Theme",
                "Light / Dark / System — controls the app's own colours.")
            GuideSettingRow("Widget Theme",
                "Light / Dark / System — controls the widget's background colour independently of the app. " +
                "Set to Dark if your launcher uses a dark wallpaper.")
            GuideSettingRow("Restore Pro Purchase",
                "Found in Setup and Settings. Checks your Google Play account and re-activates Pro " +
                "after a reinstall or phone change.")
        }

        // ── Troubleshooting ────────────────────────────────────────────────
        GuideSection(title = "Troubleshooting") {
            GuideSubheading("First-launch download")
            GuideBullet("A progress bar (e.g. 1/5, 2/5…) appears on the Dashboard during the initial branch data download. This is normal — it only happens once.")
            GuideBullet("If the download fails (no internet, or the server is busy), Appdar will show an offline state. Open the Dashboard or Nearby Apps tab when you have a connection to retry.")
            GuideBullet("Branch data is refreshed automatically every 30 days in the background.")
            Spacer(modifier = Modifier.height(8.dp))
            GuideSubheading("Widget problems")
            GuideBullet("Widget shows \"Can't load widget\" — remove it and re-add it after updating the app.")
            GuideBullet("Widget shows stale data — tap the refresh button; also check battery optimisation is disabled for Appdar.")
            GuideBullet("Widget is blank — open the Dashboard to trigger the initial branch data download, then return to the widget.")
            GuideBullet("Tapping a widget item does nothing — on some launchers (MIUI) try the refresh button first; the widget may need one refresh before taps work.")
            Spacer(modifier = Modifier.height(8.dp))
            GuideSubheading("Location & profiles")
            GuideBullet("No businesses shown — confirm location permission is \"Allow all the time\" and the initial download has completed (open the Dashboard to check).")
            GuideBullet("Profile not activating — check the profile location is saved and you're within 300 m of it. Use the Dashboard to see which profile (if any) is currently active.")
            GuideBullet("Profile activating too far away — 300 m is fixed. If GPS accuracy is poor the effective range may appear larger. Moving to an open area improves accuracy.")
            Spacer(modifier = Modifier.height(8.dp))
            GuideSubheading("Apps & icons")
            GuideBullet("App icon not showing — the app may not be installed. Tap the tile to open its Play Store listing.")
            GuideBullet("Wrong app shown for a business — go to Places and toggle off that business, then add a custom entry with the correct app.")
            Spacer(modifier = Modifier.height(8.dp))
            GuideSubheading("Pro / purchase")
            GuideBullet("\"Purchase unavailable\" error — the app must be installed from the Play Store (not sideloaded). Make sure you're signed in to Google Play.")
            GuideBullet("Pro not restored after reinstall — go to Setup or Settings → Restore Pro Purchase.")
            Spacer(modifier = Modifier.height(8.dp))
            GuideSubheading("Other")
            GuideBullet("Widget still labelled \"Nearby Apps\" in the picker — restart your launcher or reboot the device to clear its widget cache.")
            GuideBullet("Re-run the setup guide any time from the drawer → Setup.")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Section container ──────────────────────────────────────────────────────

@Composable
private fun GuideSection(
    title: String,
    defaultExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    content = content
                )
            }
        }
    }
}

// ── Text helpers ───────────────────────────────────────────────────────────

@Composable
private fun GuideText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun GuideSubheading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun GuideBullet(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("•", modifier = Modifier.width(12.dp), style = MaterialTheme.typography.bodyMedium)
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GuidePermission(name: String, why: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = why,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GuideSettingRow(setting: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(setting, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
