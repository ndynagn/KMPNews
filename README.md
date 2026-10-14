# KMPNews

This project is targeting Android, iOS, Desktop (JVM), built with
the [Kotlin Toolchain](https://kotlin-toolchain.org/latest/).

- [/androidApp](./androidApp) contains the Android application.
- [/desktopApp](./desktopApp) contains the desktop (JVM) application.
- [/iosApp](./iosApp) contains the iOS application.
- [/sharedLogic](./sharedLogic) holds the generated greeting and platform implementations shared by all clients.
- [/sharedUI](./sharedUI) holds Compose presentation for Android and JVM Desktop.
- iOS presentation uses native SwiftUI and consumes `sharedLogic` through `KotlinModules`.

The project name is `KMPNews`. The Kotlin package, Android application ID and iOS bundle ID are
`com.ndynagn.kmp.news`. This is a fresh starter project; application code from the archived project has not been migrated.

The `kotlin` (macOS/Linux) and `kotlin.bat` (Windows) scripts in the project root are self-bootstrapping wrappers for
the Kotlin Toolchain: they download the pinned version 0.13.0 on first use, so no separate installation is required.
Build the whole project with `./kotlin build`.

### Running

Run each module from the command line:

- Android app: `./kotlin run -m androidApp`
- Desktop app: `./kotlin run -m desktopApp`
- iOS app: `./kotlin run -m iosApp`

### Testing

Run the current starter tests with:

```sh
./kotlin test -m sharedLogic -p jvm -p android -p iosSimulatorArm64
```

### Engineering rules and style

Start with [AGENTS.md](AGENTS.md) and the owning module's instructions.
Adjacent CLAUDE files import those rules. Reusable standards cover
[architecture](docs/standards/architecture.md), [Kotlin](docs/standards/kotlin-style.md),
[Swift](docs/standards/swift-style.md), [documentation](docs/standards/code-documentation.md),
[review](docs/standards/review-checklist.md) and [verification](docs/standards/verification.md).
The [technology stack](docs/standards/technology-stack.md) separates the installed
starter from dependencies selected for later migration.

Instructions, docs and style configs are local to this checkout and excluded through
`.git/info/exclude`. They are not distributed by cloning this repository.
This policy does not remove already tracked IDE files from Git.

The local Toolchain plugin resolves ktlint 1.8.0 and runs it in a separate Java
process. Swift checks use the active Xcode formatter and `.swift-format`.

```sh
./kotlin check ktlint
./kotlin check ktlint -m sharedLogic
./kotlin check swiftFormat
```

Checks never format. To format, write one literal repo-relative source path per
line to the ignored `.toolchain/style-files.txt` (no blank lines, globs or comments),
then run `./kotlin do formatKotlin -m style` or `./kotlin do formatSwift -m style`.
Use separate selections for Kotlin and Swift. Missing, empty, duplicate, generated,
symlinked or out-of-checkout selections fail before any formatter starts.
Builds do not apply formatting. Full audits can report
starter style debt, which is separate from a passing check of modified files.

Seven Google Android skills are installed locally for Codex and Android Studio
at revision `5bda39f154fb859a08d45e2392f3568e049f83fb`; these ignored directories
are not distributed through Git. See root instructions for their routing and
project-specific priorities. A fresh agent turn/session may be needed for discovery.

---

Learn more
about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html), [Kotlin Toolchain](https://kotlin-toolchain.org/latest/), [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/), [Kotlin/Wasm](https://kotl.in/wasm/), [Ktor](https://ktor.io/).
