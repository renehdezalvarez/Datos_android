# AGENTS.md

## Project

Single-module Android app (Kotlin + Jetpack Compose) that forecasts mobile-data usage for two fixed lines. Product behavior and screenshots are documented in `README.md`; use it before changing UX. UI copy and most comments are Spanish (Cuban carrier context), so keep new user-facing text in Spanish.

- Gradle module: `:app` only.
- Namespace is `com.example`, but the installed `applicationId` is `com.aistudio.previsiongastos.pkwqzl`. Put new code under `com.example` (imports depend on it).
- Layers: `domain/` is pure Kotlin (no Android), `data/` is Room + repository, `ui/` is Compose, `ussd/` holds the telephony API wrapper plus the accessibility fallback, and `notifications/` holds the expiry worker.

## Commands

```bash
./gradlew testDebugUnitTest
./gradlew testDebugUnitTest --tests "com.example.UssdParserTest"
./gradlew assembleDebug
./gradlew assembleRelease
```

- First run downloads Gradle 9.3.1 via the wrapper. CI uses JDK 21 and Android SDK API 36; README says JDK 17+.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`; release APK: `app/build/outputs/apk/release/app-release.apk`.
- `assembleRelease` requires `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` (CI injects them). Local debug builds reference a gitignored `debug.keystore` at the repo root; create a standard Android debug keystore there if signing fails.
- There is no committed instrumentation test source set, formatter, or static-analysis config. The only automated tests are the JUnit tests in `app/src/test/`.

## Architecture notes

- Manual dependency injection: `MainActivity` creates `AppDatabase`, `DataPlanRepository`, and the `DataForecastViewModel` factory, then schedules `PrepaidExpiryWorker`. There is no Hilt/Koin; update this wiring when adding dependencies.
- Two fixed rows in `data_plan`: `id = 1` is the monthly calendar line, `id = 2` is the prepaid 35-day line. Do not treat them as arbitrary user-created plans.
- Cycle-day semantics are test-critical:
  - Line 1's length is always the real current calendar month (28/29/30/31 days); `customCurrentDay` is only a correction/offset from "Ajustar Días".
  - Line 2's day flows from `purchaseTimestamp`; `customCurrentDay` must not freeze it.
  - `daysRemaining` includes today, and day 1 is the purchase day (`total - elapsed + 1`), matching carrier wording such as "válidos 14 días". Tests assert this; do not "fix" it to exclusive counting.
- Data quantities are MB internally and convert at 1024 MB/GB (not 1000). Package totals are clamped to at least 100 MB.
- Room: database version 6 with `exportSchema = false` intentionally. For entity changes, bump the version and add a migration in `AppDatabase.kt`; do not re-enable schema export or remove `fallbackToDestructiveMigration()` without checking CI.

## USSD capture

- Automatic balance reading prefers the official telephony API via `UssdSystemApi` (`TelephonyManager.sendUssdRequest`, Android 8+). It returns the carrier text directly, with a 20-second timeout.
- When the API is unavailable, throws, times out, or returns unparseable text, the app arms the accessibility catcher and opens the dialer as fallback. The user can always paste the response manually.
- `UssdCatcherService` only scans windows while `UssdCaptureHolder.consultationActive` is true; that gating is a privacy guarantee, not an optimization. The user must enable the service manually in system settings.
- Consultations are tracked with a session id (`startUssdConsultation`): stale API callbacks and captures from previous consultations must be ignored via `applyUssdReading`/the pending-session check.
- Moving/renaming the accessibility service requires updating `AndroidManifest.xml`; changing the accessibility event filters or description also means updating `res/xml/ussd_catcher_config.xml` and `strings.xml`. Unit tests cover `UssdParser` only; end-to-end USSD behavior needs a real device.
- Some carriers disable `sendUssdRequest` through carrier config (`allow_ussd_requests_via_telephony_manager_bool`); the fallback path is required, not optional.

## Release / CI

- CI (`.github/workflows/ci.yml`) runs `./gradlew testDebugUnitTest assembleRelease --stacktrace` on PRs, `main`, and `v*` tags.
- Tag pushes like `vX.Y` publish a GitHub Release with the signed APK. Before tagging, bump `versionCode` and `versionName` in `app/build.gradle.kts`.
- The stable upload signing config is required for in-place updates. Do not remove or modify the `signingConfigs` blocks or the `upload` alias.
- Never commit APKs, AABs, `.jks`, or `debug.keystore`; distribute builds through GitHub Releases. `PrepaidExpiryWorker` relies on its keep rule in `app/proguard-rules.pro` — update that rule if the worker is moved or renamed.
