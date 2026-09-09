# 0013. `effect()` only for ongoing synchronisation, not for one-off bootstrapping

- **Date**: 2026-07-29
- **Status**: Accepted

## Context

Up to now, `app.ts` adopted the user preferences from the loaded principal like this:

```ts
effect(() => {
  const me = this.auth.me();
  if (me) {
    this.userPrefsStore.init(me);
  }
});
```

That looks like ordinary signal reactivity, but semantically it is something else: `auth.me()`
goes from `null` to a value exactly once per app lifetime (no re-login without a reload),
i.e. the effect is meant to react **once**, when the asynchronously loaded data arrives — not to
keep synchronising against changes.

This category error made a real bug possible: `UserPrefsStore.init()` internally read its own
`_prefs` signal (for `applyTheme`).
That read registered itself as a dependency of the calling effect — with the consequence that
*every* later `setViewMode()`/`setTilesPerRow()`/… (which write `_prefs`) re-triggered the effect,
which called `init(me)` again with the original, by then stale `me` snapshot
and instantly reverted the change that had just been made.
The short-term fix was `untracked(() => …)` in `init()` — correct and deliberately
kept (it protects every future caller of `init()`), but it only removes the symptom
at that one place.
The actual cause — an `effect()` that structurally picks up *every* new, accidentally
introduced dependency even though only a one-off action was meant — remains in place for the next
similar case (every future "load X once, as soon as Y is available" effect can repeat the same
mistake).

## Decision

**`effect()` is reserved for ongoing, genuinely repeatable synchronisation** ("whenever
signal X changes, do Y") — e.g. tying the UI language to `userPrefsStore.language()`
(`app.ts`) or loading title metadata as soon as preference or id signals change
(`core/title-meta.ts`).

**One-off actions triggered by the arrival of asynchronous data** ("once X becomes
available, do Y — and never again automatically") are **not** modulated onto a signal via
`effect()`, but attached directly to the triggering source — by a regular RxJS subscription
on the observable that delivers the data.
Concretely: `AuthStore.load()` now returns the (`shareReplay(1)`-shared) `Observable<Me | null>`;
`app.ts` subscribes to it once and calls `userPrefsStore.init(me)` directly in the callback —
no signal read tracking involved, the whole class of bugs ("a read accidentally becomes an
effect dependency") is structurally ruled out at this place, not merely avoided by discipline
(`untracked`).

Rule of thumb for new code: would it make sense to run the action *again* on a further signal
change?
If yes: `effect()`.
If no (it should only run when the data first arrives): a direct subscription on
the source, no `effect()`.

## Consequences

**Easier / better:**

- The class of bugs from the context section is structurally ruled out, not merely defused by an
  `untracked()` convention that would have to be followed anew at every new place.
  The `untracked()` in `UserPrefsStore.init()` additionally stays in place as a second line of
  defence (it also protects callers outside this one bootstrap path).
- The code reads closer to the intent: "once `/api/me` has loaded, do X"
  instead of "observe this signal continuously, but really only for the first value."

**Harder / drawbacks:**

- `AuthStore.load()` is no longer entirely "fire and forget" (a `void` return) — there is now
  a return value that not every caller needs.
  A purely optional API extension; existing callers that ignore the return value still work
  unchanged.
- A second style alongside signal effects in the same area of the code ("sometimes an observable
  subscription, sometimes an effect") — the rule of thumb above is meant to make the decision
  unambiguous at every new place.

## Alternatives Considered

- **Keep only `untracked()` and leave the architecture untouched**: quick, but leaves the
  structural risk (the next similar effect somewhere else) unaddressed — precisely the point
  that led to this ADR.
- **Keep the effect, but add a comment/convention "no untracked reads in this effect"**: purely documentary, no structural safeguard; just as easy to forget
  as the original `untracked()` gap itself.
- **`toSignal()` on the auth loading process plus an `effect()` with a `{ once: true }`-style pattern**:
  Angular has no native `{ once: true }` for effects; it would have to be built by hand (a flag +
  manual teardown) — more code for the same result as a simple RxJS subscription.
