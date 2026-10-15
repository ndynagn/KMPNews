#!/usr/bin/env python3
"""Check feature persistence and Keystore across process restarts in an isolated emulator app."""

import argparse
import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parent.parent
IDENTIFIER = "com.ndynagn.kmp.news.verification.features"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--emulator", required=True)
    arguments = parser.parse_args()
    if not arguments.emulator.startswith("emulator-"):
        parser.error("Select an existing emulator; physical devices are outside this harness")
    adb = [arguments.adb, "-s", arguments.emulator]
    subprocess.run([*adb, "get-state"], check=True, stdout=subprocess.DEVNULL)
    specification = importlib.util.spec_from_file_location("ios_features", ROOT / "scripts/verify-ios-features.py")
    helper = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(helper)
    workspace = Path(tempfile.mkdtemp(prefix="kmpnews-android-features-", dir="/private/tmp")).resolve()
    checkout = workspace / "project"
    checkout.mkdir()
    helper.stage_project(checkout)
    helper.validate_input(ROOT / "sharedLogic/verification/android/com")
    shutil.copytree(ROOT / "sharedLogic/verification/android/com", checkout / "sharedLogic/src@android/com",
                    dirs_exist_ok=True)
    contract = Path("com/ndynagn/kmp/news/feature/favorites/FavoritesPersistenceContract.kt")
    helper.validate_input(ROOT / "sharedLogic/test" / contract)
    shutil.copy2(ROOT / "sharedLogic/test" / contract, checkout / "sharedLogic/src@android" / contract)
    news_contract = Path("com/ndynagn/kmp/news/feature/search/LocalNewsEngineContract.kt")
    helper.validate_input(ROOT / "sharedLogic/test" / news_contract)
    shutil.copy2(ROOT / "sharedLogic/test" / news_contract, checkout / "sharedLogic/src@android" / news_contract)
    configuration = checkout / "sharedLogic/module.yaml"
    configuration.write_text(configuration.read_text().replace(
        "dependencies@android:\n", "dependencies@android:\n  - org.jetbrains.kotlin:kotlin-test:2.4.20\n", 1))
    helper.validate_input(ROOT / "androidApp/verification/features")
    shutil.copytree(ROOT / "androidApp/verification/features", checkout / "androidApp/src/verification")
    application = checkout / "androidApp/module.yaml"
    original = application.read_text()
    if original.count("applicationId: com.ndynagn.kmp.news") != 1:
        raise SystemExit("Expected exactly one production applicationId before isolating the harness")
    application.write_text(original.replace("applicationId: com.ndynagn.kmp.news", "applicationId: " + IDENTIFIER))
    manifest = checkout / "androidApp/configuration/main/AndroidManifest.xml"
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="KMPNews feature verification" android:usesCleartextTraffic="true">
        <activity android:name="com.ndynagn.kmp.news.verification.FeatureVerificationActivity"
                  android:exported="true" />
    </application>
</manifest>
''')
    print("Evidence:", workspace, flush=True)
    environment = dict(os.environ)
    environment.pop("KMPNEWS_TOOLCHAIN_PREPARED", None)
    with (workspace / "build.log").open("w") as output:
        build = subprocess.run([str(checkout / "kotlin"), "build", "-m", "androidApp", "-p", "android", "-v", "debug"],
                               cwd=checkout, env=environment, stdout=output, stderr=subprocess.STDOUT)
    if build.returncode:
        raise SystemExit("Feature app build failed; see " + str(workspace / "build.log"))
    apk = checkout / "build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk"
    analyzer = Path(arguments.adb).resolve().parents[1] / "cmdline-tools/latest/bin/apkanalyzer"
    actual_identifier = subprocess.check_output([str(analyzer), "manifest", "application-id", str(apk)], text=True).strip()
    if actual_identifier != IDENTIFIER:
        raise SystemExit("Refusing to install a feature harness with a different application ID")
    subprocess.run([*adb, "install", "-r", str(apk)], check=True)
    with helper.local_news_server() as server:
        port = "tcp:" + str(server.port)
        subprocess.run([*adb, "reverse", "--no-rebind", port, port], check=True)
        try:
            for phase in ("write", "read"):
                subprocess.run([*adb, "shell", "am", "force-stop", IDENTIFIER], check=True)
                subprocess.run([*adb, "shell", "run-as", IDENTIFIER, "rm", "-f", "files/verification-result.txt"], check=True)
                subprocess.run([*adb, "shell", "am", "start", "-n",
                                IDENTIFIER + "/com.ndynagn.kmp.news.verification.FeatureVerificationActivity",
                                "--es", "phase", phase, "--ei", "newsPort", str(server.port)], check=True)
                deadline = time.monotonic() + 90
                while time.monotonic() < deadline:
                    result = subprocess.run([*adb, "shell", "run-as", IDENTIFIER, "cat", "files/verification-result.txt"],
                                            text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
                    if result.returncode == 0:
                        (workspace / (phase + ".log")).write_text(result.stdout)
                        if result.stdout != "PASS":
                            raise SystemExit("Feature check failed: " + result.stdout)
                        print("PASS:", phase, flush=True)
                        break
                    time.sleep(0.5)
                else:
                    raise SystemExit("Feature check timed out in phase " + phase)
        finally:
            subprocess.run([*adb, "shell", "am", "force-stop", IDENTIFIER], check=True)
            subprocess.run([*adb, "reverse", "--remove", port], check=True)



if __name__ == "__main__":
    main()
