# Nagorithm

A nag engine for Android, not an activity tracker.

It reads aerobic activity from Health Connect, scores the effort locally, and decides whether
you are making progress against your goal. Then it tells you, with an opinion. Everything is
computed on the device; a language model may later choose the wording, but never the verdict.

v1 is local-only: no backend, no cloud storage, single user.

## What it does today

- Reads all aerobic sessions from Health Connect, scores each one for training load, and
  stores them locally so history outlives Health Connect's 30-day read window.
- Takes a goal in your own words and captures the one measurement that goal makes
  interesting — longest effort, predicted time over a distance, or weekly volume.
- Fits a speed curve to your own history and reports whether new sessions beat it.
- Decides between resting, drifting, losing fitness, holding steady, progressing, or
  ramping too hard, and writes a plain-language nag from that decision.
- Shows the full derivation behind every nag under "Why this nag?".

Not built yet: the LLM wording layer, tone selection, background sync, and notifications.
See [docs/nag-engine.md](docs/nag-engine.md) for the design and the reasoning behind it.

## Requirements

- JDK 17
- Android SDK with platform 36 installed
- A physical Android device with Health Connect (Android 14+ has it built in)

`local.properties` holds the machine-specific `sdk.dir` and is not checked in.

## Build

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew testDebugUnitTest
```

The load, pace and verdict arithmetic is pure Kotlin with no Android dependencies, so it is
covered by JVM unit tests rather than by squinting at a phone.

## Architecture

| Package | Holds |
| --- | --- |
| `health` | Health Connect reads, sport taxonomy, raw session summaries |
| `data` | Room entities, DAOs, and the repository that syncs and scores |
| `effort` | Training load, the fitted speed curve, efficiency factor |
| `verdict` | The decision, its audit trail, and the template wording |
| `format` | Value formatting shared by the UI and the nag templates |
| `ui` | Compose screens |

Data flows one way. `health` knows nothing about scoring, `effort` knows nothing about
goals, and `verdict` decides everything the wording layer is later allowed to say.

### Database

Room, currently at version 4, with schemas exported to `app/schemas` and real migrations
from version 3 onward. Destructive migration is deliberately restricted to versions 1 and 2:
past those, the database holds activity history reaching further back than Health Connect
will re-serve, plus onboarding baselines that are written once and cannot be recomputed.

| Entity | Purpose |
| --- | --- |
| `StoredActivity` | One session, deduped on Health Connect's record id, with its load score and the inputs behind it |
| `Profile` | Age, sex, heart-rate values and overrides, onboarding baseline, last check-in |
| `Goal` | Goal text, its dimension, and the starting value that dimension is measured from |

## Health Connect permissions

Read-only access to `READ_EXERCISE`, `READ_DISTANCE`, `READ_HEART_RATE`,
`READ_RESTING_HEART_RATE`, `READ_SPEED`, and `READ_STEPS`.

Steps and heart rate are also read as a liveness probe. An empty exercise list looks
identical whether you rested or the sync broke, and the difference decides whether the app
is entitled to have an opinion about it.

## Build stages

Each stage was verified on a real device before the next began.

1. **Health Connect availability and permissions** — SDK status check and permission flow. *(done)*
2. **Single successful read** — fetch sessions and display them raw. *(done)*
3. **Local storage** — Room, dedupe by record id, activity list. *(done)*
4. **a. Effort scoring** — profile setup, HRSS from heart-rate samples, rolling load
   windows, onboarding baseline. *(done)*
5. **b. Goals and the verdict** — goal capture, fitted speed curve, efficiency factor,
   local verdict, template nags, "Why this nag?". *(done)*
6. **c. Voice and nagging** — API key in `EncryptedSharedPreferences`, tone selection,
   LLM-written wording over the local verdict, background sync, notifications. *(next)*
