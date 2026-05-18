# Race Readiness Dashboard

## Problem

Assessing race fitness requires running multiple CLI commands (eff, trend, search) and mentally combining the results. Before a target race, you want a single view answering: "Am I ready?"

## What It Shows

- **EF trend** (last 4-8 weeks): rising = aerobic fitness improving
- **Weekly volume**: distance + duration, compared to peak training weeks
- **Long run progression**: longest runs in last 6 weeks, with pace/HR
- **Freshness**: volume taper in final 1-2 weeks vs training block average
- **Comparison to past races**: EF/volume at same point before previous 100K

## Implementation

### Data Sources (already exist)
- `bb eff` — EF computation per activity
- `bb trend` — trend over time for any metric
- `bb search` — filter by date range, distance, tags

### New Work
1. `bb readiness` command that combines these into one output
2. Configurable race date + target distance (CLI args or config file)
3. ASCII dashboard layout: sections for each metric above
4. Optional: compare against a previous race prep block (by tag or date range)

### Example Usage

```
bb readiness --race-date 2026-07-18 --distance 100k --compare-tag "sri-chinmoy-2021"
```

### Output (sketch)

```
Race Readiness: 100K on 2026-07-18 (61 days out)

EF Trend (8 weeks)
  ▁▂▃▃▄▅▅▆  trending up (+12%)

Weekly Volume
  Week  Distance   Duration   vs Avg
  -8    62 km      6:10       -15%
  -7    78 km      7:45       +7%
  ...

Long Runs
  2026-04-12  35 km  3:12  pace 5:30  HR 148  EF 0.68
  2026-04-26  42 km  4:05  pace 5:50  HR 152  EF 0.65

vs Sri Chinmoy 2021 Prep
  EF now: 0.67  then: 0.63  (+6%)
  Volume now: 73 km/wk  then: 68 km/wk
```

## Effort

Medium. Most data retrieval exists. Main work is aggregation logic and output formatting.
