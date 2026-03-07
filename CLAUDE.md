# Dev Environment

## Android SDK
- `ANDROID_HOME`: `/home/elendal/Android`
- Platform: android-35, build-tools 35.0.0
- AVD: `Pixel6_API35` (Google APIs, x86_64) — screen 1080x2400

## Java
- JDK 17 at `/home/linuxbrew/.linuxbrew/opt/openjdk@17` — **always use this, not system JDK 25**
- Always prefix gradle: `JAVA_HOME=/home/linuxbrew/.linuxbrew/opt/openjdk@17 ./gradlew ...`

## Project
- Package: `com.elendal.gpsalarmclock`
- AGP 8.7.0 / Kotlin 2.0.21 / Gradle 8.9
- Min SDK 26, Target SDK 35
- **kotlin-kapt** for annotation processing — NOT KSP
- Traditional XML views — NO Jetpack Compose
- Dark Material3 theme (`Theme.Material3.Dark.NoActionBar`)

## Key commands
```bash
# Build
JAVA_HOME=/home/linuxbrew/.linuxbrew/opt/openjdk@17 ./gradlew assembleDebug 2>&1 | tail -60

# Install (emulator must be running)
JAVA_HOME=/home/linuxbrew/.linuxbrew/opt/openjdk@17 ./gradlew installDebug

# Start emulator (check first with: adb devices)
$ANDROID_HOME/emulator/emulator -avd Pixel6_API35 -no-snapshot -no-audio 2>/dev/null &
# Wait for boot: adb shell getprop sys.boot_completed  (loops until "1")

# One-shot screenshot (faster than screencap + pull)
adb exec-out screencap -p > /tmp/ss.png

# Find UI element coordinates without guessing pixels
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml
# Parse bounds="[x1,y1][x2,y2]", tap center: adb input tap cx cy

# Check crash before screenshotting
adb logcat -s AndroidRuntime:E -d | grep "FATAL"

# Filter logcat to app only
adb logcat --pid=$(adb shell pidof com.elendal.gpsalarmclock) 2>/dev/null
```

## ADB coordinate system
Screenshots from `Read` tool are displayed at 900x2000, but adb tap coords use physical 1080x2400.
**Multiply displayed coords by 1.2** to get correct tap coordinates.

## Architecture
```
Alarm (Room entity)
  └── AlarmType enum: FULL_WEEK | WORKDAY | WEEKEND | DATE_RANGE
  └── Geofence fields: isGeoFenced, geoFenceLat, geoFenceLng, geoFenceRadius (meters, default 500)

AlarmScheduler    — computes next trigger per type/day, uses setExactAndAllowWhileIdle
AlarmReceiver     — fires alarm; if geofenced, checks last known location first (fail-open if null)
BootReceiver      — reschedules all enabled alarms after reboot
DismissAlarmReceiver — handles notification dismiss action
GpsAlarmClockApp  — Application class; creates notification channel "alarm_channel"

MapPickerActivity — OSMDroid fullscreen map, crosshair UX (map moves under fixed crosshair),
                    radius Slider 100–2000m, circle overlay, "Use Current Location" button
AddEditAlarmBottomSheet — BottomSheetDialogFragment; uses ActivityResultLauncher for MapPickerActivity
AlarmsAdapter     — ListAdapter with DiffUtil; disabled alarms rendered at alpha 0.5
```

## Room DB
- Version: 2
- `fallbackToDestructiveMigration()` in builder — bump version on schema changes, no migration code needed
- TypeConverter: AlarmType ↔ String

## Dependencies (non-obvious)
- `osmdroid-android:6.1.18` — OpenStreetMap, no API key, needs INTERNET permission
- `kotlinx-coroutines-play-services` — for `await()` on FusedLocationProviderClient Tasks

## Permissions in manifest
Location (fine + coarse + background), foreground service (location type), exact alarm,
boot completed, vibrate, post notifications, internet, access network state.

## Process
- Delegate all file writes to a **coder agent** (preserves main context)
- Delegate emulator boot + install + screenshot verification to a **tester agent**
- Coder agent self-corrects build errors — give it `bypassPermissions` mode
