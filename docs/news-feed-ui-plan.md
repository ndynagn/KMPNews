# Mobile news feed presentation

Status: implemented; verification results and remaining limits are recorded below.
Branch: `features/news-feed-ui`. No commit or publication is part of this task.

## Product contract

Android uses Material 3 and iOS uses native SwiftUI. Both start on News, with an
app bar naming the selected tab and bottom navigation for News, Favorites and
Profile. Favorites and Profile display a development placeholder. UI copy is
Russian; provider articles retain the existing English, unfiltered-country contract.
Desktop and macOS retain their starter entry points.

Cards adapt [Effective Compose sample's HeadlineCard](https://gitlab.com/effectivepublic/android/compose-sample/-/blob/c0d4de95fac40416d6564b16c849c4bab9a81403/screens/feed/src/main/kotlin/band/effective/headlines/compose/feed/presentation/components/HeadlineCard.kt):
an optional 150 dp/pt image, 16 dp/pt internal padding, expandable title/description,
and optional source/date. The whole card does not open a URL. Missing title and
summary have localized neutral labels; absent images/source/date are omitted.
Image failures display a neutral placeholder. Dates use the device locale/time zone.
Expansion and scroll position survive tab switching; Compose also saves them for
Activity recreation. Re-selecting a tab does not reset it.

Pull-to-refresh requests page one. Reaching the final three cards requests the next
opaque cursor. Only one operation runs at a time. An append failure disables further
automatic attempts until explicit retry or manual refresh. Tab switching preserves that retry requirement.
Server exhaustion and the local 200-card limit have distinct footer messages.
No detail screen, authentication, working favorites/profile, search or filters are added.

## Architecture and lifecycle

The existing [domain/data contract](news-feed-data-domain-plan.md) remains authoritative.
Public SharedLogic APIs and database schema are unchanged. `MobileApp` is the Android
composition boundary, accepting a repository, freshness use case, configuration
availability and a platform date formatter. The existing Desktop `App()` is preserved.

The feed uses MVI inside ViewModel: refresh, append, observation and retries need
coordination, but their transitions remain readable in event handlers. Both clients
have a State, incoming Event and screen-owned ViewModel, without a reducer or
internal Started/Finished message protocol. The exclusive states are Initial,
Loading, Empty, Error and Content; Content preserves cached cards while request
and recovery status coexist with them. Activation/deactivation are explicit lifecycle inputs;
refresh/load-more/retry are user or viewport actions. No one-shot effects are needed.
Snapshots, operation progress and recoverable failures remain in state. A storage
failure retains any last-rendered cards and requires resubscription. Network errors
retain cached cards and the failed operation for retry. Expected failures are values;
cancellation does not become a user-facing error.

One `FeedDependencies` graph and Room database belong to each mobile application.
Android uses its internal databases directory; iOS uses Application Support.
ViewModels receive domain services through constructors, without service lookup.
Observation reads Room only. Activation runs `RefreshFeedIfNeeded`; manual refresh
calls `refresh()` directly. Domain policy controls freshness, merging and capacity.

Android uses a screen/Activity-owned ViewModel and a structured session inside
`repeatOnLifecycle(STARTED)` while News is selected. Leaving News or stopping the
host cancels its collector and request. iOS uses a native `@MainActor @Observable`
ViewModel, activates only for the selected News tab in an active scene, cancels on
exit and waits for canceled requests before starting another session. Session IDs
reject late results. The Swift adapter consumes SKIE Flow/suspend APIs and maps them
to native presentation values; it does not duplicate storage or business policy.

## Configuration and dependencies

- Android: use the public project URL and publishable key in root `feed.properties`.
- iOS: use the public settings in `iosApp/Configuration/Feed.xcconfig`.
- Provider secrets exist only in Supabase; see [server setup](../supabase/README.md).
  Tracked `Feed.xcconfig` includes it optionally and is assigned only to iOS Debug/Release.
- Builds work without a key. Room observation remains available, network operations
  are disabled, and the UI explains that refresh is unavailable. No sample articles
  enter the production data source.
- Never put a key into tracked source, test fixtures, logs or screenshots.

Pinned presentation libraries: Navigation3 1.1.1, Coil 3.6.3 (Ktor 3 transport, existing
OkHttp engine for Android/JVM), NukeUI 12.8.0. Versions are recorded in the Gradle
catalog and Xcode package reference/lockfile. Compatibility was checked by compiling
Android/JVM and native iOS; the unchanged macOS target was also rebuilt. Compose
libraries are not added to Apple frameworks. NukeUI is linked only to iOS.

Sources: [Navigation3 multiplatform documentation](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html),
[Coil setup](https://coil-kt.github.io/coil/getting_started/),
[Nuke 12.8.0 manifest](https://github.com/kean/Nuke/blob/12.8.0/Package.swift).
The bundled XcodeProj helper could not decode Xcode 27 shell-script arrays and made
no changes. Package/configuration integration was performed through Xcode itself;
existing serialization, signing settings and Kotlin framework scripts were preserved.

## Verification

- `./gradlew :androidApp:compileDebugKotlin :desktopApp:compileKotlin`: passed.
- `./gradlew :sharedUI:jvmTest :androidApp:assembleDebug :desktopApp:compileKotlin`: passed.
  Five focused Kotlin tests cover cache-first activation, freshness, failed append
  retry, overlap rejection, cancellation, resubscription, missing key and capacity.
- Native Swift presentation harness: compile `FeedUiState`, `FeedEvent`,
  `FeedClient`, `FeedViewModel` plus `iosApp/presentationTests/FeedPresentationTests.swift`
  using `swiftc -parse-as-library`, then run the executable. See migration verification below.
- iOS Simulator Debug and native macOS Debug builds with `xcodebuild`: passed.
- Both mobile apps were installed and launched. Missing-key/empty-state screenshots
  were inspected. Live provider requests were not tested because neither local key
  configuration exists. Fixture-driven tests remain independent of credentials.

- Android instrumentation: four passing tests cover pull-to-refresh, tabs, expandable descriptions,
  scroll/expansion state restoration, append failure/retry, and a 320 dp layout at
  200% font scale in dark mode. The first run exposed and fixed an initial-scroll
  anchor bug: the loading footer must not retain a stable key when cards arrive.
  Final screenshots are under `/tmp/kmpnews-ui-evidence/android-final`; the direct
  runner reported `OK (4 tests)` in `/tmp/kmpnews-ui-direct-final.log`.
- iOS XCTest UI suite: two tests passed without skips on iPhone 17 Pro / iOS 27,
  covering tab titles/placeholders, expansion and scroll retention. Screenshots are
  attached to `/tmp/kmpnews-ui-ios-tests.xcresult` and exported under
  `/tmp/kmpnews-ui-evidence/ios-tests`. Light-mode cards, image failure, optional
  fields and maximum accessibility Dynamic Type in dark mode were visually inspected.
- Android Studio Gradle Sync completed successfully after dependency changes.
- Targeted Spotless and swift-format checks passed. Full-repository style auditing
  was not run; existing starter formatting is not claimed clean.
- No production signing/distribution, physical devices, older OS runtime matrix,
  live provider requests, or successful remote-image download was verified.
  iOS refresh/append/error transitions were verified in the deterministic presentation
  harness; gesture-driven network pagination was not exercised in its offline UI suite.
  Android saved-state restoration was exercised with StateRestorationTester; a full
  operating-system process death was not exercised.
  Image error rendering and cancellation ownership are covered, but a live image
  success/cache check remains separate from the offline UI acceptance.

### Reproduce iOS fixture UI tests

The normal app never contains test data. First install and launch it with no local
API key, then seed its **empty simulator cache** externally:

```sh
python3 iosApp/presentationTests/simulator_feed_fixture.py seed \
  --device <simulator-uuid> --backup /tmp/kmpnews-original-cache.db
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,id=<simulator-uuid>' \
  -parallel-testing-enabled NO test CODE_SIGNING_ALLOWED=NO
python3 iosApp/presentationTests/simulator_feed_fixture.py restore \
  --device <simulator-uuid> --backup /tmp/kmpnews-original-cache.db
```

The helper refuses to overwrite a nonempty cache or an existing backup. Restore
refuses to remove non-fixture cards. Without seeding, the cached-card UI test explicitly
skips; the tab test still runs. The original empty cache, light appearance and normal
Dynamic Type were restored after this task's fixture run.

Android UI fixtures live exclusively in `androidTest`; no database seeding is used.
Run `./gradlew :androidApp:connectedDebugAndroidTest` on a connected emulator.
The direct instrumentation runner can retain its screenshots in the test app's
external files directory; Gradle's test cleanup may remove that directory.

The default Compose UI test dependency selected an older Espresso transitively.
The test target explicitly uses the repository's existing Espresso 3.7.0 pin, which
fixes the removed reflective InputManager API on Android 17. This changes only the
test graph; no application toolchain or unrelated dependency was upgraded.

## Presentation standard migration

The canonical selection rules live in `standards/architecture.md`. Activation and
deactivation are explicit lifecycle methods; incoming events are Refresh, LoadMore
and Retry. Refresh/append are serialized; repeated requests are ignored while a
request runs. Session cancellation prevents late mutations and reactivation waits
for prior work to finish. Room observation and writes remain asynchronous in the
existing domain/data implementation. Storage retry resubscribes even without a key.

The ViewModel directly updates its request/read information and publishes one
exclusive UI state. FeedStatus describes current work and recovery alongside content;
it is not a mandatory architecture layer. No effect channel exists until a real
one-time action is implemented. Future share/browser/navigation actions will use
SideEffect; favorites will update persistent data and per-item state. Those features
and their optimistic-update policy are outside this migration.

Migration verification (2026-09-30):

- Baseline `:sharedUI:jvmTest` passed before replacing transitions.
- `./gradlew :sharedUI:jvmTest :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest :desktopApp:compileKotlin`
  passed; all eight Kotlin feed tests passed. They include a dependency that returns
  a late failure after cancellation, proving it cannot change the published state.
- The standalone Swift harness compiled and passed with the new State/Event types;
  coverage includes stale cancellation results, retained cache, retry, serialized
  requests, empty/error modes, server exhaustion and capacity.
- Android direct instrumentation passed all four tests after installing the new APKs:
  tabs, expansion/restoration, refresh, append failure/retry and large-text dark UI.
  Log: `/tmp/kmpnews-migration-android-ui.log`; screenshots:
  `/tmp/kmpnews-migration-evidence/android`.
- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination
  'platform=iOS Simulator,id=2E687D4B-13ED-423D-9B67-9DB7DAF5ED16'
  -parallel-testing-enabled NO -derivedDataPath /tmp/kmpnews-check
  -resultBundlePath /tmp/kmpnews-migration-ios.xcresult test CODE_SIGNING_ALLOWED=NO`
  built the migrated app and passed both iOS UI tests without skips. Expanded-card
  and scroll-restoration screenshots are exported to `/tmp/kmpnews-migration-evidence/ios`.
  The original simulator cache was restored after the test run.
- Targeted Spotless and swift-format passed; relative documentation links and module
  CLAUDE imports were checked. No dependency, database schema or shared public API
  changed. No application-wide style audit or new framework was introduced.

The earlier verification section describes the original implementation. Live API
and remote-image success checks remain unavailable without credentials. Physical
devices and process-death restoration were not tested in this migration.
