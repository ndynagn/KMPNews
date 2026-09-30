# Supabase feed proxy verification

Date: 2026-09-30. Scope: the proxy integration on
`features/news-feed-ui`. No push, PR, toolchain upgrade or automated
deployment was performed. The implementation contract is in
[supabase/README.md](../supabase/README.md). Earlier direct-provider verification
records describe the previous implementation, not the current transport.

## Deployment and access

- Existing project `awupjlsmdnbhpfbmykrv`, London `eu-west-2`.
- `news-feed` deployed, version 1, ACTIVE, `verify_jwt=false`.
- Applied migrations: `20260930142143_news_request_budget` and
  `20260930142522_restrict_rls_event_trigger`. The latter's local existence guard
  also permits fresh replay without the Dashboard-created helper.
- The user supplied `NEWSDATA_API_KEY` through Dashboard Secrets. Its value was
  not read, printed, stored in Git or passed to a client.
- Data API enabled for the service-only RPC; automatic table exposure disabled.
  GitHub integration exists; production deployment and automatic branching remain off.
- Publishable-key REST probes: private schema rejected with 406/PGRST106,
  public table lookup 404/PGRST205, budget RPC 401/42501.
- Security Advisors: no WARN/ERROR. One intentional INFO
  [RLS enabled without policies](https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy)
  on `private.news_request_attempts`; clients have no grants or policies and only
  the service role can reserve attempts.

## Executed checks

| Check | Result |
| --- | --- |
| Deno check, lint, fmt and handler tests | PASS; 7 tests, including malformed input, opaque cursor, secrets, budget failure, provider errors and timeout |
| `python3 supabase/tests/check_budget.py` | PASS; PostgreSQL 17.11, rolling-window boundaries, role denial, 40 concurrent calls accepting exactly 25; disposable container removed |
| Hosted SQL budget assertions inside rollback transaction, before live traffic | PASS; no test reservations retained |
| Live first and next page from host | PASS; HTTP 200, 10 articles each |
| Missing public key / unknown query parameter | PASS; 401 / 400 without upstream calls |
| `:sharedLogic:jvmTest` | PASS; 34 tests |
| `:sharedLogic:testAndroidHostTest` | PASS; 29 tests |
| `:sharedLogic:macosArm64Test` | PASS; 30 tests |
| `:sharedLogic:iosSimulatorArm64Test` | PASS; 30 tests |
| `sh scripts/check-feed-interop.sh` | PASS; macOS/iOS Swift models, Koin, Room reopen, suspend success/failure/cancellation, Flow cancellation/resubscription; normal frameworks restored |
| Android debug APK / Desktop compilation | PASS |
| iOS device framework / iOS Simulator app / macOS app builds | PASS; no device signing or distribution claim |
| Android controlled feed UI scenarios | PASS; 4 tests, including retained cards on append failure |
| Android live refresh and append | Initial NETWORK failure; PASS after AVD DNS correction, 1 test, real article visibly rendered |
| iOS live refresh and scroll | BLOCKED by DNS error -1003, including after Simulator restart |
| Targeted Kotlin/Swift formatting and `git diff --check` | Checked separately from builds; no whole-repository style acceptance implied |

The combined Gradle run in `/private/tmp/kmpnews-final-tests.log` failed because
the original live Android test failed, even though all 123 shared tests passed.
The subsequent explicit Android run in `/private/tmp/kmpnews-android-live-retry.log`
passed. These results must not be collapsed into a passing original run.

Android APK dex/XML/native entries and iOS executable/dylib/plist entries were
checked for `NEWS_API_KEY`, `NEWSDATA_API_KEY`, `NewsDataAPIKey` and `newsdata.io`;
no legacy configuration/provider endpoint was found. The unknown provider secret
value was not retrieved for scanning, so this is a configuration/endpoint scan,
not a byte-for-byte comparison with that secret.

## Runtime DNS incident

Both clients initially failed before reaching the function. Android reported
unknown host; iOS reported `NSURLErrorDomain -1003`, `Resolved 0 endpoints`,
`DNS Error: NoSuchRecord` on VPN interface `utun6`. The URL in the diagnostic
matches the configured project. Host HTTPS requests returned HTTP 200, and the
server budget was below both limits. These failures are not budget rejections.

Android AVD `Pixel_10` was restarted without wiping data, with explicit reachable
DNS servers using the supported emulator option:

```sh
"$ANDROID_HOME/emulator/emulator" -avd Pixel_10 -netdelay none -netspeed full \
  -dns-server 1.1.1.1,8.8.8.8 -no-snapshot-load
```

DNS resolution and the live refresh/append test then passed. This is a local
emulator launch setting, not an application networking workaround or a persistent
change to system/VPN configuration. A subsequent normal emulator launch may use
the broken inherited resolver until the host VPN/DNS is corrected.

iOS Simulator restart retained the DNS failure. The remaining unblock condition
is working native DNS through the host network/VPN, followed by successful live
refresh and scroll. Do not replace the hostname with an IP, disable TLS checks,
or embed the provider credential to bypass this environment issue.

The user subsequently confirmed that the network troubleshooting helped and the
feed worked. This manual confirmation does not replace a rerun of the failed iOS
automated live test; its recorded result remains unchanged.

## Evidence and repeat commands

Session logs are under `/private/tmp/kmpnews-*.log`; they are temporary and are not
versioned. Reviewed screenshots are retained locally under
`/Users/ndynagn/.codex/visualizations/2026/09/30/01a0f236-7742-7502-acec-e510e5504ba5/feed-proxy/`:
`android-live-mediator.png`, `kmpnews-android-expanded.png`, and
`kmpnews-android-append-error.png`. The live image proves rendered article metadata;
it does not establish successful loading of every third-party image.

Dedicated live tests require explicit opt-in because they consume the shared
NewsData budget. They supplement deterministic tests:

```sh
./gradlew :androidApp:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ndynagn.kmp.news.feature.feed.LiveFeedSmokeTest \
  -Pandroid.testInstrumentationRunnerArguments.liveFeedSmoke=true

TEST_RUNNER_LIVE_FEED_SMOKE=1 xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,id=B604A2A5-9F83-48D5-BE7E-F9AC74AF5CDF' \
  -derivedDataPath /private/tmp/kmpnews-proxy-xcode -parallel-testing-enabled NO \
  -only-testing:KMPNewsUITests/KMPNewsUITests/testLiveMediatorRefreshAndScroll \
  test CODE_SIGNING_ALLOWED=NO
```

The failed iOS test completed its assertions but its diagnostic collector stalled;
the owned xcodebuild process was interrupted and its xcresult attachments exported.
That run remains failed, not successful or skipped.

## Remaining release prerequisites

- Complete the iOS live test after restoring native DNS. Physical-device behavior
  and distribution signing have not been verified.
- Confirm the dedicated NewsData account quota and content redistribution terms
  before public distribution.
- Establish whether an old provider key shipped in any earlier binary. If so,
  revoke/rotate it after verifying the new server credential. No revocation is claimed.
- There is no server article cache or fairness guarantee. The budget bounds
  provider attempts, not Supabase invocations, and is shared by all visitors.
