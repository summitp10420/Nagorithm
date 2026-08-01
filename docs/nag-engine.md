# Nag engine design

Design notes for Stages 3 and 4. **Nothing here is built yet.** Recorded now because it
changes the Stage 3 database schema.

## Principle

Nagorithm is not an activity tracker. It is a judgment engine: it knows the user's goal,
measures what they have done since it last spoke to them, decides whether that is good
enough, and adjusts how hard it pushes. Ride data is input to a verdict, never the output.

The ride list built in Stage 2 is a verification harness. Once Stage 4 lands it moves
behind a debug toggle or disappears.

## Scope: aerobic goals only

This app is for endurance goals — ride a century, get faster, get in better shape, trim
up. Strength training is explicitly out of scope, and that exclusion is load-bearing
rather than cosmetic: heart rate is a poor proxy for lifting stress, so supporting it
would require a second load model with no mathematics in common with this one. Excluding
it means HRSS covers everything.

### Everything aerobic counts. Specificity is a goal concern.

HRSS is sport-agnostic — a swim, a row, and a walk all produce real load. The first real
device test settled this immediately: 12 sessions in the window, of which 6 were swims, 5
were rows, and 1 was a ride. A cycling-only filter was discarding ten of twelve workouts,
and an engine that concludes you trained once in a month when you trained twelve times is
wrong in the most demoralizing way available.

So all aerobic work is credited, always. Aerobic types are defined by inversion — everything
counts except an explicit non-aerobic list — so a new Health Connect exercise type defaults
to being credited rather than silently dropped.

What the goal changes is *specificity*, not credit:

- **Sport-specific goals** (ride a century) — all aerobic work still counts toward
  consistency and volume, but the share of load in the goal sport is its own signal. Cross
  training is not a substitute for the bike when the goal is a bike.
- **General fitness goals** (trim up, get in better shape) — every aerobic session counts
  equally and the share signal is ignored.

Critically, this logic belongs in the verdict layer, not the load calculation. The effort
engine reports load broken down by sport and stays neutral about which one matters. If
"better ride that damn bike" were welded into the scoring, general-fitness goals would
become a special case of a cycling app.

## Measurement windows

- **7-day rolling load** — the verdict window. "This week" is the unit a person actually
  feels and defends themselves in.
- **28-day rolling load, expressed per week** — the personal baseline the 7-day figure is
  measured against.
- **Since last check-in** — used for the conversational framing, so a check-in after a
  three-week absence does not read identically to a daily one covering the same rides.

15 days was considered and rejected: too long to represent "this week", too short to be a
stable baseline. 7 and 28 splits those jobs cleanly and is what 30 days of readable
Health Connect data supports on day one.

Health Connect will not serve data older than 30 days before permissions were granted,
but Room keeps everything it has ever seen, so the baseline lengthens and steadies over
time past that wall.

## Effort model

Effort is computed locally, using established training-load practice rather than an
invented score. The LLM never decides whether the user did enough; it only writes the
message. This keeps verdicts deterministic, auditable, and cheap.

### Per-ride load: HRSS (normalized TRIMP)

Power-based TSS is not available — no power meter, and Health Connect does not carry the
data. Of the heart-rate models intervals.icu offers, both regression-based ones require a
history of activities with power *and* heart rate to train against, which rules them out.
That leaves HRSS, which is also intervals.icu's own fallback when there is not enough data
to build the others, and what Elevate uses.

```
HRr       = (HR − restingHR) / (maxHR − restingHR)
TRIMP     = minutes × HRr × 0.64 × e^(1.92 × HRr)     (male; 0.86 / 1.67 female)
reference = TRIMP for one hour at threshold HR
HRSS      = (TRIMP / reference) × 100
```

This lands on the same scale as power TSS — one hour at threshold ≈ 100 — so the numbers
mean what they mean everywhere else in the sport. Load rises with roughly the square of
intensity, which correctly stops easy volume from looking like hard work.

**Compute from heart-rate samples, not the session average.** Averaging heart rate over a
ride understates variable efforts, which is the exact flaw Normalized Power was invented
to correct. Health Connect's `HeartRateRecord` carries the full sample series, so TRIMP is
summed per sample.

### Sessions without heart rate

Swimming forced this case. Most people, including anyone relying on a chest strap, have no
swim-capable heart rate monitor, so a pool session arrives as duration and distance only.
Discarding those would tell a swimmer they did nothing, which is the worst possible failure
for a motivational app.

So an HR-less session is scored at an assumed fraction of heart-rate reserve chosen per
sport — zone 3 for swimming, since nobody swims laps easily and few hold threshold for a
whole session. The assumption runs through the same TRIMP maths as a measured session
rather than a flat score per hour, so it scales with the individual's own reserve and
threshold instead of asserting an absolute number that means different things for different
people. Estimated sessions are flagged as estimates everywhere they appear.

### Inputs

- **Max HR** — the input that matters most, because threshold is derived from it, so an
  error moves both the session's intensity *and* the reference it is divided by. Measured
  on real data: dropping max from 180 to 165 inflated a ride from roughly 70 to 93.
- **Resting HR** — read from Health Connect via `READ_RESTING_HEART_RATE`. Less critical
  than it first appears: it sits in both the session's reserve fraction and the threshold
  reference, so normalization cancels most of the error. Moving resting from 60 to 45 on
  the same ride changed the score by about 7%.
- **Threshold HR** — estimated at roughly 85% of max HR initially, manually overridable.
- **Age and sex** — sex selects the TRIMP coefficients above; age backs the max HR
  floor. Captured once in profile setup.

**An observed peak is a lower bound on max HR, never an upper one.** You only see a number
by actually reaching it, and an easy endurance ride proves nothing about the ceiling.
Taking the observed peak at face value drags derived threshold down with it and inflates
every score. So detected max is the higher of the observed peak and the age estimate, and
the observed peak itself is a high percentile rather than the raw maximum, since one
spurious sensor spike would otherwise define it.

### Rolling load

Daily HRSS totals feed the windows. The 7-day total over the 28-day weekly baseline gives
the acute-to-chronic ratio: roughly 0.8–1.3 is holding steady, below 0.8 is fading, above
1.5 is ramping hard.

The ratio is self-normalizing, so it behaves the same for someone doing 200 a week as 600
— which is exactly what a no-target model needs, since it makes the same math work for a
beginner and a strong rider without either being measured against the other.

It has been criticized in recent literature as an injury-prediction tool; it is used here
for motivation, not injury risk.

Alongside the scores, every window reports plain counts the user can sanity check: ride
count, total moving time, total distance.

## No target. Progress from where you started.

There is deliberately no weekly number to hit. An absolute target punishes a beginner for
being a beginner and lets a strong rider coast. What matters is movement from the user's
own starting point.

The goal is not converted into a target. It supplies framing — a century goal makes long
rides the interesting axis — and eventually a readiness judgment.

Consequence: the app must snapshot the user's load at onboarding, so it can later say
"you have gone from there to here." That baseline is stored once and never recomputed.

## Cold start: the first weeks lie

The 28-day baseline is only as good as the history behind it, and on a fresh install that
history is bad in two compounding ways. Health Connect serves nothing older than 30 days
before permissions were granted, and Garmin does not backfill — a confirmed ride from five
days before install never appeared at all.

The result is a chronic baseline that is understated, which inflates the acute-to-chronic
ratio. The first real device run produced an ACWR of 1.95, reading "ramping hard", from a
single ordinary ride sitting on top of a truncated baseline. That is arithmetically correct
and behaviourally wrong.

So the verdict must know how much history it is standing on and hedge accordingly. Until
roughly a full chronic window of locally observed data exists, short-term ratio signals are
suppressed in favour of the ones that survive a cold start: days since last session, and
session count. Long-term signals are meaningless anyway before time passes — the onboarding
baseline necessarily reads 100% on the day it is captured.

This is not a rare edge case. Every user hits it, on day one, which is the single worst
moment to deliver a confidently wrong verdict.

## Zero load: the case that matters most

If nothing has been recorded for days, that is the verdict. It needs no goal, no baseline,
no capability metric, no efficiency factor, and no history — it is a date subtraction.

It will also be the most frequently triggered path in real use. Most people do not need
nuanced feedback about their acute-to-chronic ratio; they need something to notice they
have not moved since Tuesday.

This path must never be gated behind having enough data to compute anything else. A brand
new user with an empty database and no goal set still gets nagged on day four. And the
suggestion should not be sport-purist — go for a walk, do anything.

## Aerobic fitness trend

For goals with no finish line ("get faster", "trim up"), improvement itself is the signal.
Two measures, both from data already being read:

- **Efficiency factor** — distance covered per heartbeat. Rising over time means the same
  effort is buying more speed, which is exactly what getting fitter means. Noisy per ride:
  hills, wind, stops, and indoor rides with no distance all corrupt it. Only meaningful as
  a trend across many rides, and should stay hidden until there is enough history to be
  honest.
- **Resting heart rate trend** — falling means improving aerobic fitness. Nearly free,
  since the permission is already needed for HRSS and Garmin already writes it.

Resting heart rate is also bidirectional in a useful way. A *rising* resting HR usually
means accumulated fatigue or illness, which is precisely when the app should tell the user
to rest rather than calling them lazy. The load math alone cannot see that.

## Verdict

Four locally computed signals, all relative:

1. **Consistency** — days since the last ride, rides in the last 7 days against the user's
   own typical frequency, longest gap in the window.
2. **Short term** — 7-day load over the 28-day weekly baseline. Are they working right now
   relative to their own normal?
3. **Long term** — the current 28-day baseline over the onboarding baseline. Is the floor
   actually rising, or has consistency plateaued into a comfortable rut?
4. **Capability** — longest single ride in the last 28 days against their longest ever.
   For distance goals this is the binding constraint; weekly volume alone does not make
   someone century-ready.

Direction comes from these together, not from a compliance check.

## Phases

Sustained improvement changes what the app should be saying, not just how loudly. The nag
has a phase, derived from how long the long-term signal has been positive and how the
capability marker compares to the goal:

- **Building the habit** — early, or after a lapse. Consistency is the whole message.
- **Building volume** — consistent, load trending up. Push the trend.
- **Plateaued** — consistent but flat for a long stretch. The rut is the problem, not
  laziness.
- **Ready** — months of improvement and a capability marker near the goal. The message
  turns from "go train" to "have you actually gone and done it yet?" and eventually to
  "you could ride to Vegas, go find out."

The Ready phase matters because an app that nags forever, even after you have obviously
succeeded, is one you uninstall the week you succeed.

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
- **Profile** — age, sex, weight, resting/max/threshold HR with a flag for whether each
  was measured or estimated, and selected tone. Captured once in a setup flow.
- **Goal** — free-text goal, plus an optional distance and optional date. Edited from its
  own screen, separate from profile setup, since goals change and biological facts mostly
  do not. No derived load target.
- **Baseline snapshot** — the user's 28-day load and longest ride at onboarding. Written
  once. Everything long-term is measured against it.

Per-ride stored load should be recorded alongside the ride so the transparency view can
show what each ride contributed without recomputing it.

## Two settings surfaces

Deliberately separate, because they change on different timescales:

1. **Profile setup** — age, sex, weight, tone. Runs once at first launch.
2. **Goals** — the free-text goal and its derived weekly target. Revisited whenever the
   user wants.

## Resolved

- **No numeric target.** The question of how to translate a free-text goal into a weekly
  number is moot — progress is measured against the user's own starting point, not
  against a target. Onboarding needs no LLM call.

- **Effort model** — HRSS (normalized TRIMP), computed from heart-rate samples. Chosen
  over an invented score, and over the intervals.icu regression models which need power
  history the user does not have.
- **Resting heart rate** — worth the extra permission, but for the trend signal more than
  for load accuracy. An earlier claim here that HRSS is highly sensitive to it was wrong;
  the threshold normalization cancels most of the error. Max HR is the sensitive input.
- **Garmin does not write resting heart rate to Health Connect.** Confirmed on device: the
  permission is granted and the read returns nothing. The app must therefore treat its
  population default as visibly provisional and make the override easy to find, rather than
  quietly scoring everything against a number the user never supplied.
- **Aerobic scope** — all aerobic sessions are credited. Goal specificity is a separate
  signal computed in the verdict layer.
- **Windows** — 7-day verdict against a 28-day baseline.
- **Power** — not used. No power meter, and Health Connect does not carry it.
