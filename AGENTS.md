# Strava

Babashka project for scraping, parsing, and analyzing personal Strava activity data.

## Runtime

**Babashka only** (`bb`). No JVM Clojure step, no Garmin FIT SDK shell-out. All parsing is in-process.

## Usage

Task list: `bb tasks`. Common commands:

```bash
bb sync -y 2026 [-m 6]            # scrape calendar + download originals
bb report:activity <id>           # splits within one activity
bb report:activities [-p X ...]   # one row per activity (filtered)
bb report:by-day    --from ... --to ...
bb report:by-week   --from ... --to ...
bb report:by-month  --from ... --to ...
```

All report CLIs support `-h` / `--help`.

## Architecture

| Namespace | Purpose |
|-----------|---------|
| `strava.cli` + `strava.cli.*` | Task entrypoints (report:*, sync, load, calendar) |
| `strava.repo` | Local data access — get/load calendars, activities, tracks, originals |
| `strava.scrape.*` | HTML scrapers: `calendar`, `activity`, `original`, `session` |
| `strava.parser.*` | Format parsers: `fit`, `gpx`, `tcx` (dispatched by `parser.core`) |
| `strava.search` | Filter activities by pattern/date/distance; in-memory index (lazy-built, cached in atom) |
| `strava.track` | Split/aggregate a track: `splits`, `stats`, `agg`, `summary` |
| `strava.stats` | Per-activity stats computed from tracks |
| `strava.analysis` | Derived metrics (EF, pace, kmph, trends) |
| `strava.chart` | ASCII + SVG line charts (`line-chart`, `svg-line-chart`) |
| `strava.table` | Table printing for CLI output |
| `strava.cache` | Simple file-backed cache under `.data/` |
| `strava.api` | Legacy Strava OAuth API client — not used by current sync path |

## Data flow

1. `bb sync` → `scrape.session` logs into strava.com → `scrape.calendar` fetches monthly HTML → `scrape.activity` fetches per-activity HTML → `scrape.original` downloads FIT/GPX/TCX from `export_original`
2. `parser.core` parses the original file (dispatch by extension) → normalized track (vec of sample maps with `:at`, `:timestamp`, `:distance`, `:heart_rate`, `:enhanced_speed`, `:cadence`, …)
3. `repo/get-track` returns cached parsed track (JSON on disk under `.data/tracks/`)
4. `track/splits` buckets samples by `:by :time|:distance|:even`
5. Report CLIs aggregate + print via `strava.table`

## Data layout (`.data/`)

| Dir | Contents |
|-----|----------|
| `calendars/` | Scraped monthly calendar JSONs (one per year-month) |
| `activities/` | Scraped activity metadata JSONs |
| `originals/` | Raw downloaded FIT / GPX / TCX files |
| `tracks/` | Parsed tracks as pretty-printed JSON (~4 MB each, ~12K samples) |
| `stats/` | Precomputed per-activity stats |
| `html/`, `_activities/`, `_descriptions/`, `_tracks/` | Scrape intermediates / older layouts |

`.data/` is gitignored. Chart output (SVG, etc.) goes to `scratch/`, **not** `.data/`.

## Auth

| File | Contents | Gitignored |
|------|----------|------------|
| `.auth.edn` | Session cookie for strava.com scraping | Yes |
| `.creds.edn` | Legacy OAuth tokens (unused by scrape path) | Yes |

Scraping uses the session cookie (`scrape.session`). OAuth is not needed for the sync flow.

## Perf notes

- `bb <task> -h` should be ~60 ms. Cost above that is namespace load. If a task is slow to `-h`, look for top-level `def`s that touch disk. Example fix already applied: `strava.search/state` is now built lazily (`ensure-state!`) instead of at ns load.
- Track JSON parse via Cheshire is fast (~3 ms per 4 MB file). Migrating to CSV/transit was benchmarked and lost — don't.
- `convert.clj` (kebab-case migration script) runs in parallel via `pmap` over `.data/tracks/`.

## Dependencies (`bb.edn`)

- `org.babashka/http-client` — scraping
- `com.cnuernber/charred` — CSV
- `dev.weavejester/medley` — utilities

## No testing / linting / CI

No test suite, no CI. clj-kondo warnings exist but are not gated.
