# Feed domain and data

Scope: this feature. Follow the repository and sharedLogic instructions.
The authoritative behavior is [the feed contract](../../../../../../../../../../docs/contracts/news-feed.md).

- Keep English, unfiltered-country feed requests through the Supabase mediator
  and opaque cursor pagination. Provider parameters and secrets are server-owned.
- Room is the read source; observation must never trigger a network request.
- Merge by article ID, preserve existing cards on refresh, cap storage at 200.
- Freshness is one hour since a committed first-page fetch, not a retention TTL.
- Distinguish cache capacity, server exhaustion, successful empty results and failures.
- Preserve coroutine cancellation and atomic writes. Export failures as values.
- Test with synthetic data and controlled time/network; never include a real API key.
- There is no presentation layer in this feature scope; MVVM/MVI is decided by the later client feature.
