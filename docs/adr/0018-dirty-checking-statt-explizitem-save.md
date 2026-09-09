# 0018. Rely on Hibernate's dirty checking instead of an explicit `save()` for loaded entities

- **Date**: 2026-09-05
- **Status**: Accepted (added retrospectively on 2026-09-09 — implemented and in production)

## Context

The application uses Spring Data JPA. Transactions sit on the methods of the application layer
(`@Transactional`), and [ADR-0011](0011-kein-open-session-in-view.md) switched Open Session in View
off — so the persistence context ends **exactly at the transaction boundary** and not only when the
response is rendered.

The basic JPA rule therefore applies without qualification: an entity loaded **inside** a
transaction is *managed*. At commit, Hibernate compares it against its loaded state and writes the
changes itself. In that case a `repository.save(entity)` is not an instruction but a repetition of
what happens anyway.

For a **newly constructed** entity the opposite holds: it is *transient*, Hibernate doesn't know it,
and without an explicit `save()` it disappears without a trace.

The point came up during the quota management for the eBay integration
([ADR-0017](0017-quota-verwaltung-fuer-die-ebay-browse-api.md)) and has **not been decided** so far.
The situation in the code is inconsistent, with a clear majority on the redundant side:

- `UserPreferencesService.update(...)` loads an `AppUser`, mutates it via a `Consumer` and then calls
  `users.save(user)` — inside a `@Transactional` method.
- The same pattern in `UserAdminService.deactivate(...)`, `PosterService` (two places),
  `TitleMetaService` and `WatchlistImportService`.
- Alongside them are the legitimate cases: `AdminUserSeeder`, `GoogleOidcUserService`,
  `StreamInfoService` and the `of(...)` branches in `PosterService`/`TitleMetaService` save
  **newly created** entities, where `save()` is mandatory.

Without a ruling, every change decides this anew. And this is not merely a matter of taste: the
redundant call is **misleading**, because it suggests the write depends on it — and whoever believes
that draws the wrong conclusions as soon as an entity is *detached*.

## Decision

**Entities loaded within a transaction are mutated and not saved.
Hibernate's dirty checking does the writing at commit.**

In detail, and binding:

1. **No `save()` for a managed entity.**
   If the entity was loaded via a repository in the same transaction, mutating it is enough.
2. **`save()` remains mandatory for transient entities.**
   Anything created with `new` or an `of(...)` factory has to be saved explicitly, otherwise it is
   lost.
3. **The transaction has to be a writing one.**
   Mutating methods carry `@Transactional`; `@Transactional(readOnly = true)` is reserved for read
   methods and suppresses the flush.
4. **Loading and mutating belong in the same transaction.**
   An entity passed across a transaction boundary is detached; changes to it are silently dropped.
   Where that is unavoidable, it is commented and handled explicitly (`merge`/`save`), not left to
   chance.
5. **Where leaving it out is not obvious, it is commented.**
   A mutation with no repository call after it looks like a forgotten call in review. A brief note
   ("managed, dirty checking writes at commit") costs one line and saves the question.

### Applying this to the existing code

New code follows the rule from now on. The six existing occurrences will **not** be converted in one
go, but according to the boy-scout convention: whoever touches one of these methods anyway cleans it
up along the way — in a separate commit, apart from the functional reason for the change.

The reason for this approach: the conversion is **not purely mechanical**. At every occurrence one
has to check whether the entity really was loaded in the same transaction. Blanket removal of all
`save()` calls would sweep up exactly those cases where the call carries weight.

## Consequences

**What gets better**

- **The code tells the truth.** In future a `save()` only appears where nothing would be written
  without it. That makes the insert paths visible instead of letting them drown in the noise.
- **Less pointless database work.** For entities with an *assigned* rather than a generated key — in
  this project `QueryMeta`, `TitlePoster`, `TitleMeta` and the quota tables from ADR-0017, for
  example — Spring Data's `save()` decides via `isNew()` and takes the `merge` branch for an
  already loaded entity. That is an extra call with no benefit.
- **One question less per review.** The rule is short and can be looked up.

**What gets harder — and this is the serious part**

- **A mistake is silent.** Anyone who applies the rule but loaded the entity outside the transaction
  loses the change **without an exception and without a log entry**. The explicit `save()` call would
  have caught that mistake. So we are trading clarity for a class of bug that is harder to notice —
  deliberately, because ADR-0011 has already made the transaction boundaries tight and explicit.
- **Mockito tests cannot verify this.** A unit test with a mocked repository sees no dirty checking;
  it can only attest that **no** `save()` happened, not that anything was written. Where the writing
  itself is the promise, it takes a test against a real persistence layer.
- **The existing code stays inconsistent for a while.** Two patterns side by side are more confusing
  for readers than one consistently redundant one. That is the price of the boy-scout approach; the
  alternative would be one large, risky bulk change.
- **The rule cannot be enforced automatically.** Whether an entity is managed is not in the
  bytecode. At best an ArchUnit rule could count `save()` calls, not judge them. This rule lives off
  review.

## Alternatives Considered

**Keep calling `save()` explicitly everywhere.**
The status quo at five of the six occurrences, and not without an argument: the call is defensive,
makes the intent to write visible, and still works even if an entity is unexpectedly detached.
Rejected because that is exactly how it creates **false confidence**: it suggests that persisting
depends on it, and it obscures the difference between managed and detached — the difference that
actually matters. Someone who never learned that dirty checking exists will eventually write code
that relies on `save()` of a detached object and thereby silently overwrites an object without a
version check.

**Turn the rule around: always `save()`, and switch dirty checking off instead.**
Technically conceivable via `@org.hibernate.annotations.Immutable` or a custom flush regime.
Rejected as a fight against the persistence provider: dirty checking is not an add-on to JPA, it is
its core. Switching it off would mean using an ORM and refusing it at the same time.

**An explicit `flush()` instead of `save()`.**
Rejected: `flush()` controls *when* the write happens, not whether anything is written.
Reinterpreting it as a declaration of intent would be yet another misleading signal — with the added
damage that flushing early holds locks longer than necessary.

**Enforce the rule via ArchUnit.**
Rejected because it is not decidable: whether a `save()` argument is managed or transient follows
from the control flow, not from the structure. A rule that forbids all `save()` calls in the
application layer would forbid the necessary insert paths along with them.
