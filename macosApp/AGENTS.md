# Native macOS client

Read [Swift style](../docs/standards/swift-style.md),
[architecture](../docs/standards/architecture.md) and
[verification](../docs/standards/verification.md). Read
[iOS project rules](../iosApp/AGENTS.md) when editing the shared Xcode project.

- Native SwiftUI/AppKit, Apple Silicon, macOS 14+. Keep the separate `macosApp`
  target/scheme, bundle identifier and local ad-hoc signing configuration.
- New features live in `Features/<FeatureName>`; screens and `@MainActor @Observable`
  ViewModels have separate files. Use controlled mutation and explicit
  ownership; the ViewModel belongs to its window/navigation scope.
- SwiftUI `body` must not initiate data work. Define task triggers, repeatability
  and cancellation. Closing a window disposes window-owned tasks/subscriptions;
  app-scoped services remain available to other windows.
- Use SharedLogic domain/data through SKIE, injecting collaborators through Swift
  initializers. Do not duplicate repositories/databases in Swift. Follow the
  [accepted stack](../docs/standards/technology-stack.md) for Nuke/NukeUI and native
  navigation; define MVVM/MVI contracts using the Swift style standard.
- Prefer native menus, shortcuts, focus behavior, accessibility and localization.
  Validate resizable layouts, minimum supported size and long text. Do not copy
  mobile navigation mechanically into the desktop client.
- This entire directory is Xcode-synchronized. AGENTS.md and CLAUDE.md are excluded
  from membership. Add matching exclusions for future nested instruction files;
  verify they are absent from built app resources.
- Build the affected configuration, launch and inspect the result. Lifecycle
  changes require close/reopen and independent-window checks. Distribution
  signing and Intel support are outside the current local-development contract.

Additional review routing: apply [Apple resource naming](../docs/standards/swift-style.md#apple-resource-naming)
and [Swift declaration organization](../docs/standards/swift-style.md#acronyms-and-declaration-organization);
use [test conventions](../docs/standards/verification.md#test-and-fixture-conventions)
for native fixtures and automation identifiers.
