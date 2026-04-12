# Holiday Widget Banner — Future Feature

## Concept
A small coloured strip appears at the **bottom of the widget** on public/bank holidays,
only when there is unused vertical space below the app list. Tapping it fires a local
notification/toast: "Happy [Holiday Name]!".

A setting toggle to disable the feature will live in the app Settings screen.

## Decisions already made
| Question | Answer |
|---|---|
| Visual style | Simple coloured strip — holiday emoji + holiday name text |
| Animation | None (static strip, no Lottie) |
| Country detection | `Locale.getDefault().country` (auto from device locale) |
| Tap action | Local Toast — "Happy [Holiday Name]!" |
| Settings toggle | Yes — on/off in Settings screen |

## Data Source
**Nager.Date public API** — free, no API key, 100+ countries
```
https://date.nager.at/api/v3/PublicHolidays/{year}/{countryCode}
```
- Fetch once per day via WorkManager, cache in DataStore
- Country code from `Locale.getDefault().country`

## Implementation Plan

### Phase 1 — Holiday data
- `HolidayRepository` — WorkManager job fetches Nager.Date, caches today's holiday (if any) in DataStore as `holidayName: String?`
- Runs once at app start and once per calendar day (scheduled at midnight)

### Phase 2 — Space detection
- In `NearbyAppsWidgetProvider`, after computing list item count:
  - Widget height from `AppWidgetManager.getAppWidgetOptions()` → `OPTION_APPWIDGET_MIN_HEIGHT`
  - If `widgetHeight - (itemCount × ITEM_HEIGHT_DP) >= 48dp` → show banner
- Banner height: **48dp**

### Phase 3 — Widget banner layout
New file: `feature-widget/src/main/res/layout/widget_holiday_banner.xml`
- `LinearLayout` (horizontal), coloured background (festive amber/gold)
- `TextView` for emoji + name e.g. "🎉 Good Friday"
- Text colour: dark for contrast

### Phase 4 — Wire into widget
- In `NearbyAppsWidgetProvider.updateAppWidget()`:
  - Read `holidayName` from DataStore (blocking with `runBlocking` or pass via WorkManager output)
  - If today is a holiday AND space available → `remoteViews.addView(R.id.widget_container, bannerViews)`
  - `PendingIntent` on banner → `WidgetClickReceiver` action `ACTION_HOLIDAY_TOAST` → show Toast

### Phase 5 — Settings toggle
- Add `showHolidayBanner: Boolean = true` to `UserPreferences` / DataStore
- Settings screen: simple Switch row "Show holiday banner"
- Widget reads pref and skips banner if disabled

## Notes
- `RemoteViews` cannot run Lottie animations directly — static strip only
- WorkManager job should be tagged `"holiday_fetch"` so it can be cancelled/replaced cleanly
- Nager.Date returns an empty array (not an error) on non-holiday days — safe to handle
- Consider caching per `{year}/{countryCode}` key so the full year's holidays are
  available offline after first fetch

## Why deferred
Added during closed testing (v135). Better to ship a stable widget first, then layer in
delight features. Pick this up after v1.0 public launch.
