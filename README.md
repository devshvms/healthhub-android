# HealthHub Android

A native Android app that reads health data from **Google Health Connect** and syncs it to **Firebase Firestore** for cloud storage and cross-device access. Features a live dashboard with charts for Steps, Heart Rate, Sleep, and Stress over a rolling 24-hour window.

---

## Screenshots

> Add screenshots here after first run

---

## Features

- 📊 **Live Dashboard** — 4 metrics displayed as Vico charts with a 24-hour X-axis and 4-hour time labels
- 🏃 **Health Connect Integration** — Reads Steps, Heart Rate, Sleep sessions, and Stress levels
- ☁️ **Firebase Firestore Sync** — Delta sync (high water mark) so only new records are pushed each time
- 🔄 **Pull-to-refresh** — Up-sync and down-sync are independent; a failed write never blocks a fetch
- 🔁 **Retry Logic** — Network calls retry automatically with timeout on transient failures
- 🔐 **Google Sign-In** — Auth via Firebase Authentication with Google provider
- 🎨 **Material 3 UI** — Dark-mode ready, horizontal metric selector, smooth Crossfade transitions

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.3 |
| UI | Jetpack Compose + Material 3 |
| Navigation | Jetpack Navigation 3 |
| Charts | [Vico 1.15.0](https://patrykandpatrick.com/vico/) |
| Health data | Health Connect 1.1.0-alpha07 |
| Cloud DB | Firebase Firestore 33.x |
| Auth | Firebase Auth + Google Sign-In |
| Architecture | MVVM — `ViewModel` + `StateFlow` |
| Build | Gradle 9.2 + Version Catalog |

---

## Requirements

| Requirement | Version |
|---|---|
| Android Studio | Hedgehog or newer |
| JDK | 17 |
| Android SDK | API 36 (compile) / API 26+ (run) |
| Device / Emulator | Android 9+ (API 26+) with Health Connect installed |
| Firebase project | Required (see setup below) |

> ⚠️ Health Connect is **not available on emulators** — use a physical device for full functionality.

---

## Setup

### 1. Clone the repo

```bash
git clone https://github.com/devshvms/healthhub-android.git
cd healthhub-android
```

### 2. Create a Firebase project

1. Go to [console.firebase.google.com](https://console.firebase.google.com) and create a new project.
2. Add an **Android app** with package name `com.example.healthhub`.
3. Download `google-services.json` and place it at:
   ```
   app/google-services.json
   ```
   > 📄 See `app/google-services.json.example` for the expected structure.
4. In the Firebase Console, enable:
   - **Authentication** → Sign-in method → **Google**
   - **Firestore Database** → Create in production or test mode

### 3. Configure local SDK path

Android Studio usually generates this automatically. If not, create `local.properties` in the project root:

```properties
sdk.dir=/Users/YOUR_USERNAME/Library/Android/sdk
```

### 4. Build & run

```bash
./gradlew assembleDebug
```

Or open the project in **Android Studio** and click ▶ Run.

---

## Firestore Data Model

Each authenticated user gets their own document tree under `/users/{userId}/`.

```
users/
  {userId}/
    steps/
      {docId}  →  { startTime, endTime, count, sync_time }
    heartRate/
      {docId}  →  { time, beatsPerMinute, sync_time }
    sleep/
      {docId}  →  { startTime, endTime, durationHours, sync_time }
    stress/
      {docId}  →  { time, level, sync_time }
```

- `sync_time` — ISO-8601 timestamp of when the record was written to Firestore.
- Delta sync uses `max(endTime)` / `max(time)` per collection as a high water mark so duplicate records are never uploaded.

---

## Architecture

```
MainActivity
  └── NavHost (Navigation 3)
        ├── MainScreen  ← Google Sign-In
        └── DashboardScreen
              └── HealthViewModel
                    ├── HealthConnectManager  ← reads from Health Connect
                    └── FirestoreManager      ← reads/writes Firestore
```

- **`HealthViewModel`** — orchestrates sync, exposes `StateFlow` to the UI.
- **`FirestoreManager`** — wraps Firestore calls; implements delta sync and retry-with-timeout.
- **`HealthConnectManager`** — wraps Health Connect SDK calls.
- **`DashboardScreen`** — Compose UI; all chart data is computed inside `remember` blocks to avoid recomposition side-effects.

---

## Sync Strategy

**Up-sync (device → cloud):**
1. Query local Health Connect for each metric.
2. Query Firestore for the latest `endTime` / `time` per collection (high water mark).
3. Push only records newer than the high water mark in batches.

**Down-sync (cloud → device):**
- Runs in the `finally` block of every sync — always executes even if up-sync fails.
- Fetches the last 24 hours of data per collection from Firestore.

**Error handling:**
- Each sync direction is wrapped in its own `try/catch`.
- A Toast is shown for both up-sync and down-sync failures.
- Network calls use `retryWithTimeout` for transient errors.

---

## Known Limitations

- Health Connect requires a **physical device** (no emulator support).
- Sleep and Stress data availability depends on connected wearables / apps writing to Health Connect.
- Firestore offline support is not explicitly enabled; sync relies on network connectivity.

---

## Contributing

1. Fork the repo.
2. Create a feature branch: `git checkout -b feat/your-feature`.
3. Commit your changes: `git commit -m 'feat: add your feature'`.
4. Push and open a Pull Request.

> ⚠️ Never commit `google-services.json` or any API keys. The `.gitignore` blocks this file but please double-check before pushing.

---

## License

This project is for personal / educational use. No license is currently specified.
