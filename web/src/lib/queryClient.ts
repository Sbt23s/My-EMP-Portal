import { QueryClient } from "@tanstack/react-query";

/**
 * How every screen in the portal decides whether what it is showing is still
 * true.
 *
 * <p>The settings here were tuned for the first paint and against everything
 * after it: refetchOnMount off, refetchOnWindowFocus off, and a five minute
 * staleTime. A page opened twice in five minutes was answered from cache
 * without asking the server, which is why a new claim did not appear in its
 * own table, a group somebody had just been added to stayed missing from Chat,
 * and an approval decided on one screen was still pending on another. Each was
 * reported as a separate bug; all of them were this.
 *
 * <p>It is also why invalidateQueries so often looked like it did nothing:
 * invalidation only refetches queries that are currently mounted, so marking a
 * list stale from the page that changed it did nothing at all, and arriving
 * back at that list, refetchOnMount was off and the stale cache answered
 * anyway.
 *
 * <p>The cache still does its job on the paint that matters. staleTime is
 * short rather than zero, so the twenty tiles on a dashboard mounting together
 * still share one answer, and cached data is shown immediately while the
 * refetch happens behind it -- the screen does not blank. What changes is that
 * opening a page, returning to the tab, or coming back from a dropped
 * connection now asks whether anything moved.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,

      // Coming back to the tab is exactly when somebody wants to know what
      // happened while they were elsewhere.
      refetchOnWindowFocus: true,

      // A reconnect means the gap is unknown, so nothing on screen can be
      // trusted to be current.
      refetchOnReconnect: true,

      // Opening a page asks the server. "always" rather than true because
      // true still respects staleTime, which is the setting that made a
      // freshly-invalidated list answer from cache.
      refetchOnMount: "always",

      // Long enough that a screen mounting twenty queries at once does not
      // fire twenty requests, short enough that nothing here is what makes a
      // page stale.
      staleTime: 10 * 1000,

      gcTime: 1000 * 60 * 30,
    }
  }
});
