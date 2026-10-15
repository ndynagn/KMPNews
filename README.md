# KMPNews

This project is targeting Android, iOS, Desktop (JVM), built with
the [Kotlin Toolchain](https://kotlin-toolchain.org/latest/).

- [/androidApp](./androidApp) contains the Android application.
- [/desktopApp](./desktopApp) contains the desktop (JVM) application.
- [/iosApp](./iosApp) contains the iOS application.
- [/sharedLogic](./sharedLogic) owns Auth/Profile, Feed, Search and Favorites domain/data and platform adapters.
- [/sharedUI](./sharedUI) holds Compose presentation for Android and JVM Desktop.
- iOS presentation uses native SwiftUI and consumes `sharedLogic` through `KotlinModules`.

The project name is `KMPNews`. The Kotlin package, Android application ID and iOS bundle ID are
`com.ndynagn.kmp.news`. Android retains its archived Auth/Profile/Feed screens and Favorites placeholder;
iOS uses native Auth/Profile, Feed, Search, Favorites and article reading. JVM Desktop retains its starter window.
Native SwiftUI macOS is outside the active project.

The `kotlin` (macOS/Linux) and `kotlin.bat` (Windows) scripts in the project root are self-bootstrapping wrappers for
the Kotlin Toolchain: they download the pinned version 0.13.0 on first use, so no separate installation is required.
The small project preparation hook requires Python 3.9+ and prepares local
SKIE/Room paths before delegating to that official bootstrap. It also forces
fresh sharedLogic compilation for Koin checks. Build Apple platforms and
Debug/Release profiles in separate invocations.
Build Android with `./kotlin build -m androidApp -p android`, Desktop with
`./kotlin build -m desktopApp -p jvm`, and iOS with
`./kotlin build -m iosApp -p iosSimulatorArm64`. A whole-project Apple build
without an explicit platform is rejected to keep SKIE target caches separate.

### Running

Run each module from the command line:

- Android app: `./kotlin run -m androidApp`
- Desktop app: `./kotlin run -m desktopApp`
- iOS app: `./kotlin run -m iosApp -p iosSimulatorArm64`

### Desktop executable JAR

```sh
./kotlin package -m desktopApp -p jvm
java -jar 'build/tasks/_desktopApp_package@desktop-packaging/KMPNews-desktop.jar'
```

Use Java 25 or newer for the current compiled application; the JRE is not bundled.
The executable includes runtime dependencies and the current host's Skiko native library,
so package separately on each supported operating system/architecture.

The project hook first runs official packaging, then the local `desktop-packaging`
Toolchain plugin. The plugin consumes the resolved runtime classpath, preserves dependency
JAR bytes and gives every nested JAR a unique indexed name. This avoids Toolchain 0.13
collisions between different artifacts with the same filename, without changing versions.
Its output above is the distributable; the earlier `_desktopApp_executableJarJvm` JAR is an
intermediate. `./kotlin do packageDesktop -m desktopApp` can repeat the final step after
official packaging. Bootstrap scripts and the Toolchain installation are unchanged.

### Testing

Run feature tests in separate platform invocations:

```sh
./kotlin test -m sharedLogic -p jvm
./kotlin test -m sharedLogic -p android
./kotlin test -m sharedLogic -p iosSimulatorArm64
./kotlin test -m sharedUI -p jvm
./kotlin test -m androidApp -p android
```

### Engineering rules and style

Start with [AGENTS.md](AGENTS.md) and the owning module's instructions.
Adjacent CLAUDE files import those rules. Reusable standards cover
[architecture](docs/standards/architecture.md), [Kotlin](docs/standards/kotlin-style.md),
[Swift](docs/standards/swift-style.md), [documentation](docs/standards/code-documentation.md),
[review](docs/standards/review-checklist.md) and [verification](docs/standards/verification.md).
The [technology stack](docs/standards/technology-stack.md) records configured integrations and their verification gates.

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

### Feature migration checks

Koin, Ktor, Room/KSP/SQLite and SKIE are configured with the archived versions.
Starter tests and neutral admission probes have been removed for feature migration.
The feature-owned harnesses stage disposable copies outside the repository and install separate verification app IDs.
Select an already booted simulator/emulator; the scripts never create or erase devices:

```sh
python3 scripts/verify-ios-features.py --suite all --ios-simulator <UDID>
python3 scripts/verify-ios-features.py --suite interop --ios-simulator <UDID>
python3 scripts/verify-ios-features.py --suite keychain --ios-simulator <UDID>
python3 scripts/verify-ios-features.py --suite ui --ios-simulator <UDID> --only-testing ArticleDetailUITests
python3 scripts/verify-android-features.py --adb <SDK>/platform-tools/adb --emulator emulator-5554
python3 scripts/verify-android-ui.py --adb <SDK>/platform-tools/adb --emulator emulator-5554
python3 scripts/verify-koin-graph.py
```

Native suites can also be selected individually with `--suite`; use `--help` for current names.
UI runners create temporary test targets/APKs; they never add a legacy instrumentation module.
The local mediator fixture exercises actual OkHttp/Darwin engines without contacting a hosted service.
Temporary copies remove public server configuration and use separate app IDs and Keychain services.
Client configuration defaults to an unconfigured state. Supply only the public project URL/key
through Android's `res/values/client_configuration.xml` and iOS Info.plist `SupabaseURL` /
`SupabasePublishableKey` when real-server use is authorized. No privileged credentials belong in a client.
Backend migrations and server configuration remain archived. Live Search additionally requires mediator `q` support.
Use local HTTP fixtures; do not treat a local check as proof of production backend
compatibility. Ordinary applications and frameworks must exclude test-only APIs.
See the verification standard for platform and interop requirements.

---

Learn more
about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html), [Kotlin Toolchain](https://kotlin-toolchain.org/latest/), [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/), [Kotlin/Wasm](https://kotl.in/wasm/), [Ktor](https://ktor.io/).
