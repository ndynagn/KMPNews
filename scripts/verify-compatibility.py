#!/usr/bin/env python3
"""Stage neutral opt-in probes in a temporary checkout and verify actual platform libraries."""

import argparse
import hashlib
import http.server
import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import tempfile
import threading
import time

ROOT = Path(__file__).resolve().parent.parent


class FixtureServer(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        status = 500 if self.path == "/http" else 200
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.flush()
        if self.path == "/wait":
            time.sleep(3)
        try:
            self.wfile.write(b"{broken" if self.path == "/malformed" else b'{"value":42}')
        except (BrokenPipeError, ConnectionResetError):
            pass

    def log_message(self, *_arguments):
        pass


EXCLUDED = {"build", "generated", "vendor", "third-party", ".git", ".idea", ".agents", ".skills",
            ".gradle", ".kotlin", ".toolchain", "node_modules",
            "xcuserdata", "__pycache__", "AGENTS.md", "CLAUDE.md", ".DS_Store", "local.properties",
            "google-services.json", "GoogleService-Info.plist"}


def copy_source(source, destination):
    """Copy an approved tree without local state or following any source symlink."""
    relative = source.relative_to(ROOT)
    current = ROOT
    for part in relative.parts:
        current /= part
        if current.is_symlink():
            raise ValueError("Probe staging refuses source symlinks: " + str(current))
    if source.name in EXCLUDED or source.name.startswith(".env") or source.suffix in (".jks", ".keystore", ".p12"):
        return
    if source.is_dir():
        destination.mkdir(parents=True, exist_ok=True)
        for child in sorted(source.iterdir()):
            copy_source(child, destination / child.name)
    else:
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)


def stage(destination):
    """Stage approved build/source/resource roots; fixtures are enabled only in this disposable checkout."""
    for name in ("kotlin", "kotlin.bat", "project.yaml", "libs.versions.toml",
                 "scripts/prepare-toolchain.py", "scripts/verify-compatibility.py", "tooling/style"):
        copy_source(ROOT / name, destination / name)
    for name in ("androidApp", "desktopApp", "iosApp", "sharedLogic", "sharedUI"):
        module = ROOT / name
        copy_source(module / "module.yaml", destination / name / "module.yaml")
        for child in module.iterdir():
            if child.name in ("res", "composeResources", "proguard-rules.pro") or (
                child.name.split("@", 1)[0] in ("src", "test")
            ):
                copy_source(child, destination / name / child.name)
    for relative in ("project.pbxproj", "xcshareddata/xcschemes"):
        source = ROOT / "iosApp/module.xcodeproj" / relative
        copy_source(source, destination / "iosApp/module.xcodeproj" / relative)
    fixtures = ROOT / "sharedLogic/verification/compatibility"
    for name, target in (("common", "src"), ("jvm", "src@jvm"), ("ios", "src@ios"),
                         ("android", "src@android"), ("jvm-test", "test@jvm")):
        copy_source(fixtures / name, destination / "sharedLogic" / target / "compatibility")
    module = destination / "sharedLogic/module.yaml"
    content = module.read_text().replace("  - $libs.ktor.client.mock\n", "")
    module.write_text(content.replace("dependencies:\n", "dependencies:\n  - $libs.ktor.client.mock\n", 1))
    android = destination / "androidApp"
    copy_source(ROOT / "androidApp/verification/CompatibilityActivity.kt", android / "src/CompatibilityActivity.kt")
    module = android / "module.yaml"
    content = module.read_text().replace("dependencies:\n", "dependencies:\n  - //sharedLogic\n", 1)
    module.write_text(content.replace("applicationId: com.ndynagn.kmp.news",
                                      "applicationId: com.ndynagn.kmp.news.compatibility"))
    (android / "src/AndroidManifest.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application android:usesCleartextTraffic="true" android:label="Compatibility">
        <activity android:name=".CompatibilityActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
''')
    ios = destination / "iosApp"
    copy_source(ROOT / "iosApp/verification/CompatibilityApp.swift", ios / "src/iosApp.swift")
    project = ios / "module.xcodeproj/project.pbxproj"
    project.write_text(project.read_text().replace("com.ndynagn.kmp.news", "com.ndynagn.kmp.news.compatibility"))
    info = ios / "src/Info.plist"
    data = plistlib.loads(info.read_bytes())
    data["NSAppTransportSecurity"] = {"NSAllowsArbitraryLoads": True}
    info.write_bytes(plistlib.dumps(data))
    inventory = {str(path.relative_to(destination)): hashlib.sha256(path.read_bytes()).hexdigest()
                 for path in destination.rglob("*") if path.is_file()}
    (destination.parent / (destination.name + "-staged-files.json")).write_text(json.dumps(inventory, indent=2) + "\n")


def run(checkout, label, arguments, environment, success=True):
    with (checkout.parent / (checkout.name + "-" + label + ".log")).open("w") as output:
        result = subprocess.run(arguments, cwd=checkout, env=environment, stdout=output, stderr=subprocess.STDOUT)
    if (result.returncode == 0) != success:
        raise RuntimeError("Unexpected result for " + label + "; inspect its temporary log")


def verify_jvm(checkout, environment):
    wrapper = str(checkout / "kotlin")
    for label in ("jvm", "jvm-repeat"):
        run(checkout, label, [wrapper, "test", "-m", "sharedLogic", "-p", "jvm"], environment)
    source = checkout / "sharedLogic/src/compatibility/CompatibilityProbe.kt"
    original = source.read_text()
    try:
        source.write_text(original.replace("                    single<ProbeDependency>()\n", ""))
        run(checkout, "koin-missing-binding", [wrapper, "build", "-m", "sharedLogic", "-p", "jvm"],
            environment, success=False)
        log = (checkout.parent / (checkout.name + "-koin-missing-binding.log")).read_text()
        if "[Koin][KOIN-D001] Missing dependency:" not in log or "ProbeDependency" not in log:
            raise RuntimeError("Negative compilation did not establish the missing Koin binding")
    finally:
        source.write_text(original)
    print("PASS: JVM probes, warm repeat and missing-binding compiler rejection", flush=True)


def poll_result(read_result):
    for _attempt in range(120):
        result = read_result()
        if result:
            if not result.startswith("PASS:"):
                raise RuntimeError(result)
            print(result, flush=True)
            return
        time.sleep(1)
    raise RuntimeError("Timed out waiting for platform compatibility result")


def verify_android(checkout, environment, serial, port):
    adb = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Library/Android/sdk"))) / "platform-tools/adb"
    prefix = [str(adb), "-s", serial]
    run(checkout, "android", [str(checkout / "kotlin"), "build", "-m", "androidApp", "-p", "android"], environment)
    candidates = list((checkout / "build/tasks/_androidApp_buildAndroidDebug").glob("*-debug.apk"))
    if len(candidates) != 1:
        raise RuntimeError("Cannot select a unique probe Debug APK: " + str(candidates))
    subprocess.run(prefix + ["reverse", "tcp:" + str(port), "tcp:" + str(port)], check=True)
    try:
        subprocess.run(prefix + ["install", "-r", str(candidates[0])], check=True)
        subprocess.run(prefix + ["shell", "am", "force-stop", "com.ndynagn.kmp.news.compatibility"], check=True)
        subprocess.run(prefix + ["shell", "run-as", "com.ndynagn.kmp.news.compatibility",
                                 "rm", "-f", "files/compatibility.txt"], check=True)
        subprocess.run(prefix + ["shell", "am", "start", "-S", "-W", "-n",
                                 "com.ndynagn.kmp.news.compatibility/com.ndynagn.kmp.news.CompatibilityActivity",
                                 "--es", "serverUrl", environment["KMPNEWS_FIXTURE_SERVER"]], check=True)

        def read_result():
            result = subprocess.run(prefix + ["shell", "run-as", "com.ndynagn.kmp.news.compatibility",
                                              "cat", "files/compatibility.txt"], capture_output=True, text=True)
            return result.stdout if result.returncode == 0 else ""

        poll_result(read_result)
    finally:
        subprocess.run(prefix + ["reverse", "--remove", "tcp:" + str(port)], check=True)


def verify_ios(checkout, environment, simulator):
    run(checkout, "ios", [str(checkout / "kotlin"), "build", "-m", "iosApp", "-p", "iosSimulatorArm64"], environment)
    output = checkout / "build/tasks/_iosApp_buildIosAppIosSimulatorArm64Debug/bin/Debug-iphonesimulator"
    apps = list(output.glob("*.app"))
    if len(apps) != 1:
        raise RuntimeError("Cannot select a unique probe iOS application")
    subprocess.run(["xcrun", "simctl", "install", simulator, str(apps[0])], check=True)
    app = "com.ndynagn.kmp.news.compatibility"
    # Reused Simulator containers must not supply a stale PASS from an earlier probe.
    subprocess.run(["xcrun", "simctl", "terminate", simulator, app], capture_output=True)
    container = subprocess.check_output(
        ["xcrun", "simctl", "get_app_container", simulator, app, "data"], text=True,
    ).strip()
    result_file = Path(container) / "Documents/compatibility.txt"
    result_file.unlink(missing_ok=True)
    launch_environment = dict(environment, SIMCTL_CHILD_KMPNEWS_FIXTURE_SERVER=environment["KMPNEWS_FIXTURE_SERVER"])
    subprocess.run(["xcrun", "simctl", "launch", simulator, app], env=launch_environment, check=True)
    poll_result(lambda: result_file.read_text() if result_file.exists() else "")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", choices=("jvm", "android", "ios", "all"), default="all")
    parser.add_argument("--android-serial", default="emulator-5554")
    parser.add_argument("--ios-simulator", help="Booted Simulator UUID; required for iOS")
    arguments = parser.parse_args()
    if arguments.platform in ("ios", "all") and not arguments.ios_simulator:
        parser.error("Select an available booted iOS Simulator with --ios-simulator")
    checkout = Path(tempfile.mkdtemp(prefix="kmpnews-compatibility-", dir=tempfile.gettempdir()))
    stage(checkout)
    print("Disposable checkout and evidence: " + str(checkout), flush=True)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), FixtureServer)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    environment = dict(os.environ, KMPNEWS_FIXTURE_SERVER="http://127.0.0.1:" + str(server.server_port))
    try:
        if arguments.platform in ("jvm", "all"):
            verify_jvm(checkout, environment)
        if arguments.platform in ("android", "all"):
            verify_android(checkout, environment, arguments.android_serial, server.server_port)
        if arguments.platform in ("ios", "all"):
            verify_ios(checkout, environment, arguments.ios_simulator)
    finally:
        server.shutdown()
        server.server_close()


if __name__ == "__main__":
    main()
