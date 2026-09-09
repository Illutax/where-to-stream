# 0012. Permanent Title Cache vs. TTL-Based Availability Cache

- **Date**: 2026-07-28
- **Status**: Accepted

## Context

The app caches two functionally different things in the DB, both globally (not per user, shared
across all watchlists):

1. **Title metadata** — poster image, FSK age rating, German title (`title_meta`,
   `title_poster`), fed by `TitleMetaService`/`PosterService` from **one** IMDb GraphQL fetch per
   title (`ImdbTitleClient`).
2. **Streaming availability** — which provider offers the title and how (flatrate/purchase/rental,
   price, language) (`query_meta`, `query_result`), fed by `StreamInfoService` from the
   werstreamt.es scraping.

Both tables look structurally similar (`imdb_id` key, a timestamp), but behave fundamentally
differently: title metadata (poster, FSK rating, original title) are facts that are fixed once a
film is released and are practically immutable.
Streaming availability, by contrast, changes constantly — licences expire, providers change,
prices change — and has to be re-queried regularly.

The architecture review of 2026-07-28 (`../reviews/2026-07-28-architecture-review.md`, finding F7)
initially criticised the fact that `title_meta`/`title_poster` have no expiry control and no
cleanup for positive hits.
For the title cache, however, that is **intended behaviour**, not a deficiency — but so far it was
never written down explicitly, only implicit in the Javadoc comments in
`TitleMetaService`/`PosterService` ("A positive result is permanent").
This ADR makes the decision explicit.

## Decision

There are deliberately **two different caching strategies** for the two kinds of data.

**1. Title metadata cache (`title_meta`, `title_poster`) — permanently valid, no TTL for positive hits.**

- A positive hit (poster found, rating found, German title found) counts as **permanent**: no TTL,
  no automatic refresh, no scheduled cleanup
  (`TitleMetaService.classify`/`PosterService.classify`: a positive row simply has no age check).
  This data consists of static facts about a film title.
- Only a **negative** result ("no poster/rating found") gets a TTL
  (`poster.negative-cache-days`, default 14 days, `isNegativeFresh(...)`) — once it expires, another
  attempt is made, because a miss is more often a temporary problem (rate limit, IMDb error, title
  not yet listed) than a permanent fact.
- No cleanup job, no coupling to the watchlist: if a title disappears from every watchlist, its
  cache entry stays.
  That is intended — the data is globally valid, and a renewed watchlist import of the same title
  benefits immediately from the existing cache instead of costing a new IMDb request.

**2. Streaming availability cache (`query_meta`, `query_result`) — TTL-based plus manual invalidation.**

- A hit counts as fresh for only `wer-streamt.invalidate.after-days` days (default 28)
  (`StreamInfoService.isFresh`); after that it is automatically re-scraped on the next access.
- In addition, an ADMIN can invalidate entries early and selectively
  (`POST /api/manage/invalidate`, which sets the `invalidated` flag on `query_meta`) and have them
  re-scraped selectively (`POST /api/manage/scrape`, `/manage` UI) — for example when
  it is known that an offer has changed before the 28 days are up.

## Consequences

**Easier / advantageous:**

- A clear conceptual separation: "what the film **is**" (permanent) vs. "**where it is currently
  running**" (volatile, has to be kept current).
- Title metadata fetches are capped at **one** IMDb request per title over the entire lifetime of
  an installation (apart from negative retries every 14 days) — a massive saving in IMDb requests
  compared to a TTL approach for data that is immutable anyway.
- A title that lands on a watchlist again (one's own or someone else's) never has to ask for the
  poster or the FSK rating again.

**Downsides / deliberately accepted:**

- `title_poster` BLOBs (thumb + full per title) grow without bound; there is no cleanup when a
  title disappears from every watchlist.
  At the current usage scale (personal watchlists, no bulk data) that is acceptable.
  Should the data volume become relevant, an administrative cleanup endpoint ("delete cache
  entries for titles that are no longer on any watchlist") would be the obvious extension —
  deliberately not built as long as there is no need for it (YAGNI).
- If a poster or rating that is already cached does change at IMDb after all (a correction to a
  wrong FSK value, say), there is currently no way to force the update other than deleting the DB
  row by hand — unlike the availability cache, there is no ADMIN UI invalidation for title
  metadata.
  An accepted trade-off, not an open bug.
