# Race Report Generator

## Problem

After a race, you want a structured summary: splits, HR profile, EF curve, comparison to plan and previous races. Currently this requires running multiple CLI commands and manually assembling the picture.

## What It Generates

### Split Table
- Per-5km or per-10km splits with pace, HR, EF, cumulative time
- Positive/negative split indicator
- Comparison to target splits if provided

### Charts (ASCII or local HTML)
- HR over time
- Pace over time
- EF over time
- Elevation profile with pace overlay

### Race Summary
- Finish time, avg pace, avg HR, avg EF
- Best/worst splits
- Fade analysis: first half vs second half pace
- Comparison to previous race on same distance

## Implementation

### Data Sources (already exist)
- `strava.track` — full stream data
- `strava.analysis` — EF, pace, bucketize
- `bb eff` — EF computation
- `bb compare` — comparison across activities
- `strava.tags` — race tag identification

### New Work
1. Split computation at configurable intervals (5km, 10km, aid stations)
2. Report formatter: assemble sections into structured output
3. `bb race-report` command
4. Optional: HTML output with embedded charts (local file, open in browser)
5. Target split comparison (read from file or CLI args)

### Example Usage

```
bb race-report <activity-id>
bb race-report <activity-id> --splits 10km
bb race-report <activity-id> --compare <previous-race-id>
bb race-report <activity-id> --target-pace 5:30
bb race-report <activity-id> --html report.html
```

### Output (sketch)

```
Race Report: Sri Chinmoy 100K — 2026-07-18
Finish: 9:05:23  Avg Pace: 5:27/km  Avg HR: 152  Avg EF: 0.64

Splits (10 km)
  km      Split     Pace    HR    EF     Cumulative
  0-10    52:30     5:15    145   0.69   0:52:30
  10-20   53:45     5:22    148   0.67   1:46:15
  20-30   54:10     5:25    150   0.66   2:40:25
  30-40   55:30     5:33    153   0.64   3:35:55
  ...
  90-100  58:20     5:50    158   0.60   9:05:23

Fade: +35s/km from first to last 10K split
First half: 5:20/km  Second half: 5:35/km  (+2.8%)

vs Target (5:30/km): ahead by 3 min at halfway, behind by 0 at finish
vs Sri Chinmoy 2021: 7 min faster, +4% EF improvement
```

## Effort

Medium. Split computation is straightforward. Most analysis functions exist. HTML output is optional stretch goal.
