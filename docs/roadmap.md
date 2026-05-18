# Strava Tooling Roadmap

Local-first approach. All features build on existing bb CLI + FIT/GPX/TCX parsing + Strava API sync.

## Features

| # | Feature | Effort | Dependencies |
|---|---------|--------|-------------|
| 1 | [Race Readiness Dashboard](features/01-race-readiness.md) | Medium | EF, trend, search |
| 2 | [Auto-Tagger](features/02-auto-tagger.md) | Small | tag, sync |
| 3 | [Training Load Tracker](features/03-training-load.md) | Medium | HR streams, analysis |
| 4 | [Route Segment Analysis](features/04-route-segments.md) | Large | GPX/FIT parser, track |
| 5 | [Race Report Generator](features/05-race-reports.md) | Medium | all analysis tools |

## Priorities

Start with **Auto-Tagger** (small, immediate value) and **Race Readiness** (directly useful for 100K prep).

## Architecture

Everything runs as `bb` tasks. No cloud, no server. Output is ASCII terminal or local HTML files where visualization needs it.
