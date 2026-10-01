"""Build an isolated simulator app to verify the real Keychain adapter across process restart.

Usage: python3 iosApp/presentationTests/verify_auth_keychain.py SIMULATOR_UDID
Requires the shared iosSimulatorArm64 Debug framework produced by an iOS build.
The fixture app is removed after verification; it never calls hosted Auth.
"""

import pathlib
import plistlib
import subprocess
import sys
import tempfile
import time

ROOT = pathlib.Path(__file__).resolve().parents[2]
DEVICE = sys.argv[1]
BUNDLE_ID = "com.ndynagn.kmp.news.authverification"
FRAMEWORKS = ROOT / "sharedLogic/build/bin/iosSimulatorArm64/debugFramework"


def run(*args):
    return subprocess.check_output(args, text=True).strip()


with tempfile.TemporaryDirectory(prefix="kmp-auth-keychain-") as temporary:
    folder = pathlib.Path(temporary)
    app = folder / "AuthVerification.app"
    app.mkdir()
    (app / "Info.plist").write_bytes(plistlib.dumps({
        "CFBundleIdentifier": BUNDLE_ID,
        "CFBundleExecutable": "AuthVerification",
        "CFBundleName": "AuthVerification",
        "CFBundlePackageType": "APPL",
        "CFBundleVersion": "1",
        "CFBundleShortVersionString": "1.0",
        "MinimumOSVersion": "18.2",
        "UILaunchScreen": {},
        "UIDeviceFamily": [1, 2],
    }))
    entitlements = folder / "simulator-entitlements.plist"
    entitlements.write_bytes(plistlib.dumps({"application-identifier": "LOCALTEST." + BUNDLE_ID}))
    sdk = run("xcrun", "--sdk", "iphonesimulator", "--show-sdk-path")
    run("xcrun", "--sdk", "iphonesimulator", "swiftc", "-parse-as-library", "-sdk", sdk,
        "-target", "arm64-apple-ios18.2-simulator", "-F", str(FRAMEWORKS),
        "-framework", "SharedLogic", "-framework", "Security",
        "-Xlinker", "-dead_strip", "-Xlinker", "-syslibroot", "-Xlinker", sdk,
        "-Xlinker", "-sectcreate", "-Xlinker", "__TEXT",
        "-Xlinker", "__entitlements", "-Xlinker", str(entitlements),
        str(ROOT / "iosApp/iosApp/Features/Auth/KeychainAuthStorage.swift"),
        str(ROOT / "iosApp/presentationTests/KeychainVerificationApp.swift"),
        "-o", str(app / "AuthVerification"))
    run("codesign", "--force", "--sign", "-", str(app))
    run("xcrun", "simctl", "install", DEVICE, str(app))
    try:
        for phase in range(2):
            run("xcrun", "simctl", "launch", DEVICE, BUNDLE_ID)
            container = pathlib.Path(run("xcrun", "simctl", "get_app_container", DEVICE, BUNDLE_ID, "data"))
            result = container / "Documents/result.txt"
            expected = f"PASS phase {phase}"
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                if result.exists() and result.read_text() == expected:
                    break
                time.sleep(0.2)
            else:
                raise RuntimeError(result.read_text() if result.exists() else "No harness result")
            print(expected)
            run("xcrun", "simctl", "terminate", DEVICE, BUNDLE_ID)
        print("PASS: Keychain write, read after process restart, and deletion")
    finally:
        run("xcrun", "simctl", "uninstall", DEVICE, BUNDLE_ID)
