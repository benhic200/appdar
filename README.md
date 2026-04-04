# 📍 Appdar

> Your location. Your apps. Automatically.

Appdar is an Android home screen widget that detects where you are using GPS and surfaces the most relevant apps instantly — no searching, no folder diving. Walk into a supermarket and your supermarket app appears. Leave and it's gone.

---

## ✨ Features

- **Location-aware widget** — automatically detects nearby places and shows the right app
- **Supermarket support** — ASDA, Tesco, Sainsbury's, Morrisons, Lidl, Aldi
- **Coffee shop support** — Starbucks, Costa, Greggs
- **Fast food support** — McDonald's, KFC, Burger King
- **Lightweight** — minimal battery and GPS usage
- **No account required**

### 🔜 Coming Soon
- Custom location support — assign your own apps to Home, Work, Gym, or any location you define

---

## 📸 Screenshots

_Coming soon_

---

## 🛠️ Tech Stack

- **Language:** Java
- **Min SDK:** Android 8.0 (API 26)
- **Target SDK:** Android 13 (API 33)
- **Widget:** `AppWidgetProvider` + `RemoteViews`
- **Scrollable list:** `RemoteViewsService` / `RemoteViewsFactory`
- **Location:** `FusedLocationProviderClient` (Google Play Services)

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17+
- A device or emulator running Android 8.0+

### Installation

1. Clone the repo
   ```bash
   git clone https://github.com/yourusername/appdar.git
   ```

2. Open in Android Studio
   ```
   File → Open → select the cloned folder
   ```

3. Sync Gradle and build
   ```
   Build → Make Project
   ```

4. Run on device or emulator
   ```
   Run → Run 'app'
   ```

### Adding the Widget

1. Long press your home screen
2. Select **Widgets**
3. Find **Appdar** and drag it to your home screen
4. Grant location permission when prompted

---

## 📁 Project Structure

```
app/
├── src/main/
│   ├── java/com/yourpackage/appdar/
│   │   ├── AppdarWidget.java          # AppWidgetProvider — main widget logic
│   │   ├── AppListService.java        # RemoteViewsService
│   │   ├── AppListFactory.java        # RemoteViewsFactory — builds list items
│   │   ├── LocationHelper.java        # GPS + place detection logic
│   │   ├── PlaceMapper.java           # Maps detected place to app package
│   │   └── WidgetUpdateReceiver.java  # Handles update broadcasts
│   ├── res/
│   │   ├── layout/
│   │   │   ├── widget_layout.xml      # Root widget layout
│   │   │   └── widget_item.xml        # Individual list item layout
│   │   └── xml/
│   │       └── appdar_widget_info.xml # AppWidget provider metadata
│   └── AndroidManifest.xml
```

---

## ⚙️ How It Works

1. The widget registers a periodic update via `AppWidgetManager`
2. On each update, `LocationHelper` requests the last known GPS fix via `FusedLocationProviderClient`
3. `PlaceMapper` compares coordinates against a database of known place locations (supermarkets, coffee shops, fast food)
4. If a match is found within the configured radius, the relevant app is surfaced in the widget list via `RemoteViewsFactory`
5. Item taps are handled via `setPendingIntentTemplate` + `setFillInIntent` — **not** standard View touch dispatch (this is a RemoteViews widget, standard OnClickListener/OnTouchListener do not apply)

---

## 🐛 Known Issues

| Issue | Status | Notes |
|-------|--------|-------|
| Item taps not firing on MIUI 13 | Investigating | Related to MIUI PendingIntent `FLAG_MUTABLE` handling |
| Widget list blank after device sleep | Investigating | `RemoteViewsService` killed by MIUI battery optimisation |
| Custom launcher scroll behaviour | Known | Some third-party launchers handle `ListView` widget scroll differently |

If you're on **MIUI / Xiaomi**, please whitelist Appdar from battery optimisation:
> Settings → Battery & Performance → choose Appdar → No restrictions

---

## 🤝 Contributing

Contributions are welcome, especially:

- Additional place/brand mappings in `PlaceMapper`
- Custom location support (the next major feature)
- Testing on different launchers and Android skins

### Steps

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/custom-locations`)
3. Commit your changes (`git commit -m 'Add custom location support'`)
4. Push to the branch (`git push origin feature/custom-locations`)
5. Open a Pull Request

---

## 📋 Permissions

| Permission | Reason |
|------------|--------|
| `ACCESS_FINE_LOCATION` | Detect nearby places accurately |
| `ACCESS_COARSE_LOCATION` | Fallback location for lower-power detection |
| `ACCESS_BACKGROUND_LOCATION` | Update widget while screen is off |

---

## 🗺️ Powered by OpenStreetMap

A big thank you to **[OpenStreetMap](https://www.openstreetmap.org)** and its contributors.
Branch location data used by Appdar is sourced entirely from OSM — a free, collaborative map of the world built by volunteers.
If a location is wrong or missing, anyone can [edit the map](https://www.openstreetmap.org/edit) and fix it.

© OpenStreetMap contributors — data available under the [Open Database Licence](https://opendatacommons.org/licenses/odbl/).

---

## 📄 Licence

Distributed under the MIT Licence. See [`LICENSE`](LICENSE) for more information.

---

## 📬 Contact

Have a bug, idea, or question? [Open an issue](https://github.com/yourusername/appdar/issues) or find the app on Google Play.

---

_Built with RemoteViews. It's been an adventure._




A few directions depending on the vibe you want:
Clean / minimal

AppSpot
Apprise
Vicinity

Utility / descriptive

NearApps
LocalLaunch
AppRadius

Snappy / brandable

Appdar (app + radar)
Proxly
Spotlet


My favourite is probably Appdar — it's short, memorable, hints at location/proximity, and feels native to Android. Proxly is a close second if you want something that sounds more like a modern app name.
What does the widget actually do — is it location-based (apps near your GPS location), or more like "apps relevant to what's nearby/context-aware"? That might shift the best direction.I like Appdar toocan you give me a good marketing strategy and advert13:28Strategized marketing approach for Appdar widget applicationStrategized marketing approach for Appdar widget applicationGreat choice! Before I put something together — a couple of quick questions to make sure the strategy actually fits:Q: What does Appdar actually do?
A: Shows apps relevant to your GPS locationQ: Who's the target audience? (Select all that apply)
A: Power Android users / customisers, Casual everyday users, Productivity focused peopleQ: Where do you want to market it? (Select all that apply)
A: Google Play Store listing, Social media (Reddit, Twitter/X)13:32Synthesized marketing strategy across multiple audience segments and platformsSynthesized marketing strategy across multiple audience segments and platformsGreat, here's a full strategy tailored to that:

Marketing Strategy
Core message: The right app, exactly where you are.
The hook is that your phone already knows where you are — Appdar makes that actually useful. That's a genuinely novel concept most people haven't seen as a widget before, so lead with the "aha moment" in everything.

Google Play Store
Title line: Appdar — Location-Smart App Widget
Short description:

The widget that shows the right apps for where you are. Coffee shop, gym, office — Appdar knows.

Long description structure:

Open with the problem: "You have 80 apps. You use 6 of them. But which 6 depends on where you are."
Explain the solution in one sentence
Bullet the key features
End with a call to action: "Add the widget. Go somewhere. Watch it work."

Screenshots: The most important asset on Play Store. Show the widget on a home screen in 3 real-world settings — gym, café, office. Before/after style works well.

Reddit
Target these subreddits: r/androidwidgets, r/Android, r/androidapps, r/MIUITips
Best approach is an organic post, not an ad — Reddit users respond much better to:

"Built a widget that changes your app suggestions based on GPS location — here's how it works"

Include a short screen recording GIF walking through a real location change. Comments will do the marketing for you if the concept lands.

Twitter / X
Short punchy posts work best. Series idea:

Tweet 1: The hook ("Your phone knows you're at the gym. Why doesn't your home screen?")
Tweet 2: Short demo video
Tweet 3: User testimonial or Play Store review once you have one

Use hashtags: #Android #AndroidDev #Widgets #Productivity

Ad Copy (ready to use)
Play Store banner tagline:

Your location. Your apps. Automatically.

Reddit post headline:

I built an Android widget that shows different apps depending on where you are — Appdar

Twitter/X launch tweet:

At the gym? Appdar shows Spotify and MyFitnessPal.
At the office? Slack and Calendar.
At home? Netflix and your takeaway app.
Your home screen should know where you are.
📍 Appdar — now on Google Play.
[link] #Android #Widgets


Launch Sequence

Week 1 — Optimise Play Store listing with strong screenshots before anything else. That's your conversion page.
Week 2 — Post on r/androidwidgets and r/Android. Engage every comment personally.
Week 3 — Twitter launch tweet with demo video.
Ongoing — Reply to every Play Store review early on. Algorithms reward engagement.


Google Play Store — Long Description
You're walking into Tesco. Your phone has no idea.

Appdar does.

Appdar is a home screen widget that detects where you are and 
instantly surfaces the apps you actually need — right now, 
right here. No digging through folders. No scrolling past apps 
you won't use until next week.

──────────────────────────
WHAT IT DOES RIGHT NOW
──────────────────────────

📍 Supermarkets
Pull up near an ASDA, Lidl, Aldi, Tesco, Sainsbury's or Morrisons?
Appdar shows their app front and centre — ready for your clubcard,
your shopping list, or click & collect.

☕ Coffee Shops
Starbucks? Costa? Greggs? Appdar recognises them and puts the
right app in your hand before you reach the counter.

🍔 Fast Food
McDonald's, KFC, Burger King and more — loyalty apps, order-ahead,
deals. All there the moment you arrive, gone when you leave.

──────────────────────────
WHY IT'S DIFFERENT
──────────────────────────

Your home screen is static. Your life isn't.

Most widgets show you the same apps whether you're on the sofa 
or standing in a supermarket aisle. Appdar is the first widget 
that adapts to where you actually are.

No manual switching. No shortcuts to manage. Just the right app, 
at the right place, automatically.

──────────────────────────
COMING SOON
──────────────────────────

🏠 Custom locations — set your own apps for Home, Work, Gym,
or anywhere else that matters to you.

──────────────────────────
DESIGNED FOR ANDROID
──────────────────────────

- Lightweight home screen widget
- Works on all major Android launchers
- Minimal battery and data usage
- No account required

Add the widget. Walk into a supermarket. Watch it work.

📍 Appdar — Your location. Your apps. Automatically.

Reddit Post — r/androidwidgets (also suitable for r/Android)
Title:
I built a widget that detects when you walk into a supermarket or coffee shop and shows the right app automatically — Appdar [OC]

Body:

Hey everyone, long-time Android tinkerer here. I got fed up with the same problem every time I walked into Tesco or McDonald's — unlocking my phone, searching for the app, waiting for it to open. It's a small friction but it happens multiple times a day.
So I built Appdar.
It's a home screen widget that uses your GPS location to detect where you are and surfaces the relevant app instantly. Right now it supports:

🛒 Major supermarkets — ASDA, Lidl, Aldi, Tesco, Sainsbury's, Morrisons
☕ Coffee shops — Starbucks, Costa, Greggs
🍔 Fast food — McDonald's, KFC, Burger King

Walk in, glance at your home screen, tap. That's it. When you leave, the widget goes back to normal.
Why a widget and not an app?
Because opening an app to find an app defeats the point. The widget is always visible, zero interaction needed until you actually want to tap something.

What's coming next:
I'm working on custom location support — so you can assign your own apps to Home, Work, Gym, or anywhere else. That one's been the most requested thing already and it's well underway.

I'd love any feedback, especially from people on MIUI or custom launchers — there are some quirks I'm still ironing out and real-world testing is invaluable at this stage.
[Google Play link]
Happy to answer any questions about how it works under the hood too — built with AppWidget RemoteViews which has been... an adventure.


That last line will resonate hard on r/androidwidgets and r/Android — anyone who's worked with RemoteViews will immediately respect the effort, and it invites technical discussion which boosts engagement.
