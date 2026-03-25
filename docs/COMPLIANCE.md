# Compliance Documentation – Nearby Apps Widget (v15 – Phase 1 + Phase 2)

## Privacy Policy (Full – for hosting)

### 1. Location Data
This widget uses location permissions (`ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION`) via Android’s Geofencing API to detect when you are near a business included in our local database. On Android 12 and above, precise location (`ACCESS_FINE_LOCATION`) is required for the Geofencing API to function reliably. On earlier Android versions, approximate location (`ACCESS_COARSE_LOCATION`) is used. The location is used solely to determine which business‑app mappings to display; no location data is transmitted to any server or shared with third parties, unless you explicitly enable optional geocoding (see section 3). Location processing occurs entirely on your device and is discarded immediately after the widget updates.

**What we store locally:** The widget’s local database contains predefined business‑app mappings (business name, package name, and geographic coordinates). This data does not include any personal information.

**User controls:** You can enable or disable location access at any time in the app’s settings. The widget also allows you to adjust the detection radius (default 500 m) to control how close a business must be to appear.

**Data retention:** Your device’s location is never stored. Business‑app mappings are stored locally and updated only when you install a new version of the widget or sync with our server (optional).

### 2. Background Location
When you grant background location permission (`ACCESS_BACKGROUND_LOCATION`, required on Android 10+), the widget can continue to detect nearby businesses even when the app is not in the foreground. This allows the widget to update when you enter or exit a geofenced area. Background location is used exclusively for geofencing and is never transmitted off‑device.

### 3. Geocoding (Optional Feature)
If you enable the optional “Show addresses” feature, the app will send your approximate coordinates to Google’s Geocoding API (a third‑party service) to convert your location into a human‑readable address. This transmission is encrypted and subject to Google’s privacy policy. You can disable this feature at any time in settings.

### 4. Location History Cache
To improve widget responsiveness, the app may temporarily cache your recent locations on‑device (up to 24 hours). This cache is never synced to any server and is automatically cleared after 24 hours or when you uninstall the app.

### 5. Remote Business Mappings
Periodically, the app may download updated business‑app mappings from our server. This sync transmits only your device’s Android version and locale to ensure compatibility; no personal data is included.

### 6. Settings and Preferences
Your widget preferences (detection radius, distance units, enabled categories, etc.) are stored locally on your device. These settings are not backed up to any cloud service unless you explicitly enable backup in Android settings.

### 7. Data Security
All data processing occurs on‑device. No personal data is transmitted to our servers, except for the optional geocoding feature (coordinates sent to Google) and anonymised sync data (Android version, locale).

### 8. Your Rights
You have the right to access, correct, or delete any personal data we hold. Since we do not store personal data on our servers, you can manage your data directly through the app’s settings or by uninstalling the app.

---

## Data Safety Form Answers (Play Console – v16)

### Approximate location
- **Collected?** Yes, optional (used on Android 11 and below)
- **Shared?** No (unless optional geocoding enabled – see below)
- **Purpose:** App functionality (to show nearby business apps)
- **Processing:** Ephemeral on‑device; discarded after each widget update
- **Deletion:** Automatically discarded after use; no location logs retained

### Precise location
- **Collected?** Yes, optional (required on Android 12+ for geofencing)
- **Shared?** No (unless optional geocoding enabled – see below)
- **Purpose:** App functionality (to show nearby business apps)
- **Processing:** Ephemeral on‑device; discarded after each widget update
- **Deletion:** Automatically discarded after use; no location logs retained

### Approximate location (in background)
- **Collected?** Yes, optional (required for Android 10+ geofencing in background)
- **Shared?** No
- **Purpose:** App functionality (widget updates when app is not in foreground)
- **Processing:** Same as foreground location – ephemeral, on‑device
- **Deletion:** Automatically discarded after use

### Precise location (in background)
- **Collected?** Yes, optional (required for Android 12+ geofencing in background)
- **Shared?** No
- **Purpose:** App functionality (widget updates when app is not in foreground)
- **Processing:** Same as foreground location – ephemeral, on‑device
- **Deletion:** Automatically discarded after use

### Device/app history (location cache)
- **Collected?** Yes, optional (cached locally for up to 24 h)
- **Shared?** No
- **Purpose:** App functionality (improve widget responsiveness)
- **Processing:** Stored locally, automatically cleared after 24 h
- **Deletion:** Automatically deleted after 24 h or on app uninstall

### Other app performance data (sync analytics)
- **Collected?** Yes, optional (Android version, locale)
- **Shared?** No
- **Purpose:** Analytics (ensure compatibility of business‑app mappings)
- **Processing:** Anonymised, aggregated
- **Deletion:** Retained for 30 days, then anonymised

### Approximate location (shared with third‑party geocoding service)
- **Collected?** Yes, optional (only if “Show addresses” enabled)
- **Shared?** Yes, with Google’s Geocoding API
- **Purpose:** App functionality (convert coordinates to readable address)
- **Processing:** Encrypted transmission; subject to Google’s privacy policy
- **Deletion:** Google’s data‑retention policies apply

### Precise location (shared with third‑party geocoding service)
- **Collected?** Yes, optional (only if “Show addresses” enabled)
- **Shared?** Yes, with Google’s Geocoding API
- **Purpose:** App functionality (convert coordinates to readable address)
- **Processing:** Encrypted transmission; subject to Google’s privacy policy
- **Deletion:** Google’s data‑retention policies apply

---

## Permission Rationale

### `ACCESS_COARSE_LOCATION`
> “Nearby Apps Widget needs your approximate location to show apps for businesses near you. We use Android’s geofencing system—so we only check when you enter or exit a predefined area, not constantly. No location data ever leaves your device.”

### `ACCESS_FINE_LOCATION`
> “On Android 12 and above, Nearby Apps Widget needs precise location for geofencing to work reliably. This allows the widget to accurately detect when you enter or exit a business area. No location data ever leaves your device.”

### `ACCESS_BACKGROUND_LOCATION` (Android 10+)
> “Nearby Apps Widget needs background location access so it can update the widget when you enter or exit a business area, even when the app isn’t open. We use Android’s battery‑efficient geofencing system, and your location never leaves your device.”

---

## Battery Disclosure (store listing / in‑app)

> **Battery note:** This widget uses Android’s built‑in Geofencing API, which is designed to be battery‑efficient. It does not continuously poll your location. On Android 10 and above, geofencing uses a foreground service (with a persistent notification) to ensure reliable updates in the background. This foreground service is optimized for minimal battery impact. If you enable optional real‑time distance updates, battery usage may increase slightly. You can further reduce battery usage by increasing the detection radius in settings.

---

## ⚠️ Risk Notes & Compliance Checklist

- [ ] Manifest requests `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, and `ACCESS_BACKGROUND_LOCATION` (for Android 10+). Fine location is required for geofencing on Android 12+.
- [ ] Data Safety answers match the code’s actual behaviour (coarse location on Android 11 and below, fine location on Android 12+, optional geocoding).
- [ ] Background‑location justification is clear and complies with Google Play’s “Allowed use” policy (widget updates when app in background).
- [ ] Optional geocoding is clearly disclosed as third‑party data sharing (Google Geocoding API).
- [ ] Location‑history cache is ephemeral (24 h) and disclosed.
- [ ] Remote sync transmits only non‑personal data (Android version, locale).
- [ ] Battery disclosure is accurate and includes foreground‑service notification mention (Android 10+).

---

*Last updated: 2026‑03‑22 (v16 – Fine location permission added)*  
*Prepared by Android Release specialist.*