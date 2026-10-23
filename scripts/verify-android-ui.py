#!/usr/bin/env python3
"""Run archived feature-owned Compose UI suites in a disposable self-instrumenting emulator APK."""

import argparse
import importlib.util
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
IDENTIFIER = "com.ndynagn.kmp.news.verification.ui"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--emulator", required=True)
    parser.add_argument("--class", dest="classes", action="append", default=[])
    parser.add_argument("--auth-storage-phase", choices=("write", "read"))
    arguments = parser.parse_args()
    if not arguments.emulator.startswith("emulator-"):
        parser.error("Choose an existing emulator, never a physical device")
    classes = arguments.classes or [
        "com.ndynagn.kmp.news.feature.auth.ProfileScreenTest",
        "com.ndynagn.kmp.news.feature.feed.FeedScreenTest",
        "com.ndynagn.kmp.news.feature.componentcatalog.AppComponentsTest",
        "com.ndynagn.kmp.news.feature.componentcatalog.ComponentCatalogTest",
    ]
    if arguments.auth_storage_phase:
        if arguments.classes:
            parser.error("--auth-storage-phase selects its own feature suite")
        classes = ["com.ndynagn.kmp.news.feature.auth.AuthStorageProcessTest"]
    if not all(re.fullmatch(r"com\.ndynagn\.kmp\.news\.feature\.[A-Za-z0-9_.]+", name) for name in classes):
        parser.error("Only feature-owned test class names are accepted")
    adb = [arguments.adb, "-s", arguments.emulator]
    subprocess.run([*adb, "get-state"], check=True, stdout=subprocess.DEVNULL)
    specification = importlib.util.spec_from_file_location("feature_checks", ROOT / "scripts/verify-ios-features.py")
    helper = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(helper)
    workspace = Path(tempfile.mkdtemp(prefix="kmpnews-android-ui-", dir="/private/tmp")).resolve()
    checkout = workspace / "project"
    checkout.mkdir()
    helper.stage_project(checkout)
    source = ROOT / "androidApp/verification/ui-tests"
    helper.validate_input(source)
    shutil.copytree(source, checkout / "androidApp/src/verificationUITests")
    module = checkout / "androidApp/module.yaml"
    configuration = module.read_text()
    if configuration.count("applicationId: com.ndynagn.kmp.news") != 1:
        raise SystemExit("Expected one application ID before isolating UI checks")
    configuration = configuration.replace("applicationId: com.ndynagn.kmp.news", "applicationId: " + IDENTIFIER)
    configuration = configuration.replace("dependencies:\n", """dependencies:
  - androidx.test:runner:1.7.0
  - androidx.test.ext:junit:1.3.0
  - androidx.test.espresso:espresso-core:3.7.0
  - androidx.compose.ui:ui-test-junit4:1.12.1
  - org.jetbrains.kotlin:kotlin-test-junit:2.4.20
  - $libs.junit
""", 1)
    configuration = configuration.replace("    applicationId: " + IDENTIFIER,
        "    applicationId: " + IDENTIFIER + "\n    resourcePackaging:\n"
        "      excludes: ['/META-INF/AL2.0', '/META-INF/LGPL2.1']")
    module.write_text(configuration)
    (checkout / "androidApp/configuration/main/AndroidManifest.xml").write_text('''
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="KMPNews UI verification" android:allowBackup="false">
        <activity android:name="androidx.activity.ComponentActivity" android:exported="true"
                  android:windowSoftInputMode="adjustResize" />
        <activity android:name="com.ndynagn.kmp.news.feature.article.ReaderVerificationActivity"
                  android:exported="false" android:windowSoftInputMode="adjustResize" />
    </application>
    <instrumentation android:name="androidx.test.runner.AndroidJUnitRunner"
                     android:targetPackage="com.ndynagn.kmp.news.verification.ui" />
</manifest>
''')
    environment = dict(os.environ)
    environment.pop("KMPNEWS_TOOLCHAIN_PREPARED", None)
    print("Evidence:", workspace, flush=True)
    with (workspace / "build.log").open("w") as output:
        result = subprocess.run([str(checkout / "kotlin"), "build", "-m", "androidApp", "-p", "android", "-v", "debug"],
                                cwd=checkout, env=environment, stdout=output, stderr=subprocess.STDOUT)
    if result.returncode:
        raise SystemExit("UI APK build failed; see " + str(workspace / "build.log"))
    apk = checkout / "build/tasks/_androidApp_buildAndroidDebug/gradle-project-debug.apk"
    analyzer = Path(arguments.adb).resolve().parents[1] / "cmdline-tools/latest/bin/apkanalyzer"
    if subprocess.check_output([str(analyzer), "manifest", "application-id", str(apk)], text=True).strip() != IDENTIFIER:
        raise SystemExit("Refusing to install UI checks under another application ID")
    subprocess.run([*adb, "install", "-r", str(apk)], check=True)
    command = [*adb, "shell", "am", "instrument", "-w", "-r", "-e", "class", ",".join(classes)]
    if arguments.auth_storage_phase:
        command += ["-e", "authStoragePhase", arguments.auth_storage_phase]
    command += [IDENTIFIER + "/androidx.test.runner.AndroidJUnitRunner"]
    try:
        result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=900)
        (workspace / "ui-tests.log").write_text(result.stdout)
        passed = re.search(r"OK \((\d+) tests?\)", result.stdout)
        if result.returncode or not passed or int(passed.group(1)) == 0 or "FAILURES!!!" in result.stdout:
            raise SystemExit("UI checks did not pass; see " + str(workspace / "ui-tests.log"))
        print("PASS:", passed.group(0), flush=True)
    finally:
        subprocess.run([*adb, "shell", "am", "force-stop", IDENTIFIER], check=True)


if __name__ == "__main__":
    main()
