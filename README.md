# Strava FIT Analyzer

Babashka CLI for downloading and analyzing Strava activities from FIT files.

## Setup

1. Install [Babashka](https://github.com/babashka/babashka) and [Clojure CLI](https://clojure.org/guides/install_clojure)
2. Create a Strava API app at https://www.strava.com/settings/api
3. Create `.auth.edn`:
   ```edn
   {:client-id 12345
    :client-secret "your-secret"
    :session-cookie "your-strava4-session-cookie"}
   ```
4. Authorize: open the URL from `strava.api/start-auth` in browser, then run `(api/exchange-code! "CODE")`

## Usage

```bash
# Sync activities for a month
bb -e '(require (quote strava.repo)) (strava.repo/sync-month 2025 3)'

# Analyze an activity (1-minute buckets)
bb eff --pattern 2025-03-09 -i 60

# With time range
bb eff --pattern 2025-03-09 -i 60 --from 1h --to 2h
```

## Output

```
      Time   HR     Pace  km/h      EF   cad  slen   pts
      0h0m  145    4m55s  12.2  0.05034   91  1060    60
      0h1m  148    4m50s  12.4  0.05024   93  1080    60
```

EF = Efficiency Factor (m/min / HR). Higher is better.
