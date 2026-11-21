# D'oro Inventory

An offline inventory app for Android tablets, built for the D'oro bistro kitchen. It tracks stock levels, deliveries, usage and waste, warns when items run low or are close to expiring, **predicts when each item will run out and when to order it**, and builds a reorder list grouped by supplier.

All data stays on the tablet. The app needs no internet connection, account or server.

---

## Contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [How the data is stored](#how-the-data-is-stored)
- [Project structure](#project-structure)
- [Requirements](#requirements)
- [Building the app](#building-the-app)
- [Installing on a tablet](#installing-on-a-tablet)
- [Using the app](#using-the-app)
- [Backup and restore](#backup-and-restore)
- [Troubleshooting](#troubleshooting)

---

## Features

The navigation sidebar groups the screens into **Overview**, **Stock**, **Planning** and **Manage**. On wide tablets it shows labels; on smaller screens it collapses to an icon rail.

| Screen | What it does |
|---|---|
| **Dashboard** | Stock value, items to order, items forecast to run out within 7 days, items expiring within 3 days and waste in the last 7 days. Below that: what's predicted to run out, what needs attention, and recent activity. Every tile opens the related screen. |
| **Inventory** | All items with search, category filters and sorting (by name, by what needs attention, or by days of stock left). Each item shows a stock-level bar (against par, with a tick at the minimum) and how many days of stock are left. Large − / + buttons adjust stock quickly, with **Undo** in case of a mis-tap. On a tablet, the selected item's details, stock forecast chart and history show next to the list. |
| **Stock count** | Stock-taking: enter what's actually on the shelf for each item, with a progress bar, then save all the differences at once. |
| **History** | A log of every stock change (delivery, usage, waste, count), grouped by day and filterable by type. |
| **Forecast** | **Predictive replenishment.** For every item: average daily usage and its trend, predicted stock-out date, the date to order by (based on the supplier's lead time) and a suggested order quantity. Tap an item for a 14-day history + 14-day projection chart and a plain-language explanation of the numbers. |
| **Reorder** | Everything that needs ordering, grouped by supplier: items below their minimum **and** items the forecast says will run short before the next delivery. Shows why each item is listed, the suggested quantity and estimated cost. Orders can be shared through any messaging or email app. Tap **Received** to record the delivery. |
| **Insights** | Cost and waste report for the last 7, 14 or 30 days: value of stock used, waste and waste rate (with change vs. the previous period), deliveries, and shortfalls found in stock counts. Includes a daily chart, the most-wasted items, the biggest food costs, and **smart suggestions** such as lowering a par level for an item that's often thrown away, or raising one when demand is rising. |
| **Suppliers** | Supplier names, contact person, phone, email, notes and **lead time** (days from ordering to delivery), linked to their items. |
| **Backup** | Export all data to a JSON file, or restore from one. |

Each item has these fields:

- name, category, unit (kg, L, pcs, box, …)
- current quantity
- **minimum level**: when stock falls to this level the item is marked *Low*
- **par level**: the target level the reorder list tops the item up to
- cost per unit, supplier, expiry date, notes

Stock quantities only change through logged movements, so every change has a record in the history.

---

## Predictive replenishment

The forecast learns from each item's own history and runs entirely on the tablet. It is recalculated whenever stock, suppliers or history change.

1. **Daily usage.** Everything that left the shelf over the last 28 complete days: usage, waste, and shortfalls found during stock counts (so kitchens that only do counts still get forecasts). When there are at least two weeks of history, the last 7 days count for 60%, so busier or quieter weeks show up quickly. The **trend** compares the last 7 days with the weeks before.
2. **Runs out** = stock on hand ÷ daily usage.
3. **Order by** = the day stock falls to the *reorder point*: enough to last through the supplier's lead time plus a 2-day safety buffer.
4. **Suggested order** = enough for the lead time + the next 7 days + the safety buffer, minus what will still be on the shelf on the order-by date. If an item is below its minimum, the par-level suggestion is used when it's larger.

| Status | Meaning |
|---|---|
| **Order now** | Below minimum, or the order-by date is today (or has passed). |
| **Order soon** | Order within the next 3 days. |
| **Healthy** | Enough stock to get past the next delivery. |
| **Learning** | Fewer than 3 days of history. Record usage (the − button), waste or stock counts and it will start forecasting. |
| **No recent usage** | Nothing used in the last 28 days. |

To get good forecasts, set each supplier's **lead time** and record usage regularly (the − button or **Use**), or do regular stock counts. The constants (28-day window, 7-day cover, 2-day safety buffer) are in `data/Forecast.kt`.

---

## Tech stack

| Part | Technology |
|---|---|
| Platform | Android (native app) |
| Language | [Kotlin](https://kotlinlang.org/) 2.2 |
| UI | [Jetpack Compose](https://developer.android.com/compose) with Material 3, custom navy/slate theme |
| Database | [Room](https://developer.android.com/training/data-storage/room) 2.7 (SQLite) |
| Code generation | KSP (generates Room's database code) |
| State | AndroidViewModel + Kotlin Flow / StateFlow |
| Build | Gradle 8.14 (wrapper included), Android Gradle Plugin 8.11 |
| Java | JDK 17 |
| Android versions | Minimum Android 8.0 (API 26), target Android 16 (API 36) |

The app has no network, analytics or third-party services. Apart from AndroidX/Jetpack, it uses no other libraries.

---

## How the data is stored

The data is kept in a **SQLite database** on the tablet, managed by Room:

```
/data/data/com.dorobistro.inventory/databases/doro_inventory.db
```

The file is private to the app, so it doesn't appear in the tablet's file manager. To get a copy, use **Backup → Export** in the app.

### Tables

**`suppliers`**

| Column | Type | Notes |
|---|---|---|
| id | INTEGER | primary key |
| name | TEXT | |
| contactName, phone, email, notes | TEXT | |
| leadTimeDays | INTEGER | days from order to delivery, default 2 (added in database version 2) |

**`items`**

| Column | Type | Notes |
|---|---|---|
| id | INTEGER | primary key |
| name, category, unit | TEXT | |
| quantity | REAL | current stock |
| minQuantity | REAL | low-stock threshold |
| parLevel | REAL | reorder target |
| costPerUnit | REAL | |
| supplierId | INTEGER | → `suppliers.id`, set to NULL if the supplier is deleted |
| expiryEpochDay | INTEGER | days since 1970-01-01, nullable |
| notes | TEXT | |
| updatedAt | INTEGER | Unix time in ms |

**`movements`** (stock history)

| Column | Type | Notes |
|---|---|---|
| id | INTEGER | primary key |
| itemId | INTEGER | → `items.id`, deleted together with the item |
| type | TEXT | `DELIVERY`, `USAGE`, `WASTE`, `COUNT`, `ADJUSTMENT` |
| change | REAL | signed amount applied (+ in, − out) |
| quantityAfter | REAL | stock level after the change |
| note | TEXT | |
| timestamp | INTEGER | Unix time in ms |

Each stock change runs in a single database transaction that updates the item and adds the movement together, so the two can't get out of sync. **Undo** only reverses an item's most recent movement, for the same reason.

### Upgrading from version 1

The database is now at version 2. When you install this version over the old one, a migration adds the `leadTimeDays` column to suppliers (default 2 days). **Your existing items, suppliers and history are kept.** Backups from version 1 can still be restored; their suppliers get the default lead time.

---

## Project structure

```
app/src/main/java/com/dorobistro/inventory/
├── MainActivity.kt            App entry point
├── data/
│   ├── Entities.kt            Item, Supplier, StockMovement + low-stock / expiry rules
│   ├── Forecast.kt            Predictive replenishment (usage rate, stock-out, order-by, quantity)
│   ├── Insights.kt            Cost & waste report and smart suggestions
│   ├── SampleData.kt          Sample items with four weeks of realistic history
│   ├── InventoryDao.kt        Database queries, stock-change transactions, undo
│   ├── AppDatabase.kt         Room database setup and migration 1 → 2
│   └── Backup.kt              JSON export / import
└── ui/
    ├── App.kt                 Sidebar navigation and screen switching
    ├── InventoryViewModel.kt  Connects the screens to the database and forecast
    ├── Components.kt          Shared UI pieces (cards, badges, stock bars, KPI tiles) and formatting
    ├── DashboardScreen.kt
    ├── InventoryScreen.kt     List, detail pane with forecast chart, add/edit and adjust dialogs
    ├── CountScreen.kt
    ├── HistoryScreen.kt
    ├── ForecastScreen.kt      Predictive replenishment table, detail dialog and projection chart
    ├── ReorderScreen.kt
    ├── InsightsScreen.kt      Cost & waste insights
    ├── SuppliersScreen.kt
    ├── BackupScreen.kt
    └── theme/Theme.kt         Navy & slate theme with gold accent, status colours (light and dark)

app/src/test/java/com/dorobistro/inventory/data/
└── ForecastTest.kt            Unit tests for the forecast and sample data
```

---

## Requirements

**To build the app (on a computer):**

- JDK 17
- Android SDK with **Platform 36** and **Build-Tools**, either from [Android Studio](https://developer.android.com/studio) or from the [command-line tools](https://developer.android.com/studio#command-line-tools-only)
- Git

You don't need to install Gradle. The included `gradlew` wrapper downloads the right version.

**To run the app:**

- An Android tablet (or phone) running **Android 8.0 or newer**
- The app is designed for tablets in either orientation. It works on phones, but the layout is made for larger screens.

---

## Building the app

### Option 1: Android Studio

1. Clone the repository:
   ```sh
   git clone https://github.com/ART-MAKER-blip/doro-inventory.git
   ```
2. In Android Studio, choose **File → Open** and select the `doro-inventory` folder.
3. Wait for the Gradle sync to finish, then click **Run ▶** with a tablet connected or an emulator running.

### Option 2: command line

1. Clone the repository:
   ```sh
   git clone https://github.com/ART-MAKER-blip/doro-inventory.git
   cd doro-inventory
   ```
2. Tell Gradle where the Android SDK is. Either set an environment variable:
   ```sh
   export ANDROID_HOME=$HOME/Android/Sdk
   ```
   or create a file called `local.properties` in the project folder:
   ```
   sdk.dir=/home/<you>/Android/Sdk
   ```
3. Build the app:
   ```sh
   ./gradlew assembleDebug        # on Windows: gradlew.bat assembleDebug
   ```
   The APK is written to:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Installing on a tablet

### 1. Turn on developer mode on the tablet

1. Open **Settings → About tablet** and tap **Build number** (on Xiaomi: **OS version**) seven times.
2. Go back to **Settings → (Additional settings →) Developer options** and turn on **USB debugging**.
3. Connect the tablet with a USB cable and tap **Allow** when it asks to trust the computer.

### 2a. Install over USB

```sh
./gradlew installDebug
```

Watch the tablet screen. Some devices show a confirmation prompt that has to be accepted within a few seconds.

### 2b. Install from the APK file

Use this if 2a doesn't work.

1. Copy `app-debug.apk` to the tablet, for example into **Download**, by USB file transfer, email or cloud storage. With adb:
   ```sh
   adb push app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/DoroInventory.apk
   ```
2. On the tablet, open the **File Manager**, go to **Download** (or the **APKs** category) and tap the file.
3. If asked, allow the file manager to **install unknown apps**, then tap **Install**.

### Xiaomi / Redmi / POCO (HyperOS, MIUI)

These devices block USB installs by default. You'll see the error `INSTALL_FAILED_USER_RESTRICTED`. To allow them:

- **Developer options → Install via USB**: turn it on. This requires signing in to a Xiaomi account, and sometimes a SIM card or mobile data.
- Or use **2b** above, which always works.

When the app is installed you can unplug the cable. It runs entirely on the tablet.

### Updating

Build and install again the same way. The data on the tablet is kept. Uninstalling the app **deletes all its data**, so export a backup first.

---

## Using the app

1. **First run:** the dashboard offers **Load sample data** so you can explore the app with example items and four weeks of history (so the forecast and insights have data). To start fresh instead, go to **Inventory → Add item**.
2. **Add suppliers** under **Suppliers**, including how many days they take to deliver (lead time), then link items to them when adding or editing an item.
3. **Set min and par levels** on each item. For example, tomatoes with min 5 kg and par 15 kg are marked *Low* at 5 kg, and the reorder list suggests ordering back up to 15 kg.
4. **Day to day:**
   - Tap **−** / **+** on an item for quick one-unit changes.
   - Tap an item, then **Receive**, **Use**, **Waste** or **Count** to record larger amounts with a note.
5. **Planning:** open **Forecast** to see what will run out and when to order it. Tap an item for its chart and an explanation.
6. **Ordering:** open **Reorder**, share each supplier's list, and tap **Received** when the delivery arrives.
7. **Waste:** check **Insights** weekly for waste costs and suggestions.
8. **Stock-taking:** open **Stock count**, walk the shelves entering actual quantities, then tap **Save count**.

### Running the tests

The forecast logic has unit tests. In Android Studio, right-click `app/src/test` and choose **Run 'Tests in…'**, or from the command line:

```sh
./gradlew testDebugUnitTest
```

---

## Backup and restore

Your data exists **only on this tablet**. Android's automatic cloud backup is turned off for this app. If the tablet is lost, reset, or the app is uninstalled, the data is gone unless you've exported it.

- **Export:** **Backup → Export** saves a file like `doro-inventory-2026-11-18.json` containing all items, suppliers and the full history. Keep it somewhere safe, such as a USB drive, email or cloud folder.
- **Restore:** **Backup → Restore** loads a backup file. This **replaces** everything currently on the tablet.

A backup file can also be used to move the data to a new tablet.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `SDK location not found` | Set `ANDROID_HOME` or create `local.properties` (see [Building](#building-the-app)). |
| `Unsupported class file major version` / Java errors | Use JDK 17: `java -version` should show 17. |
| `INSTALL_FAILED_USER_RESTRICTED` | Xiaomi/HyperOS: turn on **Install via USB**, or install from the APK file. |
| `adb: no devices found` | Check the cable, turn on USB debugging, and accept the prompt on the tablet. |
| Installer opens and closes immediately | Install by tapping the APK in the tablet's file manager instead. |
| App installed but not visible | Look in the app drawer for **D'oro Inventory** (gold box icon). |
