# Prayer Time & Notification System — Full Audit

## PART 1 — Prayer Time Source Validation

### 1) Source
- **API:** Aladhan API — `https://api.aladhan.com/v1/timings`
- **Parameters:** `latitude`, `longitude`, `method=13`
- **Stored:** SharedPreferences `LocationPrefs` (saved_fajr, saved_dhuhr, …) and `PrayerTimesCache` (PrayerTimesLite). Times are stored as **local time strings** (e.g. `"06:30"`), not UTC.
- **No local calculation:** All times come from the API; no local prayer-time formula in the app.

### 2) Calculation method & timezone
- **Method:** `method=13` → **Turkey – Diyanet İşleri Başkanlığı**. Diyanet-compatible.
- **Timezone:** API returns times for the **date and location**; the API handles timezone. The app does **not** pass a timezone query; Aladhan uses the request date (defaults to “today”) and location. Device timezone override is **not** applied to the API (no `timezone` query). DST is handled by the system when parsing times with `Calendar.getInstance()` (device local time).
- **Parsing:** `PrayerReminderScheduler.parseMillis()` uses `Calendar.getInstance()` (device local). So if device is set to Turkey, parsing "13:00" gives 13:00 local; DST is implicit in the system calendar.

### 3) Log comparison (Istanbul)
- To compare with Diyanet: log today’s times from the app (e.g. in `PrayerTimesViewModel` after fetch or in Dashboard) and compare with https://diyanet.gov.tr or Diyanet’s official times for Istanbul. Method 13 is the standard Diyanet method; small differences can occur due to API vs. Diyanet’s own computation or date handling.

### 4) Offsets & rounding
- **No manual minute offsets** applied to API times.
- **Rounding:** UI uses `.take(5)` for "HH:mm"; no extra rounding in storage. Times are used as returned (HH:mm, optional space and suffix stripped).
- **UTC:** Times are **not** stored in UTC; they are stored as local time strings and interpreted in device local time when scheduling.

### Risk summary
- **Source:** Aladhan API, method 13 (Diyanet).
- **Mismatch risk:** Low for Turkey if device timezone is correct; risk if device timezone differs from user’s actual location or if API date differs from device date.

---

## PART 2 — Notification Scheduling Audit

### 1) Mechanism
- **AlarmManager** only. No WorkManager for setting prayer alarms (WorkManager is only used for refresh and is cancelled in `PrayerAlarmScheduler.cancelAllPrayerAlarms`; actual scheduling is in `PrayerReminderScheduler`).
- **Exact alarms:** `setExactAndAllowWhileIdle` (API 23+), `setExact` (API 19+), fallback `set()`.
- **ForegroundService:** Not used for prayer notifications.

### 2) When scheduled
- **On fetch success:** `PrayerTimesViewModel.fetchPrayerTimes` → `PrayerReminderScheduler(context).rescheduleAll(lite)` (on **main** thread).
- **On boot / time change:** `PrayerRescheduleReceiver` (BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED, MY_PACKAGE_REPLACED) → `rescheduleAll(lite)`.
- **After each alarm fire:** `PrayerAlarmReceiver.onReceive` → `rescheduleAll(lite)` (on **main** thread).
- **Dashboard:** `scheduleFromSavedPrefsIfPossible` on **Dispatchers.IO** when view is created.
- **PrayerSettingsBottomSheet:** When toggling exact-alarm permission or enabling notifications → `scheduleIfSavedTimingsExist()` → `rescheduleAll(lite)` (main thread).
- **PrayerTimesRefreshWorker:** After API refresh → `rescheduleAll(lite)` (worker thread).

### 3) Clearing before scheduling
- **Yes.** `PrayerReminderScheduler.rescheduleAll()` calls **`cancelAll()`** first (all prayer + pre + kerahat), then schedules only future times. No duplicate scheduling from missing clear.

### 4) Request codes
- **Unique per prayer/type:** `rcExact(name) = 10000 + safeMod(name.hashCode(), 1000)`, `rcPre(name) = 11000 + …`, `rcKerahat() = 12001`. So each prayer and type has a stable, distinct request code; no overwrite between different prayers.
- **Note:** `PrayerAlarmScheduler.cancelAllPrayerAlarms()` uses `"exact_$name".hashCode()` (different from above). That method is **not** used by the current flow; only `PrayerReminderScheduler` is used. If `cancelAllPrayerAlarms` were ever used, it would **not** cancel alarms set by `PrayerReminderScheduler` (different PendingIntent request codes).

---

## PART 3 — “All Notifications Fire at Once”

### Current logic
- **PrayerReminderScheduler.schedule():** `if (triggerAt <= System.currentTimeMillis()) return` → only **future** trigger times are scheduled.
- **parseMillis():** If `cal.timeInMillis <= now`, it does `cal.add(Calendar.DAY_OF_MONTH, 1)` → past times become **next day**. So scheduling is correct: only future alarms.

### Where “fire at once” could still come from
1. **Receiver always sends notification:** When an alarm fires, `PrayerAlarmReceiver.onReceive` **always** calls `NotificationHelper.sendNow(...)`. There is no check that “prayer time has actually passed”. If an alarm were to fire early (e.g. bad timezone, clock skew, or bug), we would still show the notification. So the fix is: in the receiver, **do not fire** if current time is **before** the intended prayer time (with a small tolerance).
2. **No debounce:** If multiple alarms fire in the same second (e.g. after device wake or bug), multiple notifications can be shown. A **1-second debounce** in `NotificationHelper.sendNow` prevents more than one notification per second.
3. **Main-thread scheduling:** Calling `rescheduleAll` on the main thread (ViewModel callback, Receiver) can cause many alarms to be set in one go; moving scheduling to a background thread avoids blocking the UI and is safer.

---

## PART 4 & 5 — Fixes Applied

- **Cancel existing:** Already done in `rescheduleAll()` via `cancelAll()`.
- **Schedule only future:** Already enforced in `schedule()` and `parseMillis()`; added a small safety margin (e.g. trigger must be > now + 1 second).
- **Exact alarms:** Already using `setExactAndAllowWhileIdle` where applicable.
- **Receiver safety:** In `PrayerAlarmReceiver`, if the intended prayer time (from cache) is in the future (now < prayerTime - 1 min), **do not** call `sendNow`.
- **Debounce:** In `NotificationHelper.sendNow`, at most one notification per second (global debounce).
- **Logging:** `Log.d("PrayerDebug", "Scheduled [name] at: [time]")` when scheduling each prayer.
- **Off main thread:** All callers that were invoking `rescheduleAll` on the main thread now run it on a background thread (e.g. `Dispatchers.IO` or `Executors.newSingleThreadExecutor()`).
- **No mass send in lifecycle:** Notifications are only sent from `PrayerAlarmReceiver.onReceive` (and any other explicit send path); no “send all 5” from Activity lifecycle.

---

## Summary

| Item | Status |
|------|--------|
| Prayer time source | Aladhan API, method 13 (Diyanet) |
| Diyanet-compatible | Yes (method 13) |
| Timezone | API + device local; no app-level override |
| Scheduling | AlarmManager, cancelAll then schedule future only |
| Duplicate scheduling | Prevented by cancelAll() first |
| Past-time scheduling | Prevented (parseMillis rolls to next day; schedule() rejects triggerAt <= now) |
| Receiver “fire if past” guard | Added (do not show if prayer time not yet passed) |
| Debounce | Added (1 sec in NotificationHelper) |
| Scheduling on main thread | Moved to background at all call sites |
| Logging | PrayerDebug logs added for each scheduled prayer |
