# Android App Development Guide

A step-by-step guide to building and deploying Android apps from the command line on macOS.

---

## Prerequisites

### 1. Install Android Studio
```bash
brew install --cask android-studio
```

After installation, **open Android Studio once** to complete the setup wizard. This downloads:
- Android SDK
- Build tools
- Platform tools (including `adb`)

### 2. Verify Environment
```bash
# Check Java (bundled with Android Studio)
/Applications/Android\ Studio.app/Contents/jbr/Contents/Home/bin/java -version

# Check ADB
~/Library/Android/sdk/platform-tools/adb --version
```

---

## Creating a New Project

### Option A: Create via Android Studio
1. Open Android Studio → New Project → Empty Activity
2. Choose Kotlin + Jetpack Compose
3. Set minimum SDK to 26 (Android 8.0)

### Option B: Create Manually (CLI)

#### 1. Create directory structure
```bash
mkdir -p my-app/app/src/main/java/com/example/myapp
mkdir -p my-app/app/src/main/res/values
mkdir -p my-app/app/src/main/res/xml
cd my-app
```

#### 2. Create `settings.gradle`
```groovy
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MyApp"
include ':app'
```

#### 3. Create `build.gradle` (root)
```groovy
plugins {
    id 'com.android.application' version '8.2.2' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.22' apply false
}
```

#### 4. Create `gradle.properties`
```properties
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```

#### 5. Create `app/build.gradle`
```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.example.myapp'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.myapp"
        minSdk 26
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = '17'
    }
    
    buildFeatures {
        compose true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion '1.5.8'
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.activity:activity-compose:1.8.1'
    implementation platform('androidx.compose:compose-bom:2023.08.00')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.material3:material3'
}
```

#### 6. Create `app/src/main/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:theme="@style/Theme.MyApp">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

#### 7. Create `app/src/main/res/values/strings.xml`
```xml
<resources>
    <string name="app_name">My App</string>
</resources>
```

#### 8. Create `app/src/main/res/values/themes.xml`
```xml
<resources>
    <style name="Theme.MyApp" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

#### 9. Create `app/src/main/java/com/example/myapp/MainActivity.kt`
```kotlin
package com.example.myapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Greeting("World")
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}
```

---

## Building the App

### Set JAVA_HOME
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

### Generate Gradle Wrapper (if not present)
Open the project in Android Studio once, or sync Gradle — this creates `gradlew`.

### Build Debug APK
```bash
./gradlew assembleDebug
```

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

---

## Deploying to Device

### Option A: USB Debugging

1. **Enable Developer Options on phone:**
   - Settings → About phone → Tap "Build number" 7 times

2. **Enable USB Debugging:**
   - Settings → System → Developer options → USB debugging → ON

3. **Connect via USB and verify:**
   ```bash
   ~/Library/Android/sdk/platform-tools/adb devices
   ```

4. **Install APK:**
   ```bash
   ~/Library/Android/sdk/platform-tools/adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Option B: Wireless Debugging (Android 11+)

1. **Enable Wireless Debugging:**
   - Settings → System → Developer options → Wireless debugging → ON

2. **Pair via QR Code (easiest):**
   - Open Android Studio → Device Manager → Pair using Wi-Fi → QR code
   - On phone: Wireless debugging → Pair with QR code → Scan

3. **Or pair via command line:**
   ```bash
   # Get pairing code and IP:port from phone
   ~/Library/Android/sdk/platform-tools/adb pair <IP:PORT> <PAIRING_CODE>
   
   # Then connect
   ~/Library/Android/sdk/platform-tools/adb connect <IP:PORT>
   ```

4. **Install APK:**
   ```bash
   ~/Library/Android/sdk/platform-tools/adb install app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Useful Commands

```bash
# Rebuild and reinstall
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# View device logs
adb logcat | grep "YourTag"

# Uninstall app
adb uninstall com.example.myapp

# List connected devices
adb devices

# Restart ADB server
adb kill-server && adb start-server
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `Unable to locate Java Runtime` | Set `JAVA_HOME` to Android Studio's bundled JDK |
| `android.useAndroidX` error | Add `android.useAndroidX=true` to `gradle.properties` |
| Device not detected via USB | Try a different cable (some are charge-only) |
| Wireless pairing fails | Ensure phone and Mac are on the same WiFi subnet |
| `QUERY_ALL_PACKAGES` needed | Add permission to manifest for Android 11+ app listing |

---

## Quick Start Template

For future projects, copy this minimal setup:
1. `settings.gradle`
2. `build.gradle` (root)
3. `gradle.properties`
4. `app/build.gradle`
5. `app/src/main/AndroidManifest.xml`
6. `app/src/main/res/values/strings.xml`
7. `app/src/main/res/values/themes.xml`
8. `app/src/main/java/.../MainActivity.kt`

Then run:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```
