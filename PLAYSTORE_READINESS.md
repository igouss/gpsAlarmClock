# Play Store Publishing Readiness — Handoff Document

This document lists every deficiency blocking or risking Play Store submission,
with exact current state, what needs to change, and what the fixed result looks like.
Fix them in priority order. Each section is self-contained.

---

## Changelog — Changes Applied

### 2026-03-08

| # | Item | Status | Files Changed |
|---|------|--------|---------------|
| 7 | Background location disclosure — fix UI not updating after permission grant | ✅ Done | `AddEditAlarmBottomSheet.kt` — added `updateGeofenceVisibility()` call in the granted branch of `backgroundLocationPermissionLauncher` |
| — | `MapPickerActivity` — replace deprecated `PreferenceManager.getDefaultSharedPreferences` (removed in API 29 compat lib) with `getSharedPreferences("osmdroid", MODE_PRIVATE)` | ✅ Done | `MapPickerActivity.kt` |
| — | `MapPickerActivity` — fix OSMDroid `Polygon` API: `fillColor`/`strokeColor`/`strokeWidth` replaced with `fillPaint.color`/`outlinePaint.color`/`outlinePaint.strokeWidth` (circle overlay was broken) | ✅ Done | `MapPickerActivity.kt` |

### 2026-03-07

All in-repo fixes have been applied. Build passes (`assembleDebug` clean). Remaining items require manual developer action outside the repo.

| # | Item | Status | Files Changed |
|---|------|--------|---------------|
| 1 | Signing config stub | ✅ Done | `app/build.gradle.kts` |
| 2 | Launcher icons / mipmap | ✅ Done | `AndroidManifest.xml`, `mipmap-anydpi-v26/ic_launcher.xml`, `mipmap-anydpi-v26/ic_launcher_round.xml`, `drawable/ic_launcher_foreground.xml`, `values/colors.xml` |
| 3 | Privacy policy | ⏳ Manual — host a public URL and link in Play Console | — |
| 4 | Insecure backup | ✅ Done | `AndroidManifest.xml` (`allowBackup=false`) |
| 5 | AlarmReceiver exported | ✅ Done | `AndroidManifest.xml` (`exported=false`, safe: AlarmScheduler uses explicit intent) |
| 6 | AlarmService foregroundServiceType | ✅ Done | `AndroidManifest.xml` (`location\|mediaPlayback`) |
| 7 | Background location disclosure | ✅ Done | New: `PrivacyDisclosureActivity.kt`, `activity_privacy_disclosure.xml`, `AndroidManifest.xml`; wired into `AddEditAlarmBottomSheet.kt` |
| 8 | ProGuard / R8 disabled | ✅ Done | `app/build.gradle.kts` (`isMinifyEnabled=true`, `isShrinkResources=true`), `proguard-rules.pro` |
| 9 | Play Store listing assets | ⏳ Manual — screenshots, feature graphic, description, content rating in Play Console | — |

### Remaining manual steps

**Signing keystore** (required to produce a release AAB):
```bash
keytool -genkey -v \
  -keystore ~/gps-alarm-release.jks \
  -alias gps-alarm \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```
Then add to `~/.gradle/gradle.properties`:
```properties
GPS_ALARM_KEYSTORE=/home/<you>/gps-alarm-release.jks
GPS_ALARM_KEY_ALIAS=gps-alarm
GPS_ALARM_KEY_PASSWORD=<password>
GPS_ALARM_STORE_PASSWORD=<password>
```

**Launcher icon** — the current icon is a placeholder adaptive icon using the existing vector drawables. Replace with a real designed icon before submission. The 512×512 PNG for Play Console is separate (uploaded in Play Console, not in the APK).

**Privacy policy** — write and host a public HTML page covering location data use. Link in Play Console → Store presence → Store settings → Privacy policy URL. Also complete the Background Location access justification form in Play Console.

---

---

## 1. CRITICAL — No Signing Configuration

### Current state
`app/build.gradle.kts` has no signing config. The `release` build type is bare:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = false
        proguardFiles(...)
    }
    // No signingConfig defined anywhere
}
```

A release AAB/APK cannot be produced without a keystore.

### What to do
1. Generate a keystore (one-time, store it outside the repo):
   ```bash
   keytool -genkey -v \
     -keystore ~/gps-alarm-release.jks \
     -alias gps-alarm \
     -keyalg RSA -keysize 2048 \
     -validity 10000
   ```
2. Store credentials in `~/.gradle/gradle.properties` (never in the repo):
   ```properties
   GPS_ALARM_KEYSTORE=/home/<you>/gps-alarm-release.jks
   GPS_ALARM_KEY_ALIAS=gps-alarm
   GPS_ALARM_KEY_PASSWORD=<password>
   GPS_ALARM_STORE_PASSWORD=<password>
   ```
3. Add to `app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file(providers.gradleProperty("GPS_ALARM_KEYSTORE").get())
            storePassword = providers.gradleProperty("GPS_ALARM_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("GPS_ALARM_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("GPS_ALARM_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### Fixed state
`./gradlew bundleRelease` produces a signed AAB ready for upload. The keystore file lives
outside the repo and credentials are in `~/.gradle/gradle.properties`.

---

## 2. CRITICAL — Missing Launcher Icons

### Current state
`app/src/main/AndroidManifest.xml` line 30:
```xml
android:icon="@drawable/ic_launcher_background"
```
This points to a vector drawable. Play Store requires bitmap launcher icons in `mipmap-*`
density buckets. There are no `mipmap-*/` directories in the project.

### What to do
1. Design or generate a 1024×1024 PNG app icon (use Android Studio's Image Asset Studio,
   or any design tool, or an AI image generator).
2. Use Android Studio → File → New → Image Asset to generate all densities, OR manually
   place PNGs in these directories under `app/src/main/res/`:

   | Directory         | Size (px) |
   |-------------------|-----------|
   | `mipmap-mdpi/`    | 48×48     |
   | `mipmap-hdpi/`    | 72×72     |
   | `mipmap-xhdpi/`   | 96×96     |
   | `mipmap-xxhdpi/`  | 144×144   |
   | `mipmap-xxxhdpi/` | 192×192   |

   Each directory gets `ic_launcher.png` and `ic_launcher_round.png`.

3. Update `AndroidManifest.xml`:
   ```xml
   <application
       android:icon="@mipmap/ic_launcher"
       android:roundIcon="@mipmap/ic_launcher_round"
       ...>
   ```

### Fixed state
Running `./gradlew assembleRelease` and inspecting the APK shows proper mipmap entries.
The launcher displays the correct icon on the device home screen.

---

## 3. CRITICAL — No Privacy Policy

### Current state
The app collects `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, and
`ACCESS_BACKGROUND_LOCATION`. Play Store mandatory policy (since 2021): any app that
collects or uses location data must have a publicly accessible privacy policy URL
linked in the Play Console listing. There is none.

### What to do
1. Write a privacy policy. Minimum required content:
   - What data is collected (location, alarm settings stored locally)
   - Why it's collected (geofence alarm triggering)
   - Whether it's shared with third parties (it's not — state that explicitly)
   - How users can request deletion
   - Contact email

   A minimal hosted HTML file on GitHub Pages is sufficient.

2. Link it in Play Console under: **Store presence → Store settings → Privacy policy URL**.

3. Optionally (but recommended), also display it inside the app under Settings or an
   About screen with a tappable link.

### Fixed state
Play Console accepts submission. The privacy policy URL is reachable, returns HTTP 200,
and covers location data. Background location access policy form is completed in Play Console.

---

## 4. CRITICAL — Insecure Backup Configuration

### Current state
`AndroidManifest.xml` line 29:
```xml
android:allowBackup="true"
```
No `android:dataExtractionRules` or `android:fullBackupContent` attribute is specified.
This means the entire app data directory — including the Room database containing alarm
schedules and geofence coordinates (lat/lng of user's home/workplace) — is backed up to
Google Drive and extractable via `adb backup` on unencrypted/rooted devices.

### What to do
**Option A (recommended): Disable backup entirely.**
```xml
<application
    android:allowBackup="false"
    ...>
```

**Option B: Allow backup but exclude the sensitive database.**

Create `app/src/main/res/xml/backup_rules.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="database" path="gps_alarm_clock_database" />
</full-backup-content>
```

Create `app/src/main/res/xml/data_extraction_rules.xml` (API 31+):
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="gps_alarm_clock_database" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" path="gps_alarm_clock_database" />
    </device-transfer>
</data-extraction-rules>
```

Then in `AndroidManifest.xml`:
```xml
<application
    android:allowBackup="true"
    android:fullBackupContent="@xml/backup_rules"
    android:dataExtractionRules="@xml/data_extraction_rules"
    ...>
```

### Fixed state
`adb backup` no longer exposes geofence location data. Play Store security review passes.
If Option A is chosen, users understand their alarm data is local-only.

---

## 5. HIGH — AlarmReceiver Exported Without Protection

### Current state
`AndroidManifest.xml` line 53–55:
```xml
<receiver
    android:name=".AlarmReceiver"
    android:exported="true" />
```

`AlarmReceiver` is exported but has no `android:permission` guard and no intent-filter
that would limit what intents it accepts. Any app on the device can send an arbitrary
intent to it, triggering alarm firing behavior — a security vulnerability.

`AlarmReceiver` should only be triggered by the Android `AlarmManager` system service.

### What to do

Option A — use a signature-level permission (cleanest):
```xml
<!-- Declare permission -->
<permission
    android:name="com.elendal.gpsalarmclock.permission.ALARM_TRIGGER"
    android:protectionLevel="signature" />

<uses-permission android:name="com.elendal.gpsalarmclock.permission.ALARM_TRIGGER" />

<receiver
    android:name=".AlarmReceiver"
    android:exported="true"
    android:permission="com.elendal.gpsalarmclock.permission.ALARM_TRIGGER" />
```

Then in `AlarmScheduler.kt`, when building the `PendingIntent` for `AlarmManager`, pass
a broadcast intent — `AlarmManager` will fire it with system credentials, which satisfy
the signature permission because the system is trusted.

Option B — set exported="false" (simplest, check if AlarmManager still works):
On API 26+, `AlarmManager` can trigger non-exported receivers via explicit intents.
If `AlarmScheduler` already uses `PendingIntent.getBroadcast` with an explicit
`ComponentName`, setting `android:exported="false"` is safe and simpler.

```xml
<receiver
    android:name=".AlarmReceiver"
    android:exported="false" />
```

Verify `AlarmScheduler.kt` creates the intent with:
```kotlin
Intent(context, AlarmReceiver::class.java)  // explicit — works with exported=false
```

### Fixed state
`AlarmReceiver` cannot be triggered by third-party apps. Alarms fire correctly because
`AlarmManager` uses an explicit intent. Play Store security scan passes.

---

## 6. HIGH — AlarmService Missing `location` Foreground Service Type

### Current state
`AndroidManifest.xml` line 69–72:
```xml
<service
    android:name=".AlarmService"
    android:exported="false"
    android:foregroundServiceType="mediaPlayback" />
```

`AlarmService` plays the alarm sound (`mediaPlayback` — correct). But `AlarmReceiver`
also performs a location check before deciding whether to fire the alarm. If that
location check happens inside or is initiated by `AlarmService`, the service is accessing
location without declaring `location` as a foreground service type. On API 29+, this
throws a `SecurityException` at runtime.

### What to do
Add `location` to the foreground service type:

```xml
<service
    android:name=".AlarmService"
    android:exported="false"
    android:foregroundServiceType="location|mediaPlayback" />
```

Also ensure the manifest already has (it does):
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

If the location check is done exclusively in `AlarmReceiver` (a `BroadcastReceiver`)
and NOT in `AlarmService`, then `AlarmService` doesn't need the `location` type — but
verify this in code before deciding.

### Fixed state
No `SecurityException` at runtime when `AlarmService` is running and location is
accessed. Play Store technical review passes the foreground service type declaration.

---

## 7. HIGH — No Background Location Opt-In Disclosure Screen

### Current state
The app requests `ACCESS_BACKGROUND_LOCATION` but has no in-app screen explaining to
the user that location is used in the background to trigger geofence alarms. Play Store's
**Location Permissions Policy** (enforced since 2021) requires apps using background
location to:
- Prominently disclose the use case before requesting the permission
- Not request background location at app install time; it must be a secondary permission

There is currently no such disclosure flow.

### What to do
1. Add a `LocationDisclosureActivity` or a dialog that appears when the user first
   enables a geofenced alarm. It must clearly state, before any permission prompt:

   > "GPS Alarm Clock accesses your location in the background to trigger alarms when
   > you enter or leave a geographic area. This happens even when the app is closed."

2. Only after user taps "I understand" → call `requestPermissions` for
   `ACCESS_BACKGROUND_LOCATION`.

3. On Android 11+ (API 30+), the system forces users to grant background location from
   Settings → App → Permissions manually — make sure the app handles the case where
   this permission is denied and gracefully falls back (disable geofence, show a message).

### Fixed state
User sees a clear disclosure before background location permission is requested. Play
Store location policy review passes. App handles denied background location without
crashing.

---

## 8. MEDIUM — ProGuard / R8 Disabled

### Current state
`app/build.gradle.kts` line 28:
```kotlin
isMinifyEnabled = false
```
Code is shipped unobfuscated and unshrunk. This is not a hard rejection criterion, but:
- It exposes all class/method names to reverse engineering
- Release APK/AAB is unnecessarily large
- Play Store flags it in the pre-launch report

### What to do
Enable minification and resource shrinking in the release build type:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

Add required keep rules to `app/proguard-rules.pro` to prevent R8 from stripping
Room entities, OSMDroid, and broadcast receivers:

```proguard
# Room entities
-keep class com.elendal.gpsalarmclock.Alarm { *; }
-keep enum com.elendal.gpsalarmclock.AlarmType { *; }

# OSMDroid
-dontwarn org.osmdroid.**
-keep class org.osmdroid.** { *; }

# BroadcastReceivers (referenced by name in AlarmManager PendingIntents)
-keep class com.elendal.gpsalarmclock.AlarmReceiver { *; }
-keep class com.elendal.gpsalarmclock.BootReceiver { *; }
-keep class com.elendal.gpsalarmclock.DismissAlarmReceiver { *; }
```

Build with minification enabled and run through the full test suite before submitting.

### Fixed state
`bundleRelease` output is ~40% smaller. Class names are obfuscated. Pre-launch report
shows no ProGuard warnings.

---

## 9. LOW — Play Store Listing Assets (not in code, but required for submission)

These are not code changes but are required to complete submission in Play Console:

| Asset | Requirement |
|---|---|
| App icon | 512×512 PNG, ≤1MB |
| Feature graphic | 1024×500 PNG/JPG |
| Screenshots | Min 2, phone screenshots (1080×1920 or similar) |
| Short description | ≤80 characters |
| Full description | ≤4000 characters |
| Privacy policy URL | Publicly accessible, HTTP 200 |
| Content rating | Complete the questionnaire in Play Console |
| Target audience | Specify age group (Everyone) |

Take screenshots from the running emulator:
```bash
adb exec-out screencap -p > /tmp/screenshot1.png
```

---

## Summary Checklist

```
[x] 1. Generate keystore, add signing config to build.gradle.kts  (stub done; keystore is manual)
[x] 2. Create mipmap-*/ icon directories with correct icon ref in manifest  (placeholder done; final art is manual)
[ ] 3. Write and host privacy policy, link in Play Console  (manual — requires a public URL)
[x] 4. Fix backup config: allowBackup=false
[x] 5. Fix AlarmReceiver export: exported=false  (verified safe via explicit intent)
[x] 6. Add location to AlarmService foregroundServiceType
[x] 7. Add background location disclosure screen (PrivacyDisclosureActivity)  (wired; UI fix applied 2026-03-08)
[x] 8. Enable isMinifyEnabled=true + isShrinkResources=true, add proguard-rules.pro keeps
[ ] 9. Collect store listing assets and upload to Play Console  (manual)
```
