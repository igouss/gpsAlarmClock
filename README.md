# GPS Alarm Clock

An Android alarm clock that fires alarms based on your physical location. Set an alarm that only goes off when you're inside (or outside) a defined geographic boundary — useful for commuters who sleep on transit, travelers in unfamiliar time zones, or anyone whose schedule depends on where they are rather than what time it is.

<!-- TODO: add screenshots -->

## Features

- **Alarm types:** Full week / Workdays only / Weekends only / Date range
- **Geofenced alarms:** alarm fires only when you're within the configured radius of a set location; if you're outside the fence, the alarm silently reschedules and checks again next time
- **Map picker:** OpenStreetMap via osmdroid (no API key required), crosshair UX where the map pans under a fixed center marker, adjustable geofence radius 100–2000m with a circle overlay
- **Reboot-safe:** BootReceiver reschedules all enabled alarms after device restart
- **Doze-compatible:** uses `AlarmManager.setExactAndAllowWhileIdle` — fires in Android Doze mode
- **Dark Material3 theme**
- **Minimum Android 8.0 (API 26)**

## Permissions

The app requests the following permissions:

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Geofence boundary checks when an alarm fires |
| `ACCESS_BACKGROUND_LOCATION` | Check location while the app is not in the foreground |
| `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM` | Reliable alarm timing; required for `setExactAndAllowWhileIdle` |
| `RECEIVE_BOOT_COMPLETED` | Reschedule alarms after reboot |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Play alarm sound and check location while the app is backgrounded |
| `VIBRATE` | Alarm delivery |
| `POST_NOTIFICATIONS` | Show alarm notification on API 33+ |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Load OpenStreetMap tiles in the map picker |

## Privacy

No data leaves the device. There are no analytics, no crash reporting, no ads, and no accounts. Location data is read at alarm fire time solely to check the geofence boundary; it is never stored beyond what the OS caches as "last known location." See the [privacy policy](https://igouss.github.io/gpsAlarmClock/privacy_policy.html) for full details.

## Building

Requires JDK 17 and Android SDK. See [SETUP.md](SETUP.md) for full environment setup.

```bash
# Debug build
JAVA_HOME=/home/linuxbrew/.linuxbrew/opt/openjdk@17 ./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

For release builds and signing, see the [Release build & signing](SETUP.md#release-build--signing) section in SETUP.md.

## Architecture

```
Alarm               — Room entity (id, label, hour, minute, alarmType,
                      startDate, endDate, isEnabled, isGeoFenced,
                      geoFenceLat, geoFenceLng, geoFenceRadius)
AlarmType           — enum: FULL_WEEK | WORKDAY | WEEKEND | DATE_RANGE
AlarmScheduler      — computes next trigger per type/day, setExactAndAllowWhileIdle
AlarmReceiver       — fires alarm; if geofenced, checks last known location (fail-open if null)
AlarmService        — foreground service; plays alarm sound and shows notification
BootReceiver        — reschedules all enabled alarms after reboot
DismissAlarmReceiver — handles notification dismiss action
GpsAlarmClockApp    — Application class; creates notification channel
MapPickerActivity   — OSMDroid fullscreen map, crosshair UX, radius slider, circle overlay
AddEditAlarmBottomSheet — BottomSheetDialogFragment; ActivityResultLauncher for map picker
AlarmsAdapter       — ListAdapter with DiffUtil; disabled alarms at alpha 0.5
AlarmDao / AlarmDatabase / AlarmRepository — Room layer
AlarmViewModel      — ViewModel backed by AlarmRepository
```

**Stack:** AGP 8.7.0, Kotlin 2.0.21, Gradle 8.9, kotlin-kapt (not KSP), traditional XML views (no Compose), Room v2 with `fallbackToDestructiveMigration`, osmdroid 6.1.18.

## License

MIT
