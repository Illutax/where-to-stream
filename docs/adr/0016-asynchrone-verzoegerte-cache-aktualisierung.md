# 0016. Asynchronous, deferred refresh of the availability cache instead of a synchronous reload on page load

- **Date**: 2026-07-30
- **Status**: Accepted

## Context

[ADR-0012](0012-permanenter-titel-cache-vs-ttl-verfuegbarkeits-cache.md) established that the
streaming availability cache (`query_meta`, `query_result`) is TTL-based
(`wer-streamt.invalidate.after-days`, default 28 days) and can additionally be invalidated early
and selectively by an ADMIN (`POST /api/manage/invalidate`, the `/manage` UI, "Cache Verwalten").

What that ADR does not record: **how** an expired or invalidated entry actually gets reloaded.
Today this happens exclusively synchronously, inside whichever HTTP request happens to ask for it
first:

- `StreamInfoService.resolveAll(imdbIds)` (called by `CatalogOverviewService.overview()` for the
  dashboard and by `AggregateService.getAll()` for the provider pages) loads the currently valid
  `QueryMeta` rows; for every hit that is missing, invalidated or past its TTL,
  `StreamInfoService.resolve(imdbId)` is called **in the same request**, which synchronously
  scrapes `WerStreamtEsSource.query(imdbId)` (through the shared `RateLimiter`, default
  2 req/s) and persists the result before the request answers.
- Because the cache is **global** (keyed by `imdbId` only, not per user), a single dashboard call
  by any user is enough to immediately re-cache every invalidated/expired title on that user's
  watchlist — regardless of whether and when an ADMIN wanted to scrape deliberately via
  "Cache Verwalten".

This has two consequences that this ADR addresses:

1. **The "Cache Verwalten" page has no observable effect.** An ADMIN invalidates titles in order to
   re-scrape them deliberately — but as soon as anyone (often the ADMIN themselves while testing)
   opens the dashboard, the invalidated state has already been resolved by the implicit reload
   before the manage page's "Scrapen" button would have had anything to do. The page appears
   ineffective even though it works technically — it is simply overtaken all the time by the
   automatic dashboard logic.
2. **A page load can block for an arbitrarily long time.** If many titles have expired or been
   invalidated at the same time (e.g. right after a large watchlist import or a bulk invalidation
   from the manage page), the next dashboard request potentially has to reload dozens of titles
   serially against the rate-limited external scraper (`parallelStream` only parallelises across
   worker threads; the request itself still waits for the slowest result) before it answers at all.

## Decision

We separate **"show cached data"** from **"refresh stale data"**:

1. For an expired/invalidated but **existing** cache entry, `StreamInfoService.resolveAll(...)`
   immediately returns the cached (possibly stale) values instead of blocking, and marks the result
   as `stale`. Only a title that has **never** been cached is still resolved synchronously (there is
   nothing else to show).
2. For every title recognised as `stale`, a refresh is kicked off in the background (`@Async`) —
   deduplicated against parallel requests for the same title — using the same, already existing
   `resolve(imdbId, forceRefresh = true)` path.
3. The dashboard and the provider pages show a small notice banner when the displayed data is
   (partly) stale — one aggregate piece of information per page, no per-row marking (YAGNI: don't
   build more visibility than was asked for).
4. In addition to the demand-driven (page-load-triggered) refresh, a **scheduled job** proactively
   takes care of refreshing titles nobody looks at any time soon: it runs on a coarse cadence
   (initially: daily) and only updates titles whose TTL **plus a random jitter factor between 1.5x
   and 2x of `wer-streamt.invalidate.after-days`** has already passed, as well as all manually
   invalidated titles. The jitter is rolled **once, when a cache entry is written**, and persisted
   (`due_for_refresh_at`), rather than being recomputed on every job run — this spreads out the
   refresh times of many titles imported/cached at the same time instead of letting them expire
   together in lockstep (thundering-herd avoidance), and it stays reliably traceable (the same entry
   always has the same due time, no matter how often the job has run since).

The full implementation plan (phases, affected classes, config, migration, tests) is in
[`docs/CACHE_REFRESH_PLAN.md`](../CACHE_REFRESH_PLAN.md).

## Consequences

**Simpler / beneficial:**

- The "Cache Verwalten" page gets its purpose back: invalidating + scraping deliberately remains the
  only way to trigger a re-scrape **immediately and reliably**; a dashboard page load no longer
  pre-empts it.
- Page loads stay fast and predictable — no request blocks on an unknown number of external scrapes
  any more.
- Titles nobody actively looks at don't go stale indefinitely (the scheduled job picks them up
  eventually), without causing constant, pointless load in the process (coarse cadence, jitter, no
  action when "nothing is due").

**Drawbacks / deliberately accepted:**

- For a short while (until the background refresh is through), users explicitly see stale data
  instead of guaranteed fresh data — hence the new banner, so that this is visible rather than
  silently wrong.
- `@Async`/`@EnableAsync` and `@Scheduled`/`@EnableScheduling` appear in the project for the first
  time (not used so far) — new infrastructure that wants testing and operational observation (size
  the executor, log job runs).
- A new `due_for_refresh_at` column on `query_meta` (Liquibase migration).
- Somewhat more complexity in `StreamInfoService` (stale-vs-fresh distinction, in-flight tracking
  against duplicate parallel refreshes of the same title).

## Alternatives Considered

- **Change nothing, just make the manage page more informative** (a timestamp instead of a boolean,
  but leave the dashboard behaviour untouched): fixes only the cosmetic symptom, not the actual
  redundancy — the page would still be overtaken by the dashboard all the time. Adopted as part of
  this plan anyway (phase 1), because the information is useful in its own right, just not
  sufficient as the sole solution.
- **Refresh only via a scheduled job, no demand-driven async path on page load**: simpler, but a
  title that was just invalidated and is looked at right away would stay stale until the next job
  run, even though a user is actively looking at it — worse UX for the normal case.
- **Roll the jitter afresh on every job run instead of persisting it on write**: saves the new
  column, but makes an entry's due time depend on the random logic of the particular run rather than
  on a stable property readable off the entry itself — harder to test and to follow. Rejected in
  favour of the persisted value.
