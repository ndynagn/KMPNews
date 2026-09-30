# iOS client and shared Apple project

Read [Swift style](../docs/standards/swift-style.md),
[architecture](../docs/standards/architecture.md) and
[verification](../docs/standards/verification.md).

- Put new client features under `iosApp/Features/<FeatureName>` (inside the Swift
  source folder). Each screen has a separate native Swift ViewModel.
- Use explicit `@MainActor @Observable` ViewModels and controlled mutation.
  The existing direct Greeting call in ContentView is a starter exception only.
- Views render state and send actions. Use an owned task/lifecycle trigger with
  deliberate repeatability and cancellation; never start data work in `body`.
  Async bridges must preserve cancellation and documented error semantics.
- Consume shared domain/data through SharedLogic and the selected SKIE bridge;
  inject dependencies through Swift initializers. Do not duplicate Kotlin
  repositories/storage or expose Koin lookup inside Swift ViewModels.
- Use NavigationStack/NavigationSplitView as appropriate and Nuke/NukeUI for
  images, following the [accepted stack](../docs/standards/technology-stack.md).
  Screen MVVM/MVI contracts use native Swift types as defined in Swift style.
- Use native navigation, accessibility, localization and client-owned UI-kit
  components. Do not expose transport DTOs to Views. Previews use deterministic
  data; they must not perform live network calls.
- This directory owns `iosApp.xcodeproj`, which contains BOTH app targets.
  Read macOS instructions too when changing its target settings. Preserve the
  static SharedLogic framework and `embedAndSignAppleFrameworkForXcode` script,
  including the IDE override guard and execution before Swift compilation.
- `project.pbxproj` is the active Xcode-authored format. The JSON migration was
  reversed because Kotlin Gradle Plugin 2.4.20 requires pbxproj during IDE Sync.
  Preserve Xcode serialization; do not run generic formatters or disable Kotlin's
  Xcode diagnostic tasks. Reconsider JSON only after verifying IDE compatibility.
  Keep platform SDK, deployment and signing settings scoped to the correct target.
- Synchronized folders provide source membership. Exclude every added instruction
  file from target membership, including nested feature AGENTS/CLAUDE files;
  inspect the final bundle. Do not manually add individual Swift source entries.
- The module instructions here are outside the synchronized iOS source folder.
  Future instructions inside `iosApp/` need explicit membership exclusions.
- UI changes need an iOS build, launch and relevant interaction/visual check.
  Project changes need before/after settings comparison and affected-client builds.
  Simulator success does not establish device signing or distribution readiness.

Additional review routing: apply [Apple resource naming](../docs/standards/swift-style.md#apple-resource-naming)
and [Swift declaration organization](../docs/standards/swift-style.md#acronyms-and-declaration-organization);
use [test conventions](../docs/standards/verification.md#test-and-fixture-conventions)
for native fixtures and automation identifiers.

Select the presentation variant using architecture; coordinated requests alone
do not require a reducer. Record events, states, concurrency and acceptance
scenarios in the feature contract. Native and Compose contracts share behavior,
not presentation types.
