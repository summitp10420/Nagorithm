# Nag engine design

Design notes for Stages 3 and 4. **Nothing here is built yet.** Recorded now because it
changes the Stage 3 database schema.

## Principle

Nagorithm is not an activity tracker. It is a judgment engine: it knows the user's goal,
measures what they have done since it last spoke to them, decides whether that is good
enough, and adjusts how hard it pushes. Ride data is input to a verdict, never the output.

The ride list built in Stage 2 is a verification harness. Once Stage 4 lands it moves
behind a debug toggle or disappears.

## Measurement windows

Two windows, both reported:

- **Since last check-in** — the primary window. The verdict is based on this.
- **Rolling 7 days** — context only, so a check-in after a three-week absence is not
  judged identically to a daily check-in covering the same rides.

This requires persisting the timestamp of every check-in.

## Effort model

Effort is computed locally. The LLM never decides whether the user did enough; it only
writes the message. This keeps verdicts deterministic, auditable, and cheap.

Per-ride load, in preference order based on available data:

1. **Heart-rate based** (preferred) — a TRIMP-style score from ride duration and average
   heart rate, using an age-estimated max heart rate from the user profile.
2. **Duration and distance fallback** — when a ride has no heart rate data, estimate from
   moving time and average speed.

Per-window effort is the sum of ride loads, alongside plain counts the user can sanity
check: ride count, total moving time, total distance.

## Goal target

The goal is free text ("get century-ride capable"), which has to become a number before
anything can be compared against it. Planned approach: a one-time translation at
onboarding produces a weekly load and distance target, which the user can review and
edit. After that, weekly comparison is pure local arithmetic.

Open question, see below.

## Verdict

Actual effort over the window versus the pro-rated target yields a ratio, which buckets
into a direction and a magnitude:

- **Behind** — under target, magnitude scaled by how far under.
- **On track** — within tolerance.
- **Crushing it** — meaningfully over target.

A streak counter tracks consecutive check-ins in the same direction. Streaks drive
intensity, so the app escalates instead of repeating itself at a constant volume.

## Tone and intensity

Two independent axes.

**Tone** is chosen by the user and never changes on its own: humorous, annoying, angry,
or a custom free-text tone.

**Intensity** is computed from the verdict magnitude and the streak, and escalates in
*both* directions.

Worked example, humorous tone:

- Behind, escalating — progressively sillier and more ridiculous accusations of laziness.
- Crushing it, escalating — progressively more absurd praise. "DAMN SON, Arnold
  Schwarzenegger over here liftin a mountain." "Damn Pogačar, stompin those hills like
  the flats back home."

The same intensity ladder under an angry tone produces something entirely different. Tone
picks the voice; intensity picks how far to push it.

Cycling framing is for the proof of concept. Tone should eventually match the theme of
whatever fitness goal is set.

## Transparency

A **"Why this nag?"** button opens the full derivation behind the message currently on
screen:

- The window used, with both dates.
- Every ride counted, with its individual load and the inputs to that load.
- Window totals versus the pro-rated target, and the resulting ratio.
- The verdict direction, magnitude, and current streak.
- Selected tone and computed intensity.
- The exact prompt sent to the LLM.

This exists so the computed verdict can be audited against the message that came back,
and so a wrong-feeling nag can be traced to either bad math or a bad generation.

## Storage implications for Stage 3

Beyond the Activity entity in the original brief:

- **CheckIn** — timestamp, window bounds, computed effort totals, target, ratio, verdict
  direction and magnitude, streak, tone, intensity, prompt sent, message returned. This
  table is what makes escalation and the transparency view possible.
- **Profile / goal** — free-text goal, derived targets, age, weight, selected tone.

## Open questions

- Should the free-text goal be translated into numeric targets by a one-time LLM call at
  onboarding, or entered manually by the user? An LLM translation is friendlier but adds
  a second API call and a failure path before the app can judge anything.
- Resting heart rate would sharpen the effort model but needs an additional Health
  Connect permission. Worth it, or is age-estimated max heart rate enough for v1?
