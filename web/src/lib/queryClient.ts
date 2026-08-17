import { QueryClient } from "@tanstack/react-query";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 10 * 60 * 1000, // 10 minutes cache freshness for instant page loads
      gcTime: 30 * 60 * 1000 // 30 minutes garbage collection time
    }
  }
});
