import { QueryClient } from "@tanstack/react-query";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      refetchOnReconnect: false,
      refetchOnMount: false,
      staleTime: 1000 * 60 * 5, // 5 minutes fresh cache for instant UI rendering
      gcTime: 1000 * 60 * 30, // Keep in memory for 30 minutes
    }
  }
});
