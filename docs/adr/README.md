# Architecture Decision Records (ADRs)

These ADRs record **why** a given architectural decision was made — context, options, trade-offs —
not **how** the system is implemented.
Format: [Nygard ADR](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions),
matching the `adr` skill in the team documentation.

Every ADR is checked against the code and translated as of 2026-09-09; what that review found is
recorded in TODO-67 in [`DONE.md`](../../DONE.md).
`DocumentationConsistencyTest` keeps this table and the files in step.

| No. | Title | Status |
| --- | --- | --- |
| [0001](0001-hash-routing-for-the-angular-client.md) | Hash routing for the Angular client | Accepted |
| [0002](0002-frontend-build-via-exec-maven-plugin.md) | Frontend build via exec-maven-plugin (system Node) | Accepted |
| [0003](0003-time-through-a-timeservice-facade.md) | Time through a TimeService facade instead of static now() calls | Accepted |
| [0004](0004-vitest-as-the-angular-test-runner.md) | Vitest as the test runner for the Angular client | Accepted |
| [0005](0005-assertj-and-mockito-in-the-backend.md) | AssertJ (with Mockito) for backend tests | Accepted |
| [0006](0006-authentication-and-authorisation.md) | Authentication and authorisation | Accepted |
| [0007](0007-a-watchlist-per-user.md) | A watchlist per user | Accepted |
| [0008](0008-remove-the-thymeleaf-client.md) | Remove the Thymeleaf client — SPA-only UI | Accepted |
| [0009](0009-domain-value-objects-instead-of-primitives.md) | Domain value objects instead of primitive types | Accepted |
| [0010](0010-optionals-must-not-default-to-null.md) | Don't default Optionals to null | Accepted |
| [0011](0011-no-open-session-in-view.md) | No Open Session in View, no lazy loading | Accepted |
| [0012](0012-permanent-title-cache-vs-ttl-availability-cache.md) | Permanent title cache vs. TTL-based availability cache | Accepted |
| [0013](0013-effects-for-ongoing-sync-not-one-shot-bootstrap.md) | effect() for ongoing sync, not for one-shot bootstrapping | Accepted |
| [0014](0014-backend-by-bounded-context-and-ports-adapters.md) | Backend by bounded context, with pragmatic ports and adapters | Accepted |
| [0015](0015-self-validating-commands-instead-of-scattered-request-validation.md) | Self-validating commands instead of scattered request validation | Accepted |
| [0016](0016-asynchronous-deferred-cache-refresh.md) | Asynchronous, deferred refresh of the availability cache | Accepted |
| [0017](0017-quota-management-for-the-ebay-browse-api.md) | Quota management for the eBay Browse API | Superseded |
| [0018](0018-dirty-checking-instead-of-an-explicit-save.md) | Dirty checking instead of an explicit save() | Accepted |
| [0019](0019-port-spi-for-inverted-context-dependencies.md) | port.spi for inverted context dependencies instead of parking them in shared | Accepted |
| [0020](0020-admin-impersonation-via-switchuserfilter.md) | Admin impersonation via Spring Security SwitchUserFilter | Accepted |
| [0021](0021-track-one-node-lts-major-checked-by-a-test.md) | Track one Node LTS major, checked by a test | Accepted |

New ADR: next free four-digit number, `NNNN-short-slug.md`, written in English.
