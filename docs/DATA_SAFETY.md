# Data Safety Form Answers – Nearby Apps Widget (v16)

This document provides the exact answers to be entered in Google Play Console’s **Data Safety** form for the Nearby Apps Widget (version 16). All answers reflect the app’s behaviour as of March 22, 2026.

---

## 1. Data Types Collected

### Approximate location
- **Does your app collect this data?** Yes (on Android 11 and below)
- **Is this data required for your app to function?** No (optional)
- **Purpose:** App functionality
- **Is this data shared with third parties?** No
- **Data retention policy:** Not retained – data is deleted immediately after processing.

### Precise location
- **Does your app collect this data?** Yes (on Android 12+)
- **Is this data required for your app to function?** No (optional)
- **Purpose:** App functionality
- **Is this data shared with third parties?** No
- **Data retention policy:** Not retained – data is deleted immediately after processing.

### Approximate location (in background)
- **Does your app collect this data?** Yes (on Android 10+)
- **Is this data required for your app to function?** No (optional)
- **Purpose:** App functionality (widget updates in background)
- **Is this data shared with third parties?** No
- **Data retention policy:** Not retained – data is deleted immediately after processing.

### Precise location (in background)
- **Does your app collect this data?** Yes (on Android 12+)
- **Is this data required for your app to function?** No (optional)
- **Purpose:** App functionality (widget updates in background)
- **Is this data shared with third parties?** No
- **Data retention policy:** Not retained – data is deleted immediately after processing.

### Device/app history (location cache)
- **Does your app collect this data?** Yes
- **Is this data required for your app to function?** No (optional)
- **Purpose:** App functionality (improve widget responsiveness)
- **Is this data shared with third parties?** No
- **Data retention policy:** Deleted within 24 hours.

### Other app performance data (sync analytics)
- **Does your app collect this data?** Yes
- **Is this data required for your app to function?** No (optional)
- **Purpose:** Analytics
- **Is this data shared with third parties?** No
- **Data retention policy:** Deleted after 30 days.

### Approximate location (shared with third‑party geocoding service)
- **Does your app collect this data?** Yes (only if “Show addresses” enabled)
- **Is this data required for your app to function?** No (optional)
- **Purpose:** App functionality (convert coordinates to readable address)
- **Is this data shared with third parties?** Yes (Google Geocoding API)
- **Data retention policy:** Governed by Google’s privacy policy.

### Precise location (shared with third‑party geocoding service)
- **Does your app collect this data?** Yes (only if “Show addresses” enabled)
- **Is this data required for your app to function?** No (optional)
- **Purpose:** App functionality (convert coordinates to readable address)
- **Is this data shared with third parties?** Yes (Google Geocoding API)
- **Data retention policy:** Governed by Google’s privacy policy.

---

## 2. Data Types Not Collected

The app does **not** collect:
- Personal identifiers (name, email, phone number, etc.)
- Financial information
- Health & fitness data
- Messages
- Photos & videos
- Audio files
- Contacts
- Calendar events
- Files & docs

---

## 3. Security Practices

- **Data encryption:** All transmitted data (geocoding coordinates, sync data) is encrypted in transit (HTTPS).
- **Data deletion:** Users can delete all local data by uninstalling the app.
- **Third‑party disclosures:** The app discloses when data is shared with third parties (Google Geocoding API) and provides a link to the third party’s privacy policy.

---

## 4. Permissions Justification

### `ACCESS_COARSE_LOCATION`
- **Why this permission?** To detect when the user is near a business included in the local database (Android 11 and below).
- **How is it used?** The app uses Android’s Geofencing API, which only checks location when the user enters/exits a predefined area (geofence). No continuous polling.
- **User control:** Users can deny this permission; the widget will then show a static list of businesses.

### `ACCESS_FINE_LOCATION`
- **Why this permission?** On Android 12 and above, precise location is required for the Geofencing API to function reliably.
- **How is it used?** Same geofencing mechanism as coarse location; no additional data collection.
- **User control:** Users can deny this permission; geofencing will not work on Android 12+.

### `ACCESS_BACKGROUND_LOCATION` (Android 10+)
- **Why this permission?** To allow the widget to update when the user enters/exits a geofenced area while the app is not in the foreground.
- **How is it used?** Same geofencing mechanism as foreground location; no additional data collection.
- **User control:** Users can deny this permission; geofencing will only work when the app is in the foreground.

---

## 5. Play Console Declaration Text

You may copy the following summary for the “Data safety” section of the store listing:

> “This app uses location (approximate on Android 11 and below, precise on Android 12+) to show nearby business apps via Android’s battery‑efficient geofencing. Location data is processed on‑device and never stored or shared, except for optional address lookup (Google Geocoding API). The app may cache recent locations locally for up to 24 hours to improve responsiveness. No personal data is collected.”

---

*Last updated: 2026‑03‑22*  
*Prepared by Android Release specialist.*