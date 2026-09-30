# Feed domain/data implementation validation

Date: 2026-09-30. Branch: `features/news-feed-data-domain`.
Contract: [feed behavior](news-feed-data-domain-plan.md).
Status: implemented and locally validated; no commit or push.

## Implemented

- Portable domain models, explicit read/update failures and one-hour refresh policy.
- NewsData client, nullable DTO mapping, opaque pagination and sanitized failure values.
- Room schema v1, transactional cache, 200-card limit, stable merge/deduplication.
- Koin composition with typed Android/JVM/Apple factories and explicit resource ownership.
- Scoped feature rules, synthetic common/platform tests and a separate Swift interop harness.
- Android INTERNET permission and macOS outgoing-client entitlement for future host wiring.
  No incoming connections or additional device/resource privileges are enabled.
- macOS Debug/Release DEAD_CODE_STRIPPING enabled through Xcode. The static SQLite
  dependency graph otherwise retained unused C wrappers with unresolved symbols.
  The standalone Swift harness uses the same flag. iOS already enabled it.
  Xcode also serialized explicit NO values for unrelated sandbox capabilities;
  these preserve their existing disabled state.
  See Apple's [linker build settings](https://developer.apple.com/documentation/xcode/build-settings-reference).

Dependency versions: Koin 4.2.2, Ktor 3.6.0, serialization 1.11.0 (compiler plugin
2.4.20), Room 2.8.5, SQLite bundled 2.6.2, KSP2 2.3.12, SKIE 0.10.15.
Android device-test infrastructure additionally uses AndroidX runner 1.7.0.
No Kotlin, AGP, Gradle or Xcode upgrade was performed.

Toolchain: Kotlin 2.4.20, Gradle 9.7.1, AGP 9.1.1, Zulu JDK 21.0.2;
CLI Xcode 27.0 (27A266a), Apple Swift 6.4 in Swift 5 language mode. Existing
Xcode 27.2 beta was used only to edit the project already open in that process.
The project remains project.pbxproj. No Kotlin diagnostic task was disabled.

## Executed verification

| Check | Result |
| --- | --- |
| Shared JVM tests | 18 feed tests passed, including actual Room driver |
| Shared macOS tests | 18 feed tests passed, including actual Room driver |
| Shared iOS Simulator tests | 18 feed tests passed, including actual Room driver |
| Android host tests | 17 feed tests passed |
| Android device Room test | Passed on Pixel 10 emulator / Android 17 |
| Room rollback and reopen | Passed on JVM, macOS, iOS Simulator and Android device |
| Android assembleDebug / Desktop compileKotlin | Passed |
| iOS device framework link | Passed; device signing is not tested |
| Swift interop on macOS and iOS Simulator | Passed with synthetic network and real Room |
| Android Studio IDE Sync | Initial sync passed in 56s; final sync after Apple settings passed in 2s (13:44 local) |
| Targeted Kotlin and Swift formatting | Passed; second apply idempotent; no full-repository style claim |
| Ordinary framework headers / app resources | Test fixture absent; AGENTS/CLAUDE absent from both app bundles |
| macOS Debug / Release and iOS Simulator Debug apps | Passed |
| Signed macOS network entitlement | Outgoing client enabled; sandbox retained |

Feed test counts exclude starter arithmetic tests. Swift checks compile and run a
real Swift consumer of the framework on each Apple runtime: nullable fields/Long,
Koin factory, successful suspend, quota failure, storage failure as a Flow value,
Room reopen, cancellation reaching the HTTP job, guard release after cancellation,
Flow disposal and resubscription. Screens themselves are unchanged and unconnected.

Commands used:

```sh
./gradlew :sharedLogic:jvmTest :sharedLogic:macosArm64Test \
  :sharedLogic:iosSimulatorArm64Test :sharedLogic:testAndroidHostTest \
  :sharedLogic:connectedAndroidDeviceTest :sharedLogic:linkDebugFrameworkIosArm64 \
  :androidApp:assembleDebug :desktopApp:compileKotlin
sh scripts/check-feed-interop.sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme macosApp -configuration Debug \
  -destination 'platform=macOS,arch=arm64' -derivedDataPath /tmp/kmpnews-feed-apps build
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/kmpnews-feed-apps build CODE_SIGNING_ALLOWED=NO
xcodebuild -project iosApp/iosApp.xcodeproj -scheme macosApp -configuration Release \
  -destination 'platform=macOS,arch=arm64' -derivedDataPath /tmp/kmpnews-feed-apps build
xcrun swift-format lint --strict --configuration .swift-format \
  sharedLogic/interopTests/FeedInteropSmoke.swift
git diff --check
```

Spotless apply/check used the repository's `styleFiles` property with an explicit
list of all changed/new Kotlin source files and sharedLogic/build.gradle.kts.
No application build formats sources. Xcode project serialization was left to Xcode.

## Limits and next stage

- No credentialed NewsData request was made. Provider quotas, actual account
  responses and content-use conditions are not verified by synthetic fixtures.
- No feed UI, native/Compose ViewModel, navigation or app activation hook is added.
  No end-user feed interaction or visual acceptance is claimed.
- No App Store signing, physical Apple device run or distribution validation.
- Existing repository-wide formatting debt is unchanged; only touched sources checked.
- Kotlin expect/actual beta and native framework bundle-ID warnings, plus Gradle
  deprecation warnings remain visible; they are not hidden with global suppressions.

Next: connect the repository and refresh policy to the Compose and native Swift
presentation contracts, including host-owned persistent database locations and
local key configuration. Authorization/favorites remain a separate stage.

## Koin compiler integration

Koin Compiler Plugin 1.2.1 is pinned alongside Koin 4.2.2 / Kotlin 2.4.20.
`compileSafety`, `strictSafety` and `unsafeDslChecks` are enabled in sharedLogic.
Repository/use-case construction uses compiler DSL and an explicit NewsRepository
binding. Resource-backed providers remain explicit lambdas; the graph is assembled
by an isolated `koinApplication`. No business annotations, global container,
Koin KSP processor, public API or cache-policy changes were introduced.

Compile-time rejection was checked against a copy of the actual project at
`/tmp/kmpnews-koin-negative`, excluding Git, build outputs and IDE caches:

1. `./gradlew :sharedLogic:compileKotlinJvm`: success.
2. Remove only the FeedClock registration: failure with `KOIN-D001` for the
   repository and refresh operation.
3. Restore registration and repeat the command: success.
4. Remove only `bind NewsRepository::class`: failure with `KOIN-D001` at the
   refresh constructor and `KOIN-D002` at the typed dependency accessor.
5. Restore the binding and repeat: success.

No clean command or incremental-cache deletion was used between these steps.
Logs: `/tmp/koin-negative-{baseline,clock,restored-clock,binding,restored-binding}.log`.
These prove that the actual isolated graph and interface resolution are checked;
merely applying the plugin or compiling a leaf module would not establish this.

Actual Android Studio Sync completed at 15:11 local time on 2026-09-30:
`BUILD SUCCESSFUL in 7s`. One SDK XML-version warning remains in
`:sharedUI:prepareKotlinIdeaImport` (reader supports version 3; metadata is version 4).
No toolchain update or diagnostic bypass was applied.

The new JVM integration test uses two real Room databases and independent mock
HTTP clients. It checks independent repositories/use cases/cache state, continued
operation of the second owner after closing the first, terminated client jobs and
rejected database reads after closure.

Post-integration verification:

- Shared tests: JVM 19 feed/DI tests, macOS 18, iOS Simulator 18, Android host 17;
  all passed. The Android device Room test passed again. Starter tests excluded.
- Android Debug app, JVM Desktop compilation and iOS device framework: passed.
- `sh scripts/check-feed-interop.sh`: both macOS and iOS Simulator Swift consumers
  passed; ordinary frameworks were restored successfully afterwards.
- macOS Debug and iOS Simulator Debug app builds: passed with the ordinary frameworks.
- Production graph rejection checks and actual IDE Sync: passed as described above.

Gradle reports configuration-time resolution warnings with strict compiler safety;
these and existing expect/actual, framework bundle-ID and Gradle deprecation warnings
remain visible. No blanket suppression or toolchain change was used.

Apple builds used the same commands above with derived data at
`/tmp/kmpnews-koin-apps`. Current run logs are `/tmp/koin-suite.log`,
`/tmp/koin-swift.log`, `/tmp/koin-macos.log`, `/tmp/koin-ios.log` and
`/tmp/koin-style-final.log`. The compiler integration itself changes no Apple
project settings; concurrent local signing-setting edits were preserved.
Targeted Spotless checks cover the module build script, FeedDependencies and its
new JVM test. Documentation links and `git diff --check` were checked as well.

## Audit corrections: resource cleanup and contract clarity

Date: 2026-09-30. Public Kotlin/Swift signatures, provider/cache behavior, UI,
dependencies and toolchains remain unchanged. No commit/push.

- Expanded KDoc for repository observation/mutations, update outcomes, freshness,
  snapshot flags and resource ownership, with relative links to the canonical contract.
- Added internal feed-scoped cleanup that attempts all resources in order, preserves
  the first/original Throwable and suppresses later failures without swallowing cancellation.
- Container ownership is retained before module registration; failures after acquisition
  also attempt container cleanup. Platform factories release the database on client-creation
  failure. An internal callback exercises the assembly failure path without a public test API.
- Added the Room/KSP suppression rationale, moved DAO operations before helpers,
  renamed FakeFeedStore and separated semantic phases in affected code/tests.
- Test temporary-directory cleanup is in an outer finally independent of assertions.

Executed verification:

| Check | Result |
| --- | --- |
| Shared JVM tests | 25 feed/DI tests passed |
| Shared macOS / iOS Simulator tests | 21 feed tests passed on each |
| Android host tests | 20 feed tests passed |
| Android device Room test | Passed |
| Android Debug / Desktop compilation / iOS device framework | Passed |
| Swift interop | macOS and iOS Simulator both passed; normal frameworks restored |
| Apple applications | macOS Debug and iOS Simulator Debug builds passed |
| Koin negative incremental test | Missing FeedClock gives KOIN-D001; restoring it succeeds, without clean |
| Targeted formatting, contract links and diff whitespace | Passed |

Three common cleanup tests cover first/middle failures, attempt order, multiple errors,
original cancellation identity and self-suppression avoidance. Three additional JVM
integration tests cover real owner closure when a Koin onClose callback throws,
assembly cancellation after acquisition (including container disposal), and a client
creation failure after opening Room. The existing independent-owner test still passes.
The helper cannot guarantee that a third-party resource fully releases itself when
its own close implementation throws; it guarantees attempts on subsequent resources.

Commands: the platform task matrix and Swift/Xcode commands above were rerun.
Apple builds used `/tmp/kmpnews-koin-apps`. Logs: `/tmp/feed-fix-suite.log`,
`/tmp/feed-fix-swift.log`, `/tmp/feed-fix-macos.log`, `/tmp/feed-fix-ios.log`.
The isolated copy `/tmp/kmpnews-cleanup-negative` ran `:sharedLogic:compileKotlinJvm`
with the full graph, removed FeedClock, then restored it; logs are
`/tmp/feed-fix-negative-{baseline,missing,restored}.log`.

Targeted Spotless check uses the explicit changed-source list in
`/tmp/feed-fix-style-files.txt`. A start-of-task SHA-256 snapshot confirms previous
unrelated work was preserved. No Gradle configuration changed, so IDE Sync was not
rerun for this correction. Existing toolchain warnings remain; Koin reports that
test compilations without an entry point skip graph validation, while the production
entry point is independently covered by the negative test. No new live API,
distribution-signing or UI acceptance claim is made. This is a scoped correction,
not a whole-repository standards audit.

## ContentNegotiation integration (2026-09-30)

The shared HTTP configuration now installs Ktor ContentNegotiation with the existing
Ktor version and a single internal Json configuration (`ignoreUnknownKeys = true`).
NewsDataClient receives a JsonObject through the plugin and decodes the success DTO
from that tree, without parsing the response string twice. Object/array provider
errors, HTTP fallback, timeouts and cancellation retain their existing semantics.
Conversion failures become INVALID_RESPONSE unless the HTTP status supplies a more
specific failure. MockEngine clients, including DI and Swift fixtures, use the same
configuration and explicit JSON response content types.

Executed commands:

```sh
./gradlew spotlessApply spotlessCheck -PstyleFiles="$(cat /tmp/content-style-files.txt)" \
  :sharedLogic:jvmTest :sharedLogic:testAndroidHostTest \
  :sharedLogic:macosArm64Test :sharedLogic:iosSimulatorArm64Test \
  :sharedLogic:linkDebugFrameworkIosArm64
sh scripts/check-feed-interop.sh
git diff --check
```

| Check | Result |
| --- | --- |
| JVM / Android host | 29 / 24 total tests passed |
| macOS / iOS Simulator | 25 total tests passed on each |
| iOS device framework | Built successfully |
| Swift interop on macOS and iOS Simulator | Passed; normal frameworks restored |
| Targeted Spotless and diff whitespace | Passed |

Network scenarios cover the Accept header, actual plugin conversion, empty success,
nullable and unknown fields, invalid JSON shapes, object/array errors, errors in a
200 response, non-JSON HTTP errors, unsupported content types, timeouts and cancellation.
The first run exposed an unconfigured DI test client; that fixture was corrected and
the complete requested task matrix then passed. Logs are available locally at
`/tmp/content-negotiation-recheck.log` and `/tmp/content-negotiation-swift.log`.

This scoped integration did not rerun Android device tests, full application builds,
IDE Sync or live API calls. Existing Gradle/Kotlin/SKIE and test IOException deprecation
warnings remain. No dependency version upgrade, public API change, UI change,
commit or push is included.

## DefaultRequest and opt-in HTTP logging (2026-09-30)

DefaultRequest supplies `https://newsdata.io/api/`; NewsDataClient uses `1/latest`.
The proposed base without `/api/` would have changed the existing endpoint, so the
implementation preserves `/api/1/latest` and tests its exact scheme/host/path.
Query parameters and the API key remain scoped to the feed request.

The existing factories remain quiet. New overloads accept FeedHttpLogger, retaining
its sink without taking ownership. Logging uses HEADERS, masks X-ACCESS-KEY,
Authorization, Proxy-Authorization, Cookie and Set-Cookie, and filters to the exact
HTTPS newsdata.io host. No body logging, retry policy, HTTP cache or toolchain upgrade
was introduced. README contains Kotlin/Swift debug composition examples.

Executed verification:

| Check | Result |
| --- | --- |
| JVM / Android host tests | 32 / 27 total tests passed |
| macOS / iOS Simulator tests | 28 total tests passed on each |
| Android Debug / Desktop compilation / iOS device framework | Passed |
| Swift interop on macOS and iOS Simulator | Passed, including new factory and Kotlin-to-Swift logger callback; normal frameworks restored |
| macOS Debug / iOS Simulator Debug applications | Both built successfully |
| Targeted Spotless, Swift-format lint and git diff --check | Passed |

MockEngine scenarios verify exact endpoint/query/Accept behavior, enabled logging,
credential masking in request/response headers, omitted response bodies, success and
HTTP failure statuses, excluded hosts/plain HTTP, absence of the plugin by default,
and preserved cancellation. The Swift sink uses NSLock to protect callback evidence.

Commands:

```sh
./gradlew spotlessApply -PstyleFiles="$(cat /tmp/logging-style-files.txt)" \
  :sharedLogic:jvmTest :sharedLogic:testAndroidHostTest \
  :sharedLogic:macosArm64Test :sharedLogic:iosSimulatorArm64Test \
  :sharedLogic:linkDebugFrameworkIosArm64 :androidApp:assembleDebug :desktopApp:compileKotlin
./gradlew spotlessApply spotlessCheck -PstyleFiles="$(cat /tmp/logging-style-files.txt)"
xcrun swift-format lint --strict --configuration .swift-format sharedLogic/interopTests/FeedInteropSmoke.swift
sh scripts/check-feed-interop.sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme macosApp -configuration Debug \
  -destination 'platform=macOS,arch=arm64' -derivedDataPath /tmp/kmpnews-koin-apps build
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/kmpnews-koin-apps build CODE_SIGNING_ALLOWED=NO
git diff --check
```

Local logs: `/tmp/feed-logging-tests.log`, `/tmp/feed-logging-style.log`,
`/tmp/feed-logging-swift.log`, `/tmp/feed-logging-macos.log`, `/tmp/feed-logging-ios.log`.
Existing Gradle/Kotlin/SKIE and test IOException warnings remain. IDE Sync, Android
device tests, live API, UI interaction and distribution signing were not rerun for
this integration. No commit or push was performed.

## Pre-publication spacing pass (2026-09-30)

Reviewed the 66 pending files and ran targeted Spotless over all 40 changed/new
Kotlin and Gradle Kotlin DSL files, plus Swift-format on the interop harness.
Seven source/test files received semantic blank-line corrections. Comparing their
nonblank lines against the pre-pass snapshot confirms no code or literal changes.
No repeated blank lines remain in the changed Kotlin/Swift sources; git diff --check
passes. Xcode-generated configuration and the exported Room schema were preserved.
Behavioral suites were not repeated for this whitespace-only pass; the preceding
integration results remain the behavior evidence. Current macOS signing settings
are included in delivery by explicit user instruction.
