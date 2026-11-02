# Nag engine design

Design notes and the reasoning behind them.

**Status.** Effort scoring, goals, the progress model, the local verdict, tone selection,
the LLM call, background sync and notifications are all built and running on a device. Phases,
streaks and intensity are not. Sections below are marked where they describe intent rather
than code. [overview.md](overview.md) is the more current walkthrough.

## Principle

Nagorithm is not an activity tracker. It is a judgment engine: it knows the user's goal,
measures what they have done since it last spoke to them, decides whether that is good
enough, and adjusts how hard it pushes. Ride data is input to a verdict, never the output.

The activity list is a verification harness and lives behind a collapsed diagnostics
section.

## The app must never overstate what it knows

This is the constraint everything else bends around, and it earned its place twice on the
first device run of the verdict engine.

It told a user doing about two hours a week to ease off, because the acute-to-chronic ratio
was 2.10. The ratio was correct and the advice was ridiculous. Then it congratulated a swim
for being 24.2% better than predicted, off a curve fitted through two points, where the
line necessarily passes exactly through both and no residual remains to say whether the
model fits anything at all.

Both failures ran the same direction: confident arithmetic on thin evidence. That is the
failure that gets a nagging app deleted, because a single obviously wrong verdict discredits
every correct one that follows. So every signal carries a competence boundary — an absolute
floor beneath a ratio, a minimum number of points beneath a fit, a distance range outside
which a curve may not be extrapolated — and below that boundary the app states facts without
sizing them.

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

**Swimming never uses sampled heart rate at all.** A watch that cannot read underwater still
emits the occasional stray value, and those few readings are noise rather than a
measurement. Trusting them is worse than having none: the first device run scored a
21-minute swim at 5 HRSS from four stray samples, against roughly 22 for the identical swims
either side of it that had no samples to mislead the calculation. Proper swim heart rate is
a hardware problem, and hardcore swimmers are not who this app is for.

### Partial coverage

The same failure lurks in every sport, because samples only bill the time between
themselves. A chest strap that dies halfway through a ride would charge the second half at
nothing. So coverage is measured, and any stretch the samples do not span is filled at the
sport's assumed intensity. A session more than 90% covered counts as measured; below that it
is marked partial and says so.

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

## No target. The goal chooses the yardstick.

There is deliberately no weekly number to hit. An absolute target punishes a beginner for
being a beginner and lets a strong rider coast. What matters is movement from the user's
own starting point.

The goal is not converted into a target. It selects **which measurement becomes the
baseline**, and that baseline is always the user's own earlier value of it:

| Dimension | Goal sounds like | Measured as |
| --- | --- | --- |
| Distance | ride a century, finish a 5k | longest single effort |
| Pace | six minute mile, sub-20 5k | predicted time over a fixed distance |
| Volume | trim up, stop falling apart | 28-day load expressed per week |

A century and a first 5k are the same arithmetic pointed at different columns.

The starting value is captured when the goal is set and never recomputed. Re-deriving it
would move the finish line every time the user improved, which is the exact failure the
no-target model exists to avoid. It lives on the goal rather than the profile, because a
pace goal and a distance goal start from different numbers on the same day.

A goal set before any qualifying activity exists has no starting value. That is a real
state rather than an error: it means there is nothing to measure from yet, and the verdict
has to say so instead of inventing a baseline.

## Cold start: the first weeks lie

The 28-day baseline is only as good as the history behind it, and on a fresh install that
history is bad in two compounding ways. Health Connect serves nothing older than 30 days
before permissions were granted, and Garmin does not backfill — a confirmed ride from five
days before install never appeared at all.

The result is a chronic baseline that is understated, which inflates the acute-to-chronic
ratio. The first real device run produced an ACWR of 1.95, reading "ramping hard", from a
single ordinary ride sitting on top of a truncated baseline. That is arithmetically correct
and behaviourally wrong.

So the verdict knows how much history it is standing on and hedges accordingly. Until there
are at least three weeks of locally observed data across at least four sessions, ratio
signals are suppressed in favour of the ones that survive a cold start: days since the last
session, and session count. Long-term signals are meaningless anyway before time passes —
the onboarding baseline necessarily reads 100% on the day it is captured.

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

## Am I getting fitter?

Load cannot answer this. Someone improving and someone grinding themselves into the ground
both post high numbers — load measures cost, not return. Progress needs a separate model.

### Why not buckets

The first attempt grouped sessions by distance so that pace could be compared like with
like, since pace across different distances is meaningless. That was the wrong tool, and it
failed twice over.

It first failed on boundaries: bucket indices computed from a logarithm lay down a fixed
grid, so a 40.0 km and a 41.5 km ride — under 4% apart — landed either side of a line and
were never compared, while 40.0 and 45.9 grouped happily. Clustering by actual proximity
fixed that. But the deeper problem survived: bucketing can only ever speak about distances
you have repeated, and it discards everything else. Real training is full of one-off
distances.

The platforms this app is measured against do not bucket. They fit a threshold and track
how it moves.

### Fitted speed curve

Over hard efforts, distance is close to linear in duration:

```
distance = criticalSpeed × time + reserve
```

Fitting that line means a 900 m swim and a 1400 m swim both inform the same estimate. No
session is discarded for being an unusual length, and there are no boundaries to fall either
side of. It is the same model behind Critical Swim Speed and functional threshold power.

The fit is only honest if each point is a genuinely hard effort for its duration, which
training sessions are not. So it is fitted to the upper envelope — the furthest covered at
each duration — rather than the whole scatter. An easy hour is then evidence of nothing
rather than evidence of decline.

Two guards keep it from overstating itself. A fit through fewer than three points is not
allowed to quote a percentage, because with two points and two parameters the line passes
exactly through both and there is no residual left to measure error with. And it may not
predict far outside the distances actually recorded, because that is where a straight line
stops describing physiology.

New sessions are judged against a curve fitted **without them**. Including a session in its
own baseline would let a breakthrough quietly explain itself away.

Because the same line predicts a time at any distance, the abstraction can be converted
back into something recognisable before it reaches the user: not "critical speed rose to
0.89 m/s" but "your 1000 m is worth 18:40 now, against 19:22 a month ago."

### Efficiency factor

Speed per heartbeat: distance over time over average heart rate. Going faster at the same
heart rate is the cleanest evidence of aerobic improvement there is, and because both terms
are rates it does not care how long the session was.

Swimming is excluded outright — the heart rate is missing or wrong, for the same reason load
scoring ignores stray swim samples.

### The two cover each other's gaps

This is the useful part. The speed curve wants clean distance and tolerates absent heart
rate; efficiency wants heart rate and tolerates a messy route.

- **Pool swims and ergs** — a fixed, windless, flat distance every time, and never any
  usable heart rate. The curve is at its best exactly where load is at its worst.
- **Road rides and runs** — heart rate is reliable, distance is corrupted by hills and wind.
  Efficiency carries these.

Outdoor cycling pace remains genuinely noisy because elevation is not read. That is a larger
source of error than the bucketing bug ever was, and it is unaddressed.

### Resting heart rate trend *(not built)*

Falling means improving aerobic fitness, and it is nearly free since the permission is
already needed for HRSS. It is also bidirectional in a useful way: a *rising* resting heart
rate usually means accumulated fatigue or illness, which is precisely when the app should
tell the user to rest rather than calling them lazy.

Blocked on hardware rather than code — Garmin does not write resting heart rate to Health
Connect, confirmed on device.

## Verdict

One of seven states, decided locally. The wording layer may only choose how to say the one
that came back; it never gets to pick which is true.

| State | Meaning |
| --- | --- |
| `FEED_SILENT` | Health Connect returned nothing, including passive data. Not the user's fault |
| `COLD_START` | Too little history for any trend to mean anything |
| `RESTING` | Inside the recovery window for the load being carried |
| `IDLE` | Past the point where absence costs fitness |
| `STEADY` | Work going in, nothing measurably changed. The common case |
| `PROGRESSING` | Something improved in the dimension the goal cares about |
| `RAMPING_HARD` | Acute load well above what the base supports |

### Two modes, and the quiet one carries the app

At a cadence of every couple of days, most check-ins have no news, because fitness moves on
a scale of weeks. So the honest design has two shapes:

- **News** — a session arrived. Say what it did to the number the goal depends on, up or
  down.
- **No news** — say where things stand, then say go and do something.

The no-news case is the frequent one, which means consistency carries more of the product
than the progress analysis does. The analysis is the occasional payoff.

`RAMPING_HARD` exists so the news case can come back negative. If the verdict can only ever
congratulate, it is a cheerleader, and the user stops believing it — which costs the times
it says something true.

### News survives a late sync

News is measured by when the app *first saw* a session, not when the session happened.
Garmin can deliver a ride days late, and a session that arrives after its window was judged
is still new information to the user.

## Stagnation: detect it, do not diagnose it

Detecting absence is trivial — nothing came in. Attributing it is impossible: a plateau, a
taper, illness, a holiday and simple laziness look identical from here.

The resolution is that attribution is unnecessary. Two weeks off costs the same whether the
user was sick or scrolling; the physiology does not care why. So the app detects the fact
and lets tone handle the uncertainty, rather than diagnosing a cause it cannot see.

Detraining is not a fixed clock either. Someone carrying 400 HRSS a week sheds more in ten
days than someone at 80, because they built more that needs maintaining. So the tolerance
before absence starts costing something scales with chronic load — three days at the top
end, seven at the bottom — and past that the bands are drifting, losing, and gone soft.

## Silence is not idleness

An empty exercise list looks exactly the same whether the user rested or the sync broke.
Getting this wrong means telling someone to get off the couch two days after a sixty-mile
ride, which is the fastest uninstall available.

Steps and heart rate arrive passively all day from any worn device. If *nothing* arrived —
no workouts and no passive data — the feed is broken and the app says so instead of forming
an opinion. This is not hypothetical: a confirmed ride never reached Health Connect at all
during testing.

What this cannot catch is a single missing session among many, or work no device recorded.
A weightlifting session that never reached Garmin, Oura or Health Connect is invisible, and
the app has to stay humble about the possibility rather than assume its picture is complete.

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

## Storage

Built:

- **StoredActivity** — one session, keyed on Health Connect's own record id so re-reads
  dedupe for free. Carries its load score and every input behind it, so the transparency
  view can show what a session contributed without recomputing it. Also carries when the
  app first saw it, which is what makes late syncs count as news.
- **Profile** — age, sex, resting/max/threshold heart rate with overrides and detection
  flags, the onboarding baseline, and the last check-in timestamp.
- **Goal** — the text in the user's own words, its dimension, optional target distance and
  date, and the starting value that dimension is measured from. Replaced rather than edited,
  since changing a goal's dimension in place would leave a starting number answering a
  question nobody asked. Old goals are archived, never deleted.

Not built:

- **CheckIn** — timestamp, the verdict and its inputs, tone, intensity, prompt sent,
  message returned. Needed for escalation and for auditing a generated message against the
  verdict behind it. The audit trail currently exists in memory only.

## Two settings surfaces

Deliberately separate, because they change on different timescales:

1. **Profile setup** — age, sex, heart-rate overrides. Runs once at first launch.
2. **Goals** — the free-text goal and its dimension. Revisited whenever the user wants.
3. **Voice** — tone and the optional API key. Changes nothing the app knows, only how it
   says it, which is why it sits apart from both of the above.

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
- **The goal selects the baseline** rather than supplying a target. Progress is still
  measured against the user's own past; the goal only decides which measurement counts.
- **Progress is a fitted curve, not a bucket.** Distance bucketing was built, then deleted:
  it could only speak about repeated distances and split at arbitrary boundaries. Critical
  speed and efficiency factor between them cover every session, and cover each other's
  weaknesses in data.
- **Ratios need an absolute floor.** ACWR is meaningless when both numbers are small, and
  telling a two-hour-a-week user to back off is the opposite of what the app is for.
- **Attribution is unnecessary.** Detect that someone stopped; do not try to work out why.
- **hrTSS is not an upgrade over HRSS.** The standard formulation squares the ratio of
  *average* heart rate to threshold, which badly underscores intervals — twenty minutes hard
  alternating with twenty easy averages out to something moderate. Per-sample summation
  already handles that correctly and is kept.
- **The model rewords a settled verdict.** It receives the finished verdict, the goal text,
  the audit-trail lines, and, so it has something worth observing, the last 42 days of
  sessions and the user's recent notes. It chooses what to mention but never what is true.
  This superseded an earlier rule that it was never shown sessions; the privacy cost of that
  change is stated in the README.
- **Fabricated numbers fail closed.** The reply may contain no figures at all, except whole
  figures from the user's own goal or notes. One caught invention would discredit every honest
  number the app has ever shown, so a rejected answer falls back to the template and says so
  in the audit.
- **The key is not a dependency.** Without one the app is fully functional and makes no
  network call. The model is an upgrade to the voice.
- **The card shows what was said, not a fresh rendering.** Recomputing the wording on open
  would show a second version of one judgment, which reads as the app changing its mind.

## Open

- **sTSS for swims.** The fitted critical speed is exactly the Functional Threshold Speed
  the pace-based TSS equation needs, which would let a hard swim outscore an easy one
  instead of every swim receiving a flat zone-3 estimate from duration.
- **Elevation.** Unread, and the largest remaining source of noise in outdoor cycling pace.
- **Oura.** Plausibly a strong fit — resting heart rate is its strength and Garmin's gap —
  but unverified. Nobody has confirmed it writes exercise sessions to Health Connect, and
  it comes off for lifting.
- **Yards for swimmers.** Distances are shown in metres because that is what Garmin records,
  but a 914 m swim is a 1000 yard swim to the person who swam it.
- **Distributing the app.** v1 is bring-your-own-key, which is the only model that needs no
  server. Billing users against a single shared key is not a settings change: a key shipped
  inside an APK can be unzipped out of it by anyone, so it would require a proxy holding the
  key, authenticating users and metering their calls. That is a backend, and the moment one
  exists the local-only privacy claim has to be restated honestly.
