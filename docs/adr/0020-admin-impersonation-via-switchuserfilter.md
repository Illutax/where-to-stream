# 0020. Admin impersonation through Spring Security's `SwitchUserFilter`

- **Date**: 2026-09-06
- **Status**: Accepted (recorded 2026-09-09 — implemented and in operation)

## Context

An ADMIN should be able to see the application as another user for a while, in order to follow up
that user's reports without having to be given their password (TODO-53). This is not a matter of
convenience, it is an intervention into accountability: for as long as an impersonation is running,
the system sees actions under an identity that did not trigger them.

The situation:

- Authentication through Spring Security with form login, HTTP Basic and optionally Google OIDC
  ([ADR-0006](0006-authentication-and-authorisation.md)); sessions live in the database.
- The logged-in user is resolved in the presentation layer and passed down as a `userId`;
  below that nobody reads the `SecurityContext`
  ([ADR-0007](0007-a-watchlist-per-user.md), enforced by ArchUnit).
- ~~The eBay price lookup books two calls per title against a daily budget that all users share,
  and accounts for them per user~~ ([ADR-0017](0017-quota-management-for-the-ebay-browse-api.md)) —
  **gone (TODO-56).** This part of the situation no longer exists; see point 5.

Spring Security ships `SwitchUserFilter` (present in 7.1, verified). It swaps the authentication in
the `SecurityContext` and files the original one as a `SwitchUserGrantedAuthority` — which is what
the way back feeds on. A hand-rolled version would have to reproduce the same state machine,
including session handling and the return.

## Decision

**Impersonation through `SwitchUserFilter`, with four constraints that together tip the balance.**

### 1. No ADMIN impersonates an ADMIN

Whoever takes over another ADMIN can use that account's rights without the action being
identifiable as their own — a privilege escalation without a trace. The exclusion is the
conservative and verifiable choice and is enforced by a dedicated `UserDetailsChecker` on the
filter, that is, at the point where the target user is loaded, and not only in the UI.

### 2. Writing is allowed

A deliberate decision against the more cautious variant: a tool that cannot reproduce a reported
bug does not solve the problem it is being built for. Many reports concern exactly the writing
paths (import, changing the watchlist, settings).

The price is real and is not being argued away: **while an impersonation is running, changes to the
data cannot be told apart from the user's own.** The countermeasures are points 3 and 4 —
they make the process traceable and visible, they do not prevent it.

### 3. Beginning and end are logged, with both identities

Without that it cannot be decided afterwards whether a user did something themselves. Both events
go to `warn` — not because something is going wrong, but because a log entry that sits below the
usual threshold is no longer there when it actually matters.

### 4. The UI shows it permanently and unmissably

A switch one forgets about is the more dangerous mistake: the admin takes their own session for the
user's, or the other way round. A banner stays in place across all pages, names both identities and
carries the way out right next to it. `MeDto` transports the state so that the SPA knows it instead
of having to guess it.

### 5. Price lookups are blocked during an impersonation

> **Moot since 2026-09-07 (TODO-56).** The price lookup has been withdrawn — it ran, but turned
> out not to be good enough; its replacement is a search link that is built in the browser and
> consumes no quota. With that there is no longer any place where impersonation
> costs anything — the block, the special status and the `ImpersonationPort` it needed are gone
> without replacement. The other four points apply unchanged.
>
> The paragraph stays because the question "whose quota does an impersonated action book
> against?" will come up again with the next rate-limited feature — and the answer is likely to
> be the same.

The only place where impersonation costs money. Whose quota it would be booked against is a
question without a good answer: booked against the impersonated user, the admin consumes someone
else's budget and the user later hits a limit they have not used up; booked against the admin, it
requires the quota layer to know the original identity — and by ADR-0007 it deliberately does not.

Instead of building in either of the two blemishes, the lookup is dropped. The endpoint answers
with a status of its own, which the UI renders as a notice — not an error, but a stated
unavailability, like the other states of this endpoint too.

The check sits in the controller, not in the service: whether an impersonation is running is part
of the authentication, and reading that is the presentation layer's job under ADR-0007.

## Consequences

**What gets better**

- An admin can follow up a report without asking for the password — the stopgap used so far, which
  is worse than any impersonation.
- The path is standard functionality rather than a hand-rolled one; the state machine including the
  return comes from the framework.
- Impersonations can be reconstructed from the log.

**What gets harder**

- **The accountability of data changes goes down.** That is the direct consequence of point 2 and
  it is not fixable, only observable. Whoever wants to know afterwards who changed a watchlist
  has to consult the log; the data itself does not say.
- **A new, powerful endpoint.** It is restricted to ADMIN and excludes ADMIN targets, but it
  exists. A hijacked admin session thereby becomes more valuable than before.
- ~~**The blocked price lookup is a visible gap.**~~ Moot with the withdrawal of the price lookup
  (TODO-56): there is no price display left whose blocking could be noticed.
- **One more state in the frontend.** The banner and the way out want maintaining.
  The third point — the special status of the price lookup — is gone along with it.
- **Testability:** that ADMIN targets are refused is a guarantee only a test upholds —
  in operation its absence is only noticed once it is needed.

## Alternatives Considered

**Read-only impersonation.**
The safer variant: every writing endpoint refuses during an impersonation, accountability stays
untouched. Rejected because a considerable share of the reports concerns exactly the writing
paths — a tool that cannot trigger the reported bug does not save you from having to ask for the
password. The decision is reversible: the block would be a filter over the mutating endpoints and
could be retrofitted if practice argues against it.

**A custom implementation instead of `SwitchUserFilter`.**
Rejected: the same state machine, written ourselves, with the difference that bugs in it are ours.
Rebuilding security mechanics that the framework already brings along is the most expensive route
to a worse result.

**Letting the admin reset passwords instead of impersonating.**
Already exists (`resetPassword`) and is the status quo. Rejected as a substitute, because it locks
the user out in order to help them — and because a reset password gives the admin permanent access
where an impersonation is a limited, visible session.

**Impersonation behind a feature flag.**
Considered, in order to close the attack surface in environments that have no need for it. Rejected
for this project: with five users and one installation, a flag that is never flipped is above all a
path that is never tested.
