# D'oro Inventory

An offline inventory app for Android tablets, built for a bistro kitchen. All data is stored on the device.

## Features

- **Dashboard**: item count, low/out of stock, items expiring within 3 days, stock value, recent activity
- **Inventory**: search, category filters, quick +/− buttons, list and detail side by side on tablets
- **Stock movements**: deliveries, usage, waste and count corrections, each one logged
- **Stock count**: walk-through count that saves all the differences at once
- **Reorder list**: low items grouped by supplier, with suggested order quantities and costs, shareable as text
- **Suppliers**: contacts linked to items
- **Backup**: export to and restore from a JSON file

## Tech

Kotlin, Jetpack Compose (Material 3), Room (SQLite). minSdk 26, targetSdk 36.

## Build

Requires JDK 17 and the Android SDK (platform 36).

```sh
./gradlew assembleDebug    # APK in app/build/outputs/apk/debug/
./gradlew installDebug     # install on a connected device
```

On Xiaomi/HyperOS devices, turn on **Developer options → Install via USB**, or copy the APK to the device and install it from the file manager.
