# Strava

Babashka project for downloading, parsing, and analyzing Strava activity data from FIT files.

## Usage

```bash
bb eff --pattern <date-or-name> [-i <seconds>] [--from <offset>] [--to <offset>]
```

Example: `bb eff --pattern 2025-03-09 -i 60 --from 1h --to 2h`

## Two runtimes

- **Babashka** (`bb`): runs all code. Entrypoint via `bb eff` (defined in `bb.edn` tasks).
- **JVM Clojure** (`clj`): only used internally by `strava.fit` to resolve the Garmin FIT SDK classpath (`clj -Spath`). The CSVTool runs as a shelled-out `java` process.

## Architecture

| Namespace | Purpose |
|-----------|---------|
| `strava.core` | CLI entrypoint, formatting, enrichment (EF, pace, km/h) |
| `strava.api` | Strava API v3 client (OAuth, activities, streams, FIT download) |
| `strava.fit` | FIT → CSV → Clojure maps pipeline, caching, bucketed aggregation |
| `strava.repo` | Local FIT file repository (`.repo/`), sync by month, pattern search |

## Data flow

1. `repo/sync-month` downloads FIT files via `api/download-original` into `.repo/`
2. `fit/parse-file` shells out to Garmin CSVTool, parses CSV, caches as EDN in `.cache/`
3. `fit/bucketize` aggregates records into time windows
4. `core/enrich` adds EF, pace, km/h
5. `core/print-table` formats output

## Auth & credentials

| File | Contents | Gitignored |
|------|----------|------------|
| `.auth.edn` | Client ID, client secret, session cookie | Yes |
| `.creds.edn` | OAuth tokens (auto-managed by atom watcher) | Yes |

- OAuth flow: open `api/start-auth` URL → get code → `api/exchange-code!`
- Token refresh: `api/refresh-token!`
- FIT download uses session cookie (`:session-cookie` in `.auth.edn`), not OAuth

## Rate limits

- 200 requests/15min overall, 2,000 daily
- 100 read requests/15min, 1,000 daily
- FIT download via `export_original` is not rate-limited (web endpoint)

## FIT parsing

CSVTool flags: `--data record -e -deg -se`. Outputs columnar CSV with one row per second. Parsed records are cached as EDN in `.cache/` keyed by activity ID. Sentinel values (65.535 speed, 12607.0 altitude) indicate missing data.

## Dependencies

| File | Deps |
|------|------|
| `bb.edn` | `org.babashka/http-client`, `dev.weavejester/medley` |
| `deps.edn` | `com.garmin/fit` (SDK for CSVTool) |

## No testing / linting / CI

This project has no test suite, linter, formatter, or CI pipeline.
