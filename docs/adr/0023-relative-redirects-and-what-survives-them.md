# 0023. Take Tomcat's relative redirects, and assert forwarded headers on what survives them

- **Date**: 2026-10-17
- **Status**: Accepted

## Context

We run behind a TLS-terminating reverse proxy, so the scheme and host the client actually used only
arrive in `X-Forwarded-*`.
`server.forward-headers-strategy=native` (Tomcat's `RemoteIpValve`) rewrites the request from those
headers, and `ProxyForwardedHeadersTest` exists because getting that wrong fails quietly rather than
loudly — TODO-61 in [`DONE.md`](../../DONE.md).

Spring Boot 4.2.0-M2 changed a default that the test had been reading through:
`server.tomcat.servlet.use-relative-redirects` now defaults to `true`, matching Tomcat's own
default (it is also a rename — `server.tomcat.use-relative-redirects` is the deprecated
predecessor).
Until then Spring Boot forced it to `false`, so Tomcat expanded every context-relative redirect into
an absolute URL, and built that URL from the request — which is to say, from whatever the valve had
rewritten it to.
The test asserted on exactly that expansion, and both of its redirect cases went red on the upgrade.

**The property governs less than it first appears.** Measured against the running application in all
three deployment shapes, under both values:

| Flow | `relative=true` (the new default) | `relative=false` |
| --- | --- | --- |
| `GET /app/` unauthenticated → login | `/login` | `https://w2s.example/login` |
| **`POST /login` ok → saved request** | **`https://w2s.example/app/?continue`** | **`https://w2s.example/app/?continue`** |
| `GET /` → SPA | `/app/` | `https://w2s.example/app/` |
| `POST /logout` | `/login?logout` | `https://w2s.example/login?logout` |
| `GET /api/**` unauthenticated | `401`, no redirect | `401`, no redirect |

The bold row is the reason this ADR exists.
Tomcat only expands redirects the application hands it as a **path**.
Spring Security's `SavedRequestAwareAuthenticationSuccessHandler` hands `sendRedirect` a URL it has
already built in full from the saved request, so that one is absolute either way — and still built
from the valve's view of the request.
It is also the precise flow TODO-61 was opened for: after an expired session, a successful login has
to land on the dashboard, on the scheme the browser actually used.

A second signal is untouched for the same reason: the `Secure` flag on the `SESSION`, `XSRF-TOKEN`
and `remember-me` cookies follows `request.isSecure()`, which the valve sets from
`X-Forwarded-Proto`.
Behind the proxy the cookies come back `Secure`; on a direct plaintext hop they do not.
Tomcat's redirect handling never touches it.

And nothing else in the backend builds absolute URLs — no `ServletUriComponentsBuilder`, no
`fromCurrentRequest`.
After this change the forwarded headers carry exactly those two things, and both keep working.

Behaviour per deployment shape, with the new default:

- **Behind the proxy** — `/login` instead of `https://w2s.example/login`. The browser resolves the
  path against the URL it used, which is the proxy's, so the result is identical. The difference is
  that the scheme downgrade the valve was configured against is no longer *reachable* for these
  redirects, even if the valve fails to recognise the peer.
- **Direct over http** — `/login` instead of `http://localhost:8080/login`. No behavioural change.
- **Direct over https** (measured with a self-signed certificate, no proxy) — `/login` instead of
  `https://localhost:8443/login`. No behavioural change either; the absolute form was already right
  there, because `getScheme()` returns `https` without anyone's help.

A relative `Location` has been legal since RFC 7231 (2014); browsers, Caddy and `java.net.http`
all follow it.

## Decision

**Take the new default.** We do not pin `use-relative-redirects`.
Behind a proxy a relative redirect is the more robust of the two: there is no scheme and no host in
it to be wrong.

**The test asserts the two signals that outlive the redirect form**, not the redirect form itself:

1. the absolute `Location` of the post-login saved-request redirect, and
2. the `Secure` flag on the session cookie, present behind the proxy and absent without it.

Both were checked against a run with `forward-headers-strategy=none`, where both fail — a test for
a quiet failure is worth only as much as its demonstrated ability to go red.

**`ProxyForwardedHeadersTest` deliberately asserts nothing about whether a redirect is relative or
absolute.** That is what tied it to a framework default last time. Both of its signals hold on
either side of the upgrade, so the version bump itself does not turn it red.

**No property pin, in either direction.** A pin would also have to be written twice to cross the
upgrade: on 4.2.0-M1 only the old name `server.tomcat.use-relative-redirects` exists, and an
unknown property key is ignored in silence rather than rejected — a pin spelled the new way would
look correct and do nothing.

The cost of the move: the test now has to log in — fetch the CSRF cookie, post the form — where it
previously got its evidence from a single unauthenticated `GET`.

## Consequences

**Easier:**

- The upgrade is no longer a behavioural decision smuggled in through a default. It happens, and
  the test keeps asserting the same two things before and after.
- The flow that is tested is the flow that actually broke (TODO-61), not a cheaper stand-in that
  happened to expose the same scheme.
- The `Secure` flag is now covered at all. It was the signal with the sharper edge — a session
  cookie without it is one the browser will also send over plain http — and nothing had been
  watching it.

**Harder:**

- The test carries a small HTTP harness: a cookie jar, a form login, a CSRF header. It keeps its
  own jar rather than using `java.net.CookieManager`, because behind the proxy headers the session
  cookie comes back `Secure` and a conforming cookie store would then refuse to send it over the
  plaintext hop the test actually speaks. That subtlety now lives in the test and has to be
  understood by whoever edits it next.
- `Location` headers are no longer self-explanatory in a log or a `curl -i`. Reading `/login` tells
  you nothing about which host the client was on; you have to look at the request to know.
- Should the application ever start building absolute URLs itself — an e-mail link, an OIDC
  `redirect_uri`, a sitemap — that code depends on the forwarded headers again, and this ADR's
  claim that "exactly two things" depend on them stops being true.

## Alternatives Considered

**Pin `server.tomcat.servlet.use-relative-redirects=false`** and keep the old test unchanged.
This was implemented first and does work — the release notes name it as the way to restore the old
behaviour. It was dropped because it preserves the more fragile of the two behaviours for no gain
beyond leaving a test alone, and because it would have to be spelled with two different property
names across the upgrade to be effective on both sides of it.

**Keep the entry-point redirect as the signal, and pin the property only in the test.**
Rejected outright: the test would then assert a behaviour production no longer has, which is the
one thing worse than no test.
`src/test/resources/application.properties` already repeats `forward-headers-strategy` for exactly
the opposite reason — to stop the test passing against a default we do not ship.
