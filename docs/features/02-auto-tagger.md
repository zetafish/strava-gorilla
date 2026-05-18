# Auto-Tagger

## Problem

After each run, you manually `bb tag` activities. Tagging is mechanical — a 30km run at easy pace is obviously a "long run", a 8km at tempo pace is obviously "tempo". This should be automatic.

## Rules Engine

Classify by pace zone (relative to threshold) + distance + HR:

| Tag | Distance | Pace | HR |
|-----|----------|------|----|
| `easy` | < 15 km | > threshold + 30s/km | < 75% HRmax |
| `tempo` | 5-15 km | threshold +/- 10s/km | 80-90% HRmax |
| `long` | > 25 km | any | any |
| `interval` | any | high variance between segments | high variance |
| `race` | any | manually tagged or Strava race flag | any |
| `recovery` | < 8 km | very easy | < 70% HRmax |

Thresholds should be configurable per athlete (config file or `.athlete.edn`).

## Implementation

### Data Sources (already exist)
- `bb sync` — pulls activity summaries from Strava API
- `bb search` — retrieves activities with metrics
- `strava.analysis/enrich` — computes pace, EF from streams
- `strava.tags` — read/write tag storage

### New Work
1. Rules engine: function that takes activity summary -> set of tags
2. `bb auto-tag` command: apply rules to untagged activities
3. Athlete config: threshold pace, HRmax, distance breakpoints
4. `--dry-run` flag to preview before applying
5. Skip activities already tagged (unless `--force`)

### Example Usage

```
bb auto-tag                    # tag all untagged activities
bb auto-tag --dry-run          # preview
bb auto-tag --since 2026-01-01 # only recent
bb auto-tag --force            # re-tag everything
```

### Output (sketch)

```
Auto-tagging 12 untagged activities...

2026-05-15  12.3 km  5:15/km  HR 142  → easy
2026-05-13  32.1 km  5:40/km  HR 148  → long
2026-05-11   8.2 km  4:25/km  HR 168  → tempo
2026-05-09   6.0 km  6:10/km  HR 128  → recovery

Apply tags? [y/n]
```

## Effort

Small. Rules are simple conditionals. Tag infrastructure already exists.
