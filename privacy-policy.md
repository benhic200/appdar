# Privacy Policy for Appdar

**Last updated:** March 2026
**Developer:** Hickie Labs
**Contact:** ben.hickie+dev@gmail.com

---

## Overview

Appdar is a home screen widget that uses your device's GPS location to detect nearby places — such as supermarkets, fast food restaurants, and coffee shops — and surfaces relevant apps automatically.

This privacy policy explains what data Appdar accesses, how it is used, and your rights as a user.

---

## What data we access

### Location data
Appdar requests access to your device's location in order to detect when you are near a supported place (for example, a supermarket or coffee shop). This is the core function of the app.

- **Foreground location** — used when the app or widget is active
- **Background location** — used to update the widget while your screen is off

Location data is processed **entirely on your device**. It is never transmitted to our servers, never shared with third parties, and never stored beyond the current session.

### No other data is collected
Appdar does not collect, store, or transmit:
- Your name, email address, or any personal identifiers
- Your browsing history or app usage
- Device identifiers or advertising IDs
- Any data from the apps it surfaces on screen

---

## How we use your location

Your location is used solely to:

1. Detect when you are near a supported place
2. Display the relevant app on your home screen widget
3. Switch back to your default apps when you leave

Your location is never used for advertising, analytics, or any purpose beyond the core widget functionality described above.

---

## Data sharing

We do not share any data with third parties. There are no analytics tools, advertising SDKs, or third-party services embedded in Appdar.

---

## Data storage and retention

Appdar does not store your location data. Each location check is performed in the moment and discarded immediately. Nothing is written to disk or sent anywhere.

Your app preferences (such as your Home apps selection) are stored locally on your device using Android's SharedPreferences. This data never leaves your device.

---

## Third-party services

Appdar uses the following Android and Google services:

- **Google Play Billing** — used to process the optional Pro upgrade purchase. Payment data is handled entirely by Google and is subject to [Google's Privacy Policy](https://policies.google.com/privacy).
- **Google Play Services (FusedLocationProviderClient)** — used to obtain your device's location efficiently. Subject to [Google's Privacy Policy](https://policies.google.com/privacy).

---

## Permissions explained

| Permission | Why it's needed |
|---|---|
| `ACCESS_FINE_LOCATION` | Accurately detect nearby places |
| `ACCESS_COARSE_LOCATION` | Fallback location for lower-power detection |
| `ACCESS_BACKGROUND_LOCATION` | Update the widget while the screen is off |

You can revoke location permissions at any time via your device's Settings → Apps → Appdar → Permissions. Revoking location permission will prevent the widget from detecting nearby places.

---

## Children's privacy

Appdar is not directed at children under the age of 13 and we do not knowingly collect any data from children.

---

## Changes to this policy

We may update this privacy policy from time to time. Any changes will be reflected on this page with an updated date at the top. Continued use of Appdar after changes are posted constitutes acceptance of the updated policy.

---

## Contact us

If you have any questions about this privacy policy or how Appdar handles your data, please contact us at:

**ben.hickie+dev@gmail.com**

---

*Appdar is developed by Hickie Labs, Durham, England, UK.*
