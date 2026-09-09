# 0017. Quota management for the eBay Browse API

- **Date**: 2026-09-05
- **Status**: Superseded (2026-09-07)

> **Moot because the feature is gone — not because the decision was wrong.**
> The developer account was granted, the price lookup ran, and the quota management described here
> was in operation together with it.
> In practice the feature turned out not to be good enough and has been withdrawn with TODO-56;
> the two tables have been removed by changeset.
>
> Its place is taken by a search link per title (TODO-57) that is built in the browser:
> no request, no budget, no account — and therefore nothing left to ration.
> The question this ADR answers no longer arises.
>
> The document stays because the considerations on dividing up a shared quota
> and on the provider's own report taking precedence over our counter are reusable,
> should a rate-limited third-party API ever come along again.

## Context

The planned "eBay price lookup per title" feature (see
[`docs/EBAY_PRICE_LOOKUP_PLAN.md`](../EBAY_PRICE_LOOKUP_PLAN.md)) fetches, for a given title, the
cheapest buy-it-now offer and the lowest running auction bid.
The source was initially meant to be scraping the eBay search results page.
A POC disproved that option: after some 40 requests in 25 minutes the egress IP was blocked across
all marketplaces, and requests a minute apart ran into an Akamai challenge without exception, with
the access never recovering.
The integration therefore goes through the **eBay Browse API**.

This trades a risk of being blocked for a hard but predictable limit:

- The standard quota is **5,000 calls per day and application** — not per user.
  All users share this budget.
- A title lookup currently costs **two** calls
  (`buyingOptions:{FIXED_PRICE}` and `buyingOptions:{AUCTION}`).
  Whether the two can be combined into one call is unverified; until then a factor of 2 applies.
- More quota is only available through eBay's "Application Growth Check".

Without a brake this budget is exhausted quickly: a single watchlist of 200 titles consumes 400
calls on a "look everything up once", which is 8% of the daily budget for all users combined.

The project as it stands offers **no** basis for this:

- **A per-user rate limit on incoming API endpoints does not exist anywhere.**
- The existing `RateLimiter` (`shared/platform/outbound/RateLimiter.java`) is nothing but a spacer
  between *outgoing* requests of one integration
  (`acquire()` blocks until the next slot).
  It knows neither users nor daily budgets and cannot be extended for this task without diluting
  its purpose.
- `TimeService` (`shared/platform/time`, [ADR-0003](0003-time-through-a-timeservice-facade.md)) provides
  `now()` and `today()`, where `today()` uses the **system time zone**.
  An eBay quota day, however, is not a day in the system zone (see below).

Two requirements from the client are fixed:

1. **Distribution formula** `requests_pro_nutzer = 5000 / (anzahl_der_nutzer / 2)`, i.e. `10000 / n`.
   This is a **deliberate twofold overbooking**, on the assumption that at most half of the
   registered users look up prices on a given day.
   It trades unused quota for a higher per-user limit.
   For scale: the application currently has **five** registered users,
   and that number is not going to grow noticeably any time soon.
2. **A global cap at exactly 5,000**, with no safety margin.

It follows necessarily from the overbooking that a per-user limit alone does **not** protect the
quota: if more than half of the users use up their limit, the first active users exhaust the shared
budget.

What remains unclear, finally, is **when eBay resets the daily quota**.
The working hypothesis is "12 o'clock Pacific time", it comes from an answer given by Gemini and is
backed by no primary source.
It is also ambiguous: "12 o'clock" can mean noon or midnight.
To make matters worse, Pacific time alternates between PST (UTC−8) and PDT (UTC−7).

## Decision

We build the quota management as a **self-contained part of the new bounded context
`purchaseoffers`**, with two levels of counters, its own persistence and a clear precedence rule
with respect to eBay.

### 1. Two levels of counters

- **Per-user daily counter**: `10000 / n` requests per user and quota day.
- **Global daily counter**: a hard cap at **5,000** requests per quota day,
  which kicks in before eBay itself refuses.

`n` is the number of registered users according to the database,
**determined once per quota day and frozen for that day**.
Users who are added over the course of the day immediately get the same limit as everyone else;
existing limits are not recalculated.

The user count is obtained through a `port.in` of the `accountaccess` context,
not by direct access to that context's data
([ADR-0014](0014-backend-by-bounded-context-and-ports-adapters.md)).

### 2. eBay's answer beats our own counter

Our global counter is an **estimate** of consumption, eBay's quota state is the
**fact**.
The two drift apart — through retries, through parallel requests, through calls that fail on the
way, and through every restart of the application.

If eBay reports quota exhaustion, the quota day counts as **over immediately**,
regardless of our own counter.
From that point on, no further call goes out until the next reset.

It is precisely this precedence that replaces the safety margin on the global cap:
whoever miscounts notices it from the quota response rather than from a buffer.

### 3. Persistence in two tables of its own — not on the user

The quota state is persisted, in **two narrow tables in the
`purchaseoffers` context**:

- one row per quota day for the global consumption and the time of exhaustion,
- one row per quota day and user for the per-user consumption.

Explicitly **not** as columns on `AppUser`:
the user belongs to `accountaccess`, the quota budget to `purchaseoffers`.
A column on the user would put state of one context into the other's entity,
violate the isolation rule from ADR-0014 and write to the user table on every price lookup.

It is persisted because the state has to survive a restart:
otherwise an evening deploy would lift the daily block, and the application would run once more
against a quota that eBay considers long exhausted.
The same argument applies to the per-user counters — if only they lived in memory, every deploy
would hand every user a fresh budget, and the global cap would be the only real protection.

This is a **deliberate exception** to the plan's rule that this feature stores nothing.
That rule is about **price data**: a bid changes by the minute, and a buy-it-now offer with a
quantity of 1 is gone entirely once it sells — a persisted price would not be "slightly older",
it would be wrong.
Quota state is the opposite: stable for a day, tiny, and useless without persistence.
For offers and prices it stays at **no** table.

### 4. The quota day is a day in Pacific time

The quota day is derived from `TimeService.now()` together with a **configured time zone**
(`America/Los_Angeles`) and a **configured reset time**
(`ebay.quota.reset-time`, default `00:00`).

- **Not** `LocalDate.now()` — ADR-0003 forbids that.
- **Nor** `TimeService.today()`: that returns the day in the system zone,
  and the quota day is not one.
  `TimeService` stays unchanged; the zone logic belongs in `purchaseoffers`.
- **Zone instead of offset:** a hard-wired UTC−8 is an hour off during daylight saving time —
  and off in exactly the direction where a reset that comes too early runs against a quota that is
  still exhausted.
- **The time is configurable** because the "12 o'clock" hypothesis is ambiguous.
  If the reading is wrong, the correction costs a configuration value instead of a release.

If the Browse API sends along a header with the reset time, that header takes precedence over the
configuration.

### 5. Exhaustion degrades visibly, it does not throw

Neither a per-user limit that has been reached nor an exhausted global budget leads to an exception
in the user's request.
The endpoint answers normally with a "budget exhausted" state, and the frontend shows a
comprehensible message — analogous to the "currently unavailable" state.
Per-user and global exhaustion are **distinguished** in the process, because the difference matters
to the user.

### 6. Logging as a data source for the open question

Whenever eBay reports quota exhaustion, the status code, error code, error text and
**all** quota headers eBay sends along are logged at `warn`.
This is not for debugging but for settling the reset time empirically:
two such events, together with the next successful call after each, narrow down the reset window
without provoking anything.

## Consequences

**What gets easier:**

- The quota is protected against exhaustion by individual users, and protected server-side —
  client-side limiting would have been useless.
- The daily block survives deploys and restarts.
  An evening deploy can no longer "release" the quota by accident.
- A wrongly guessed reset time has no consequences for correctness:
  if the reset happens later than assumed, eBay reports the quota as exhausted and the day is
  closed again.
  It costs a few futile calls, not the feature.
- The quota logic lies entirely in `purchaseoffers` and is thereby isolated in a way ArchUnit can
  check.
- Per-user limits can be explained, because they do not change within a day.

**What gets harder:**

- **The project gets its first incoming rate limiting.** That is new code to be tested in its own
  right — including the concurrency of two counters.
- **There is persistence in a feature that was explicitly supposed to have none.** The difference
  between "do not store price data" and "store quota state" has to be understood,
  otherwise it looks like a contradiction. This ADR is where it is recorded.
- **The per-user table grows without bound.**
  A cleanup rule is deliberately deferred to a later stage:
  the usage figures are informative in retrospect — how often the assumption behind the formula
  breaks, how consumption is distributed across users, whether the global cap ever kicks in at all.
  At `n` = 5 that is around 1,800 rows a year, so not a size problem.
  Two points remain to be kept in mind nonetheless:
  this is user-related usage data with no deletion deadline,
  and when a user is deleted their rows should go with them.
- **The overbooking remains a bet.** If the assumption behind the formula does not hold, the global
  cap kicks in — and then the feature is off for everyone, not just for the heavy users.
  How often that happens has to be observed.
- **The time zone logic still has to be tested, but its impact is defused.**
  An error only takes effect twice a year and only for an hour, and that is exactly the case caught
  by the precedence of eBay's quota response (point 2):
  a reset that comes too early costs a few futile calls, then the day closes again.
  It should still be tested with a fixed clock against both time zone states — the test is cheap,
  and without it an error would never be noticed for lack of a symptom.
- **At the current `n`, the per-user limit is not a fairness brake.**
  With the five registered users of today the formula yields 2,000 requests per user,
  i.e. 1,000 title lookups — three heavy users active at the same time already reach the global
  cap.
  The client has accepted this: the number of users is known and will not grow noticeably any time
  soon, and against the case actually feared — a script with a valid session cookie —
  the per-user limit works unchanged.
  In this constellation the protection of the quota comes from the global cap;
  the distribution formula only serves its purpose at a larger `n`.

**Open, deliberately not decided here:**

- The exact error code on quota exhaustion (probably `2001`/`RATE_LIMIT`) and which
  quota headers the Browse API sends along — to be verified against the sandbox.
- Whether both offer types can be queried in one call.
  That would double the title budget, but changes nothing about this decision.
- Whether the reset actually happens at 12 o'clock Pacific time.

## Alternatives Considered

**Per-user counters as columns on `AppUser`.**
Obvious, because the limit "hangs off the user".
Rejected: `AppUser` belongs to `accountaccess`, the quota budget to `purchaseoffers`.
That would have violated the isolation rule from ADR-0014, written to the user table on every price
lookup and spread the quota logic over two contexts.
Besides, the columns would be pointless after a change of offer source, but hard to get rid of
again.

**Purely in-memory counters, no persistence.**
The cheapest option and in line with "this feature stores nothing".
Rejected for the global exhaustion state, because every restart would lift the daily block.
For the per-user counters it would have been defensible — rejected for consistency:
a deploy would otherwise hand every user a fresh budget and make the global cap the only real
protection.

**Only a global cap, no per-user limit.**
Considerably less code.
Rejected because a single user — or a script with a valid session cookie — can then use up
everyone else's daily budget.
That is exactly the case the distribution formula exists for.

**A safety margin on the global cap (e.g. 4,800 instead of 5,000).**
Was the original proposal in the plan.
Rejected by the client in favour of the exact value, backed by the precedence of eBay's
answer (point 2).
The margin would have given away 200 calls a day to cover a case that point 2 catches anyway.

**Extending the existing `RateLimiter`.**
Rejected: the `RateLimiter` keeps intervals between outgoing requests and is deliberately close to
stateless.
Daily budgets, a user reference and persistence would have turned it into something else and
affected all four existing integrations.
The outgoing spacer stays unchanged and is used for the eBay integration in addition.

**Bucket4j, Resilience4j or Redis-backed limiting.**
Rejected as disproportionate: the application runs as a single instance, the limit is a simple
daily counter, and each of these options would bring in a dependency or an infrastructure
component for logic that consists of two counters and a table.
If we ever move to multiple instances this decision has to be reassessed — the DB persistence from
point 3 already carries part of the load then.
