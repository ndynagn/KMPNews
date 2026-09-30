# News feed domain/data contract

Accepted: 2026-09-30. Implementation branch: `features/news-feed-data-domain`,
based on published dev `ada940923d23e15091647b43b08c1c15b4d2d5e8`.
Follow the [accepted stack](standards/technology-stack.md) and
[architecture](standards/architecture.md). Execution evidence and remaining limits:
[feed validation](news-feed-validation.md).

## Scope and ownership

SharedLogic owns feature/feed/domain, data and composition. Room is the sole
observed read source. Android/Desktop presentation remains sharedUI; Apple
presentation remains Swift. This stage introduces no feed UI or ViewModel;
MVVM/MVI is selected when the presentation contract is implemented.
Authentication, favorites, background synchronization, server proxy and image
caching are excluded. Existing Greeting behavior is unchanged.

The feed is English and general, with no country/category/search filter. The
prototype directly accesses NewsData.io Free. The API key is supplied by the host
composition root, never tracked, persisted in Room or logged. Direct client
configuration cannot hide a key in a distributed binary.

## Provider contract and evidence

Inspected primary sources: [OpenAPI](https://newsdata.io/openapi.json),
[documentation](https://newsdata.io/documentation), [pricing](https://newsdata.io/pricing)
and [terms](https://newsdata.io/terms). Findings are not a credentialed live API test.

- GET `https://newsdata.io/api/1/latest`, `language=en`, `size=10`, `timezone=UTC`.
- Use the supported `X-ACCESS-KEY` header, not a credential in the URL. Redirects
  are disabled so the credential is not forwarded to another endpoint.
- Free: up to ten articles per call, twelve-hour delayed news, latest endpoint's
  48-hour window, published allowances of 200 credits/day and 30 credits/15 minutes.
  Account quotas are shared across devices; no local counter pretends to enforce them.
- Pass `nextPage` unchanged as `page`. Missing/null means server exhaustion.
  No numeric page derivation, inferred cursor lifetime or automatic pagination loop.
- Successful envelope requires status, totalResults and a results array.
  Only article_id is required in the article schema; other selected fields may
  be missing/null. Unknown fields are ignored. DTOs never escape data.
- `Article.summary` maps provider `description`; the name avoids NSObject's
  description collision in Swift. Other fields: id, title, url, imageUrl,
  sourceId, sourceName and publishedAtEpochMilliseconds.
- Parse non-null `pubDate` in the requested UTC timezone. Missing dates remain
  null; malformed dates reject the page. Never invent content, dates or URLs.
- Exclude full content, paid AI fields and source-page scraping.
- Error results can be an object or a list. Normalize machine-code whitespace
  (the schema includes `ServiceUnavailable `); never expose raw messages.
  Distinguish quota exhaustion from rate limiting. Preserve unknown-code handling.
- Explicit failures: network, timeout, access denied, invalid request, rate limit,
  quota, server unavailable, invalid response and storage. No automatic retries.
  Request/socket timeout is 30 seconds; connection timeout is 15 seconds.

No numerical cache-retention permission was verified in the inspected terms.
The 200-card cache is a product choice, not a content license. Third-party content
rights and direct-client key conditions must be reviewed before distribution.
No provider contact or assumption of unrestricted redistribution is authorized.
Synthetic fixtures independently verify persistence and all behavior below.

## Refresh, concurrency and cancellation

- Observing the feed never requests the network. Clients immediately receive
  persisted data, including stale data. No timer or background polling exists.
- Activation/foreground calls `RefreshFeedIfNeeded`. Refresh when uninitialized
  or at least one hour after a committed first-page fetch. A backwards clock jump
  also makes the cache eligible. The clock is injected for testing.
- Manual refresh always requests page one, bypassing freshness. A successful
  empty response initializes freshness. Append and failure never reset freshness.
- One repository instance owns mutations for one database. Extra simultaneous
  refresh/append calls return AlreadyRunning instead of queuing. Share one
  app-owned graph across its consumers; do not create competing graphs for one file.
- Network I/O occurs outside Room transactions and belongs to the caller's coroutine.
  Cancellation propagates and releases the mutation guard. It is not a failure value.
- A committed database update can survive cancellation occurring after commit;
  observation remains the source of truth, not delivery of an operation return value.

The local-first approach follows [Android's offline-first guide](https://developer.android.com/topic/architecture/data-layer/offline-first)
and [Now in Android](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md).
The hour threshold and capacity are our decisions, not intervals prescribed by those sources.

## Cache and pagination

- Persist at most 200 unique cards. No extra requests to fill the cache and no
  deletion purely due to age. The hour is a freshness threshold, not retention TTL.
- Merge by exact article_id. Incoming fields replace previous metadata, including
  nulls. Repeated IDs within one response use the last occurrence's metadata.
- Order by publication timestamp descending, null dates last. Preserve prior
  relative order for ties; new ties follow existing ones in response order.
- Trim the oldest tail to 200 after merging. Refresh retains older cards and
  replaces the server cursor with the newly fetched first page's cursor.
- Append uses the persisted cursor. Before initialization it loads the first page.
  An absent cursor on initialized data returns EndReached without HTTP. Otherwise,
  capacity returns CacheLimitReached without HTTP; it is not server exhaustion.
- Empty successful refresh retains old cards, updates freshness and the cursor.
  Duplicate-only or empty append can advance the cursor without adding cards.
- Articles, order, freshness and cursor commit in one transaction. Failed network,
  decoding or storage work preserves the previous snapshot and cursor.
- Schema version 1 is exported. No destructive migration fallback is configured.

## Public API and resource ownership

- `Article`: immutable domain metadata; no persistence/serialization annotations.
- `FeedSnapshot`: ordered cards, nullable lastRefreshedAtEpochMilliseconds,
  hasMore (server cursor exists), isCacheLimitReached (local capacity).
- `FeedReadResult`: Snapshot or StorageFailure. A storage failure completes the
  collection after emitting its value; callers may explicitly resubscribe.
- `NewsRepository`: observeFeed, refresh, loadNextPage. Public APIs expose neither
  Room/HTTP types nor PagingData nor screen state.
- `FeedUpdateResult`: Updated, Fresh, EndReached, CacheLimitReached,
  AlreadyRunning or Failed(FeedFailure). Updated follows transaction commit.
- `RefreshFeedIfNeeded`: the only use case, because freshness is business policy.
- `createFeedDependencies`: typed platform factory, receiving an absolute writable
  database path and runtime key; Android also receives application Context.
  The host selects an app-private persistent location, not a temporary directory.
- Koin is isolated inside composition; constructors receive dependencies explicitly.
  Swift uses the typed factory, not service lookup. Cancel all consumer tasks before
  closing the app-owned FeedDependencies once. Closing one owner must not close another.
- Closing attempts Koin, HTTP client and database in order even after a cleanup failure.
  The first failure is rethrown with later failures suppressed. Initialization failure
  (including cancellation) stays primary while all acquired resources get cleanup attempts.
  Closure is synchronous, does not await task completion and has no new Swift error bridge;
  repeated/concurrent closure is not supported.

## Verification contract

Deterministic common tests cover mapping, nulls, malformed data, object/list error
envelopes, fixed request parameters, cursor preservation, merge order, deduplication,
capacity, empty success, freshness boundaries, errors, concurrency and cancellation.

Platform Room tests create/write/observe, force an insertion failure after deletion
to prove transaction rollback, close and reopen the database. Run on JVM, macOS,
iOS Simulator and Android emulator. Swift fixtures exercise real Room, the repository,
Ktor MockEngine and SKIE on macOS/iOS, including failure values and actual cancellation.

The fixture source set is opt-in with `-PfeedInteropTests=true`; it is absent from
normal app frameworks. Run `sh scripts/check-feed-interop.sh` with a booted Simulator
(or set SIMULATOR_UDID). The script restores normal frameworks after successful checks.
If interrupted, rebuild frameworks without the property before using them in apps.
The standalone Swift linker uses dead stripping, matching Xcode's normal behavior.

Compile both Compose clients, both Swift apps and the iOS device framework. Perform
real IDE Sync, targeted formatting checks and verify normal headers omit fixtures.
Live API smoke is additional and requires a locally configured key; fixtures never
consume provider credits. Record unavailable checks rather than claiming success.
