# Android Dev Environment Setup

## System state at start
- OS: Fedora Linux (kernel 6.18.4)
- JDK: OpenJDK 25 (system default — **too new for AGP, not used**)
- No Android SDK, no Gradle, no adb

---

## Step 1: Install JDK 17

AGP 8.x requires JDK 17 or 21. JDK 25 breaks things. Installed via Homebrew (no sudo needed):

```bash
brew install openjdk@17
```

Result: `/home/linuxbrew/.linuxbrew/opt/openjdk@17/bin/java` — OpenJDK 17.0.18

---

## Step 2: Download Android Command Line Tools

```bash
mkdir -p ~/Android/cmdline-tools
cd ~/Android
wget "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -O cmdline-tools.zip
unzip -q cmdline-tools.zip -d cmdline-tools-tmp
mkdir -p cmdline-tools/latest
mv cmdline-tools-tmp/cmdline-tools/* cmdline-tools/latest/
rm -rf cmdline-tools-tmp cmdline-tools.zip
```

The `latest/` subdirectory is **required** — sdkmanager won't work without it.

---

## Step 3: Accept SDK licenses

```bash
ANDROID_HOME="/home/elendal/Android" \
JAVA_HOME="/home/linuxbrew/.linuxbrew/opt/openjdk@17" \
  printf 'y\ny\ny\ny\ny\ny\ny\ny\ny\ny\n' | \
  /home/elendal/Android/cmdline-tools/latest/bin/sdkmanager --licenses
```

Accepted 7 licenses.

---

## Step 4: Install Android SDK components

```bash
ANDROID_HOME="/home/elendal/Android" \
JAVA_HOME="/home/linuxbrew/.linuxbrew/opt/openjdk@17" \
  /home/elendal/Android/cmdline-tools/latest/bin/sdkmanager \
    "platform-tools" \
    "platforms;android-35" \
    "build-tools;35.0.0" \
    "emulator" \
    "system-images;android-35;google_apis;x86_64"
```

Installed:
| Component | Version |
|---|---|
| platform-tools | 37.0.0 |
| platforms;android-35 | r02 |
| build-tools;35.0.0 | 35.0.0 |
| emulator | latest |
| system-images;android-35;google_apis;x86_64 | r09 |

---

## Step 5: Create Android Virtual Device (AVD)

```bash
ANDROID_HOME="/home/elendal/Android" \
JAVA_HOME="/home/linuxbrew/.linuxbrew/opt/openjdk@17" \
  /home/elendal/Android/cmdline-tools/latest/bin/avdmanager create avd \
    -n "Pixel6_API35" \
    -k "system-images;android-35;google_apis;x86_64" \
    -d "pixel_6"
```

AVD name: `Pixel6_API35`, API 35, Google APIs, x86_64.

---

## Step 6: Set environment variables

Appended to `~/.bashrc`:

```bash
export ANDROID_HOME="/home/elendal/Android"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME="/home/linuxbrew/.linuxbrew/opt/openjdk@17"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

---

## Step 7: Scaffold the Android project

Used `gradle wrapper` (after `brew install gradle`) to generate the Gradle wrapper JAR, then created all project files:

```
gpsAlarmClock/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── local.properties                  (sdk.dir=/home/elendal/Android)
├── gradle/
│   ├── libs.versions.toml            (version catalog)
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties (Gradle 8.9-bin)
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── kotlin/com/elendal/gpsalarmclock/
        │   ├── MainActivity.kt
        │   ├── GeofenceBroadcastReceiver.kt
        │   └── AlarmService.kt
        └── res/
            ├── layout/activity_main.xml
            ├── values/strings.xml
            ├── values/themes.xml
            ├── values/colors.xml
            └── drawable/ic_launcher_background.xml
```

Key versions:
| Tool | Version |
|---|---|
| AGP | 8.7.0 |
| Kotlin | 2.0.21 |
| Gradle wrapper | 8.9 |
| Min SDK | 26 |
| Target/Compile SDK | 35 |

Dependencies: play-services-location 21.3.0, Room, Lifecycle KTX, Coroutines, Material3, ConstraintLayout.

Permissions in manifest: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `RECEIVE_BOOT_COMPLETED`, `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM`.

---

## Step 8: Verify build

```bash
cd /home/elendal/IdeaProjects/gpsAlarmClock
ANDROID_HOME=/home/elendal/Android \
JAVA_HOME=/home/linuxbrew/.linuxbrew/opt/openjdk@17 \
  ./gradlew assembleDebug
```

Result: **BUILD SUCCESSFUL in 20s**
APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## Common commands

```bash
# Start emulator
$ANDROID_HOME/emulator/emulator -avd Pixel6_API35 -no-snapshot &

# Build debug APK
./gradlew assembleDebug

# Install on running emulator
./gradlew installDebug

# Launch app
adb shell am start -n com.elendal.gpsalarmclock/.MainActivity

# View logs
adb logcat -s gpsAlarmClock

# List AVDs
avdmanager list avd

# Install new SDK components
sdkmanager "extras;google;google_play_services"
```
