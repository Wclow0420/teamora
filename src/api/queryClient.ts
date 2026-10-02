import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
      // "Window focus" = app foreground, wired up in QueryFreshness.
      refetchOnWindowFocus: true,
    },
  },
});
