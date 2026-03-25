# Release Notes – Nearby Apps Widget

## v1.16 (versionCode 16) – 2026‑03‑22

### What's New
- **Geofencing permission fix:** Added `ACCESS_FINE_LOCATION` permission (required for Play Services Geofencing API on Android 12+)
- **Distance calculation logging:** Added debug logs in `NearbyAppsWidgetListFactory` to diagnose the 200‑meter distance fallback
- **Tabbed companion app skeleton:** Basic tab layout with "Nearby Apps" (placeholder) and "Settings" (current setup UI)
- **Compliance updates:** Privacy policy and Data Safety form updated to reflect fine‑location usage on Android 12+

### Bug Fixes
- **Geofencing permission error:** Missing `ACCESS_FINE_LOCATION` permission now included in manifest and runtime request
- **RemoteViewsService intent filter:** Added missing intent filter and `exported=true` for `NearbyAppsWidgetListService`

### Known Issues
- Distance calculation may still show 200 m fallback (investigating via new logs)
- Widget scroll may not be fully active (height increased in v17)
- Some UI elements in the companion app are placeholders

### Compliance Notes
- Privacy policy updated to include precise location (`ACCESS_FINE_LOCATION`) usage on Android 12+
- Data Safety form now includes "Precise location" data type (collected on Android 12+, optional)
- Permission rationale for `ACCESS_FINE_LOCATION` added
- Battery disclosure updated to mention foreground‑service notification (Android 10+)

### Download
- APK: `http://192.168.0.111:8080/nearby‑apps‑widget‑v16.apk`
- SHA‑256: `c27d3cfc04fafddf41e11bb6ef69777a891562577c7a0b827561d897d42f1bb0`

---

## Previous Versions

### v1.15 (versionCode 15) – 2026‑03‑20
- Initial production‑ready widget with geofencing (coarse location only)
- Privacy policy and Data Safety form for coarse location
- Basic widget with 5‑item list, green/grey status dots
- Package‑visibility fix for Android 11+

*For earlier versions, see PROJECT_TRACKER.md.*