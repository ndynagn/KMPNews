#!/usr/bin/env python3
"""Run feature-owned Swift checks in a disposable Toolchain Simulator application."""

import argparse
import contextlib
import importlib.util
import json
import os
import plistlib
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SUITES = {
    "auth": "AuthPresentationTests.swift",
    "profile": "ProfileEditingTests.swift",
    "profile-photo": "ProfilePhotoPreparationTests.swift",
    "catalog": "ComponentCatalogTests.swift",
    "feed": "FeedPresentationTests.swift",
    "article-grid": "ArticleGridLayoutTests.swift",
    "search": "SearchPresentationTests.swift",
    "favorite-save": "FavoriteSavePresentationTests.swift",
    "favorites-list": "FavoritesListPresentationTests.swift",
    "article-detail": "ArticleDetailPresentationTests.swift",
    "error-feedback": "ErrorFeedbackPresentationTests.swift",
    "profile-cache": "ProfileAvatarCacheTests.swift",
    "keychain": "KeychainProcessTests.swift",
}
MODULES = ("androidApp", "desktopApp", "iosApp", "sharedLogic", "sharedUI", "tooling/style",
           "tooling/desktop-packaging")


def validate_input(path):
    """Reject out-of-checkout inputs and symlinks before staging handwritten verification files."""
    relative = path.relative_to(ROOT)
    if ".." in relative.parts:
        raise ValueError("Verification inputs must stay inside the checkout: " + str(path))
    current = ROOT
    for part in relative.parts:
        current /= part
        if current.is_symlink():
            raise ValueError("Verification inputs must not be symlinks: " + str(current))
    if path.is_dir():
        for child in path.rglob("*"):
            if child.is_symlink():
                raise ValueError("Verification inputs must not be symlinks: " + str(child))


def local_news_server():
    source = ROOT / "scripts/local-news-server.py"
    validate_input(source)
    specification = importlib.util.spec_from_file_location("local_news", source)
    fixture = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(fixture)
    return fixture.LocalNewsServer()


def verify_ui_summary(document):
    """A successful xcodebuild exit with no executed tests is not acceptance."""
    if document.get("result") != "Passed" or document.get("failedTests", 0) != 0 or document.get("passedTests", 0) <= 0:
        raise ValueError("XCTest summary must contain executed passing tests and no failures")


def stage_project(destination):
    """Copy explicit build/source inputs, excluding local settings, credentials and generated files."""
    validate = validate_input

    ownership = ROOT / ".toolchain/generated/android-files.json"
    validate(ownership)
    generated = set(json.loads(ownership.read_text())) if ownership.exists() else set()

    def excluded(directory, names):
        return [name for name in names if name in ("generated", "__pycache__", "AGENTS.md", "CLAUDE.md")
                or str((Path(directory) / name).relative_to(ROOT)) in generated]

    for name in ("project.yaml", "libs.versions.toml", "kotlin", "kotlin.bat", ".swift-format", ".editorconfig"):
        validate(ROOT / name)
        shutil.copy2(ROOT / name, destination / name)
    for module in MODULES:
        source = ROOT / module
        validate(source / "module.yaml")
        target = destination / module
        target.mkdir(parents=True)
        for path in source.iterdir():
            if path.name == "module.yaml" or path.name == "plugin.yaml":
                validate(path)
                shutil.copy2(path, target / path.name)
            elif path.is_dir() and (
                path.name.startswith(("src", "test"))
                or path.name in ("res", "resources", "composeResources", "configuration", "schemas")
            ):
                validate(path)
                shutil.copytree(path, target / path.name,
                                ignore=excluded)
    project = destination / "iosApp/module.xcodeproj"
    project.mkdir()
    validate(ROOT / "iosApp/module.xcodeproj/project.pbxproj")
    shutil.copy2(ROOT / "iosApp/module.xcodeproj/project.pbxproj", project / "project.pbxproj")
    schemes = project / "xcshareddata/xcschemes"
    schemes.mkdir(parents=True)
    original_schemes = ROOT / "iosApp/module.xcodeproj/xcshareddata/xcschemes"
    scheme = original_schemes / "app.xcscheme"
    if not scheme.exists():
        scheme = original_schemes / "iosApp.xcscheme"
    validate(scheme)
    shutil.copy2(scheme, schemes / "app.xcscheme")
    pins = Path("iosApp/module.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved")
    if (ROOT / pins).exists():
        validate(ROOT / pins)
        (destination / pins).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / pins, destination / pins)
    scripts = destination / "scripts"
    scripts.mkdir()
    validate(ROOT / "scripts/prepare-toolchain.py")
    shutil.copy2(ROOT / "scripts/prepare-toolchain.py", scripts / "prepare-toolchain.py")
    validate(ROOT / "scripts/package-desktop.py")
    shutil.copy2(ROOT / "scripts/package-desktop.py", scripts / "package-desktop.py")
    # The generated Android manifest is recreated from canonical configuration.
    (destination / "androidApp/src/AndroidManifest.xml").unlink(missing_ok=True)
    # Verification copies must remain local-only if a developer later configures real public endpoints.
    info = destination / "iosApp/src/Info.plist"
    if info.exists():
        document = plistlib.loads(info.read_bytes())
        document.pop("SupabaseURL", None)
        document.pop("SupabasePublishableKey", None)
        info.write_bytes(plistlib.dumps(document, sort_keys=False))
    configuration = destination / "androidApp/res/values/client_configuration.xml"
    if configuration.exists():
        configuration.write_text('<resources><string name="supabase_url" translatable="false"></string>'
                                 '<string name="supabase_publishable_key" translatable="false"></string></resources>\n')


def run_ui_checks(checkout, workspace, arguments):
    """Create a temporary Xcode UI-test target; the production project is never rewritten."""
    fixtures = checkout / "iosApp/src/VerificationFixtures"
    fixtures.mkdir()
    for owner in ("auth-profile", "feed", "search", "favorites", "articles"):
        source = ROOT / "iosApp/verification" / owner
        validate_input(source)
        shutil.copytree(source, fixtures / owner)
    source = ROOT / "iosApp/verification/ui-tests"
    validate_input(source)
    shutil.copytree(source, checkout / "iosApp/verificationUITests")
    generator = ROOT / "iosApp/verification/support/UIProject.swift"
    validate_input(generator)
    support = workspace / "xcode-support"
    (support / "Sources").mkdir(parents=True)
    shutil.copy2(generator, support / "Sources/main.swift")
    (support / "Package.swift").write_text('''// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "VerificationProject", platforms: [.macOS(.v13)],
    dependencies: [.package(url: "https://github.com/tuist/XcodeProj.git", exact: "8.27.7")],
    targets: [.executableTarget(name: "VerificationProject", dependencies: ["XcodeProj"], path: "Sources")])
''')
    print("Evidence:", workspace, flush=True)
    with (workspace / "project-setup.log").open("w") as output:
        result = subprocess.run(["swift", "run", "--package-path", str(support), "VerificationProject",
                                 str(checkout / "iosApp/module.xcodeproj")],
                                stdout=output, stderr=subprocess.STDOUT)
    if result.returncode:
        raise SystemExit("UI-test project preparation failed; see " + str(workspace / "project-setup.log"))
    environment = dict(os.environ)
    environment.pop("KMPNEWS_TOOLCHAIN_PREPARED", None)
    command = ["xcrun", "xcodebuild", "-project", str(checkout / "iosApp/module.xcodeproj"),
               "-scheme", "FeatureUITests", "-configuration", "Debug", "-destination", "id=" + arguments.ios_simulator,
               "-derivedDataPath", str(workspace / "derivedData"), "-resultBundlePath", str(workspace / "results.xcresult"),
               "test", "SRCROOT=" + str(checkout / "iosApp"), "PROJECT_DIR=" + str(checkout / "iosApp"),
               "-parallel-testing-enabled", "NO"]
    command += ["-only-testing:FeatureUITests/" + selection for selection in arguments.only_testing]
    with (workspace / "ui-tests.log").open("w") as output:
        result = subprocess.run(command, cwd=checkout, env=environment, stdout=output, stderr=subprocess.STDOUT)
    if result.returncode:
        raise SystemExit("UI checks failed; see " + str(workspace / "ui-tests.log"))
    summary = subprocess.check_output(["xcrun", "xcresulttool", "get", "test-results", "summary",
                                       "--path", str(workspace / "results.xcresult")], text=True)
    (workspace / "summary.json").write_text(summary)
    document = json.loads(summary)
    verify_ui_summary(document)
    print("PASS:", document["passedTests"], "XCTest UI tests; skipped:", document.get("skippedTests", 0), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite", choices=(*SUITES, "all", "interop", "ui"), required=True)
    parser.add_argument("--ios-simulator", required=True)
    parser.add_argument("--only-testing", action="append", default=[], help="UI test class or class/method")
    arguments = parser.parse_args()
    devices = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "devices", "booted", "--json"]))
    if not any(device["udid"] == arguments.ios_simulator for group in devices["devices"].values() for device in group):
        parser.error("Select an already booted iOS Simulator; this script never creates or erases devices")

    workspace = Path(tempfile.mkdtemp(prefix="kmpnews-" + arguments.suite + "-", dir="/private/tmp")).resolve()
    checkout = workspace / "project"
    checkout.mkdir()
    stage_project(checkout)
    if arguments.suite == "ui":
        run_ui_checks(checkout, workspace, arguments)
        return
    if arguments.only_testing:
        parser.error("--only-testing is supported only with --suite ui")
    sources = checkout / "iosApp/src"
    (sources / "iosApp.swift").unlink()
    if arguments.suite in ("all", "interop"):
        calls = []
        if arguments.suite == "interop":
            directory = ROOT / "sharedLogic/interopTests"
            names = ("FeedInteropSmoke.swift", "SearchInteropSmoke.swift", "FavoritesInteropSmoke.swift", "NewsEngineInteropSmoke.swift")
            validate_input(ROOT / "sharedLogic/verification/appleInterop/com")
            shutil.copytree(ROOT / "sharedLogic/verification/appleInterop/com",
                            checkout / "sharedLogic/src@ios/com", dirs_exist_ok=True)
            contract = Path("com/ndynagn/kmp/news/feature/search/LocalNewsEngineContract.kt")
            validate_input(ROOT / "sharedLogic/test" / contract)
            shutil.copy2(ROOT / "sharedLogic/test" / contract, checkout / "sharedLogic/src@ios" / contract)
            configuration = checkout / "sharedLogic/module.yaml"
            configuration.write_text(configuration.read_text().replace(
                "dependencies:\n", "dependencies:\n  - $libs.ktor.client.mock\n", 1))
        else:
            directory = ROOT / "iosApp/presentationTests"
            names = (name for key, name in SUITES.items() if key != "keychain")
        for name in names:
            validate_input(directory / name)
            content = (directory / name).read_text()
            entry = re.search(r"@main\s+struct\s+(\w+)", content)
            if not entry or content.count("@main") != 1:
                raise SystemExit("Expected one test entry point in " + name)
            (sources / name).write_text(content.replace("@main", "", 1))
            declaration = re.search(r"static func main\(\)([^\{]*)", content)
            if not declaration:
                raise SystemExit("Missing main declaration in " + name)
            modifiers = declaration.group(1)
            prefix = ("try " if "throws" in modifiers else "") + ("await " if "async" in modifiers else "")
            calls.append("        " + prefix + entry.group(1) + ".main()")
        (sources / "VerificationMain.swift").write_text(
            "import Foundation\n@main struct FeatureVerification {\n"
            "    @MainActor static func main() async throws {\n" + "\n".join(calls)
            + '\n        print("PASS: selected feature suites completed")\n    }\n}\n')
    else:
        validate_input(ROOT / "iosApp/presentationTests" / SUITES[arguments.suite])
        shutil.copy2(ROOT / "iosApp/presentationTests" / SUITES[arguments.suite], sources / "VerificationMain.swift")
    fixtures = sources / "VerificationFixtures"
    fixtures.mkdir()
    for owner in ("auth-profile", "feed", "search", "favorites", "articles"):
        validate_input(ROOT / "iosApp/verification" / owner)
        shutil.copytree(ROOT / "iosApp/verification" / owner, fixtures / owner)
    identifier = "com.ndynagn.kmp.news.verification." + arguments.suite.replace("-", "")
    info = sources / "Info.plist"
    document = plistlib.loads(info.read_bytes())
    document["CFBundleIdentifier"] = identifier
    if arguments.suite == "interop":
        document["NSAppTransportSecurity"] = {"NSAllowsLocalNetworking": True}
    info.write_bytes(plistlib.dumps(document, sort_keys=False))
    environment = dict(os.environ)
    environment.pop("KMPNEWS_TOOLCHAIN_PREPARED", None)
    build_log = workspace / "build.log"
    print("Evidence:", workspace, flush=True)

    with build_log.open("w") as output:
        result = subprocess.run(
            ["xcrun", "xcodebuild", "-project", str(checkout / "iosApp/module.xcodeproj"),
             "-scheme", "app", "-configuration", "Debug", "-destination", "id=" + arguments.ios_simulator,
             "-derivedDataPath", str(workspace / "derivedData"), "build", "PRODUCT_BUNDLE_IDENTIFIER=" + identifier,
             "SRCROOT=" + str(checkout / "iosApp"), "PROJECT_DIR=" + str(checkout / "iosApp"),
             "SWIFT_ACTIVE_COMPILATION_CONDITIONS=DEBUG FEATURE_VERIFICATION"],
            cwd=checkout, env=environment, stdout=output, stderr=subprocess.STDOUT,
        )
    if result.returncode:
        raise SystemExit("Feature check build failed; see " + str(build_log))
    applications = list((workspace / "derivedData/Build/Products/Debug-iphonesimulator").glob("*.app"))
    if len(applications) != 1:
        raise SystemExit("Expected exactly one Simulator check application")
    application = applications[0]
    actual_identifier = plistlib.loads((application / "Info.plist").read_bytes())["CFBundleIdentifier"]
    if actual_identifier != identifier:
        raise SystemExit("Refusing to install a verification app with the production bundle identifier")
    subprocess.run(["xcrun", "simctl", "install", arguments.ios_simulator, str(application)], check=True)
    phases = ("write", "read") if arguments.suite == "keychain" else (None,)
    with local_news_server() if arguments.suite == "interop" else contextlib.nullcontext(None) as server:
        for phase in phases:
            run = subprocess.run(
                ["xcrun", "simctl", "launch", "--console-pty", arguments.ios_simulator, identifier]
                + ([phase] if phase else []) + (["--news-port", str(server.port)] if server else []),
                text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=120,
            )
            log = workspace / ((phase or "run") + ".log")
            log.write_text(run.stdout)
            print(run.stdout, end="")
            marker = "PASS: selected feature suites completed" if arguments.suite in ("all", "interop") else "PASS:"
            if run.returncode or marker not in run.stdout:
                raise SystemExit("Feature check did not report PASS; see " + str(log))


if __name__ == "__main__":
    main()
