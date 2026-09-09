import { routes } from './app.routes';

/**
 * The route table is the only place that says a page is reachable at all.
 *
 * <p>A feature can be complete — component, API client, translations, a nav link — and still be
 * unreachable because its route was never registered, and nothing else in the suite notices: every
 * component spec instantiates its component directly. So the admin routes are listed here by path,
 * together with the guard, because an admin page that loses `canActivate` is worse than one that
 * is missing.
 */
describe('app routes', () => {
  const byPath = (path: string) => routes.find((r) => r.path === path);

  it.each(['admin/metrics', 'admin/users'])('registers %s behind a guard', (path) => {
    const route = byPath(path);

    expect([route !== undefined, route?.canActivate?.length ?? 0]).toEqual([true, 1]);
  });

  it('registers the pages a signed-in user reaches from the navbar', () => {
    expect(routes.map((r) => r.path)).toEqual(
      expect.arrayContaining(['', 'watchlist', 'settings', 'status', 'manage']),
    );
  });
});
