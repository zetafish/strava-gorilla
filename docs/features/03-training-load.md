# Training Load Tracker

## Problem

Volume (km/week) doesn't capture intensity. A 60km week of easy running and a 60km week with 15km of tempo work stress the body differently. Training load metrics (TRIMP, TSS, or similar) combine duration and intensity into a single number, enabling acute:chronic ratio tracking to spot injury risk and peak fitness.

## Metrics

### Per Activity
- **TRIMP** (Training Impulse): `duration_minutes * HR_fraction * intensity_weight`
  - HR fraction = (avg_HR - resting_HR) / (max_HR - resting_HR)
  - Exponential weighting by HR zone (Banister method)
- **Simplified load**: `duration * avg_HR / HRmax` (good enough, no zone config needed)

### Rolling
- **ATL** (Acute Training Load): 7-day exponentially weighted average of daily load
- **CTL** (Chronic Training Load): 42-day exponentially weighted average
- **TSB** (Training Stress Balance): CTL - ATL
  - Positive = fresh, negative = fatigued
  - Sweet spot for racing: slightly positive (5-25)

## Implementation

### Data Sources (already exist)
- FIT stream parsing gives per-second HR data
- `strava.analysis/enrich` computes avg HR
- `bb sync` provides activity dates and durations

### New Work
1. TRIMP computation function in `strava.analysis`
2. Daily load aggregation (sum of activity loads per day)
3. ATL/CTL/TSB rolling average computation
4. `bb load` command with ASCII chart output
5. Athlete config: resting HR, max HR (extend `.athlete.edn` from auto-tagger)

### Example Usage

```
bb load                        # last 12 weeks
bb load --weeks 24             # longer view
bb load --target 2026-07-18    # show projected TSB on race day
```

### Output (sketch)

```
Training Load (12 weeks)

       ATL ───  CTL ━━━  TSB ┄┄┄

  Load
  120│         ───
  100│  ━━━  ───   ───
   80│━━━  ━━━        ───  ━━━
   60│                    ━━━  ━━━
   40│                            ━━━
   20│┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
    0│
     └──────────────────────────────
     Mar        Apr        May

  Current: ATL 85  CTL 72  TSB -13 (fatigued)
  Race day projection (7wk): TSB +8 if taper starts week -2
```

## Effort

Medium. HR stream parsing exists. Main work is TRIMP formula, rolling averages, and charting.
