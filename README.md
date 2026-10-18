# Truck Sales – offline Android app

Daily truck-wise sales for 25 products and 5 trucks on village routes. Everything is stored on the
phone in a local SQLite database. No internet, cloud, server or paid service is used.

## Using the app

The date at the top applies to every screen. Tap it to pick a date, or use ◀ ▶ / Today.

| Tab | What it does |
|---|---|
| **Dashboard** | KPI cards (day sales ₹, month sales ₹, day qty, month qty), truck performance table, daily sales graph for the month, monthly graph for the last 12 months, truck-wise comparison graph. |
| **Entry** | **Morning:** pick a truck, enter **Opening** stock, tap **Save**. **Evening:** when the truck returns, open the same truck and date, enter **Closing** stock, tap **Save**. **Sold = Opening − Closing** and the value (Sold × day's rate) are calculated then. Truck buttons turn **orange** while closing is pending and **green** when completed. Opening is pre-filled from that truck's last closing stock. Leave a product blank if the truck did not carry it. |
| **Reports** | Choose From/To dates: summary, date-wise list (tap a date to see every truck and product with the full calculation), truck-wise and product-wise totals. **Export this period to Excel (CSV)** saves a detailed file that opens in Excel / Google Sheets. |
| **Settings** (PIN, default **1234**) | Revise rates and product names, edit truck names and village routes, **Backup now / Restore from backup**, change the PIN. |

### How rates work
- Rates are saved **effective from the date shown at the top**. To revise today's rates, open Settings
  with today's date, change the rates, and tap **Save Rates**.
- A date uses the most recent rate set on or before that date, so changing today's rates never affects
  earlier days.
- Every saved sale stores the rate and value used at that moment, so reports for past dates always show
  the original rates. If a rate was set wrong for a day, correct it in Settings for that date and then
  open that day's entry and tap **Save** again to recalculate it.

**Change the PIN on first use** (Settings → Change PIN).

## Build the APK

### Option A – free cloud build on GitHub (no software to install)
1. Create a free GitHub account and a new **private** repository.
2. Upload all files from this folder (keep the folder structure, including `.github/` and `app/release.jks`).
3. Open the repository's **Actions** tab. The *Build APK* job starts automatically (or click *Run workflow*).
4. When it finishes (about 3–5 minutes) open the run and download **TruckSales-APK**. Unzip it to get
   `TruckSales.apk`.

### Option B – Android Studio on a computer
1. Install Android Studio (free) and open this folder.
2. Let it sync, then choose **Build → Generate App Bundles or APKs → Build APK(s)** or run in a terminal:
   `./gradlew assembleRelease` (Windows: `gradlew.bat assembleRelease`).
3. The APK is at `app/build/outputs/apk/release/app-release.apk`.

Requirements: JDK 17+, Android SDK platform 35 (Android Studio installs these automatically).

## Install on the phone
1. Copy `TruckSales.apk` to the phone (WhatsApp to yourself, USB cable, or Google Drive).
2. Open it. If asked, allow **Install unknown apps** for the app you opened it with.
3. Tap **Install**. Works on Android 5.0 and newer.

Sales of a truck count in the dashboard and reports only after its closing stock is entered. The
dashboard lists trucks whose closing is still pending.

### Backup (do this weekly)
- **Settings → Backup now** saves all data to one file. In the picker choose **Google Drive** (or any
  other cloud/storage app on the phone) to keep a copy off the phone, or Downloads / SD card.
- **Settings → Restore from backup** puts that data back, e.g. on a new phone after installing the app.
- The dashboard shows a reminder when the last backup is more than 7 days old.
- Android's own phone backup (Settings → Google → Backup) also includes this app's data automatically
  when it is switched on, but don't rely on it alone.

### Keeping data safe
- Data stays on the phone after closing the app or restarting the phone.
- **Updating:** install a newer APK over the old one – data is kept. All builds are signed with the
  same key (`app/release.jks`), which is what makes updates keep data. Do not lose or replace that file.
- **Uninstalling the app or "Clear data" deletes all records.** Don't do this unless you mean to.

## Project layout
```
app/src/main/java/com/retail/trucksales/
  MainActivity.java     date bar, tabs, PIN
  DashboardScreen.java  KPIs and graphs
  EntryScreen.java      daily truck stock entry
  ReportScreen.java     date-range reports
  SettingsScreen.java   rates, trucks, PIN
  Db.java               SQLite tables and queries
  Calc.java             sold / value / date calculations
  ChartView.java        bar chart (drawn directly, no library)
  Ui.java, Screen.java  small UI helpers
test/
  CalcTest.java         calculation tests (plain Java)
  test_db.py            database tests using the app's exact SQL
```
No third-party libraries are used, which keeps the APK very small (well under 1 MB).

### Running the tests
```
javac -d out app/src/main/java/com/retail/trucksales/Calc.java test/CalcTest.java && java -cp out com.retail.trucksales.CalcTest
python3 test/test_db.py
```
