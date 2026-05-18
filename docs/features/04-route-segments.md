# Route Segment Analysis

## Problem

In ultramarathons, terrain changes everything. A 5:00/km pace on flat is easy; the same pace uphill destroys your race. Understanding how you perform across different gradient segments — and how that compares across training runs on the same route — is critical for pacing strategy.

## What It Does

1. Parse GPS+elevation track into segments by gradient
2. Classify segments: climb (>3%), rolling (1-3%), flat (-1 to 1%), descent (<-1%)
3. Show pace, HR, EF per segment type
4. Compare same route across multiple runs

## Implementation

### Data Sources (already exist)
- `strava.track` — track/stream data from FIT files
- `strava.parser.gpx` / `strava.parser.fit` — GPS + elevation parsing
- `strava.analysis` — pace, EF computation

### New Work
1. Gradient computation from elevation + distance streams
2. Segment classifier: split track into gradient-typed chunks
3. Per-segment stats: pace, HR, EF, duration, distance
4. `bb segments` command for single activity
5. `bb segments --compare` for same route across dates
6. Gradient-adjusted pace (GAP) calculation

### Example Usage

```
bb segments <activity-id>
bb segments <activity-id> --min-length 500m
bb segments --route "homebush-loop" --compare
```

### Output (sketch)

```
Segments: Six Foot Track Ultra 2026

  km      Gradient   Type     Pace    HR    EF
  0-5     -2.1%      descent  4:45    138   0.72
  5-12    +4.3%      climb    6:20    162   0.58
  12-18   +0.5%      flat     5:10    148   0.68
  18-25   -3.8%      descent  4:30    142   0.70
  25-32   +5.1%      climb    7:00    165   0.54
  ...

  Summary by terrain:
  Climb:    avg 6:40/km  HR 163  EF 0.56  (18.2 km)
  Flat:     avg 5:05/km  HR 147  EF 0.69  (14.8 km)
  Descent:  avg 4:35/km  HR 140  EF 0.71  (12.0 km)
```

## Effort

Large. Gradient computation and segment splitting are new. Elevation data can be noisy, may need smoothing. Route matching across runs requires GPS proximity logic.
