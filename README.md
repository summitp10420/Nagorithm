# Nagorithm

Lightweight AI cycling coach for Android. Reads cycling activities from Health Connect,
stores them locally, and generates a weekly plain-language coaching tip via an LLM API
call using the user's own API key.

v1 is local-only: no backend, no cloud storage, single user, cycling only.

## Requirements

- JDK 17
- Android SDK with platform 36 installed
- A physical Android device with Health Connect (Android 14+ has it built in)

## Build

```bash
./gradlew assembleDebug
```

Install on a connected device:

```bash
./gradlew installDebug
```

`local.properties` holds the machine-specific `sdk.dir` and is not checked in.

## Stack

| Concern | Choice |
| --- | --- |
| UI | Jetpack Compose + Material 3 |
| Health data | `androidx.health.connect:connect-client` |
| Min / target SDK | 26 / 36 |

## Build stages

The app is built in verified increments. Each stage is tested on a real device before
the next begins.

1. **Health Connect availability + permissions** — SDK status check and permission
   request flow. *(done)*
2. **Single successful read** — fetch cycling `ExerciseSessionRecord`s from the last 30
   days and display them raw. *(current)*
3. **Local storage** — Room database, dedupe by source record ID, activity list screen.
4. **Weekly check-in + LLM call** — API key in `EncryptedSharedPreferences`, goal and
   profile onboarding, one coaching call over the last 7 days of rides.

Not in scope for v1: background scheduling, notifications, multi-sport support, cloud
sync, monthly goal reassessment.

## Health Connect permissions

The app requests read-only access to:

- `READ_EXERCISE`
- `READ_DISTANCE`
- `READ_HEART_RATE`
- `READ_SPEED`
- `READ_STEPS`
