#!/usr/bin/env python3
"""Prepare checkout-specific SKIE/Room paths and fresh Koin compilation, then delegate to the official wrapper."""

import contextlib
import json
import os
import re
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent


def safe_path(relative):
    """Reject symlinks before writing inside this checkout."""
    current = ROOT
    for part in Path(relative).parts:
        current /= part
        if current.is_symlink():
            raise ValueError("Toolchain paths must not be symlinks: " + str(current))
    return current


def write_if_changed(path, text):
    if path.exists() and path.read_text() == text:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    safe_path(temporary.relative_to(ROOT))
    temporary.write_text(text)
    temporary.replace(path)


def option_values(arguments, short, long):
    values = []
    for index, value in enumerate(arguments):
        if value in (short, long):
            if index + 1 == len(arguments):
                raise ValueError("Missing argument for " + value)
            values.append(arguments[index + 1])
        elif value.startswith(long + "="):
            values.append(value.split("=", 1)[1])
    return values


def profile(arguments):
    platforms = option_values(arguments, "-p", "--platform")
    modules = option_values(arguments, "-m", "--module")
    if any(command in arguments for command in ("build", "run")) and not platforms and (
        not modules or "iosApp" in modules
    ):
        raise ValueError("Select one explicit -p platform for Apple/whole-project builds and runs")
    apple = [item for item in platforms if item.startswith("ios")]
    task_profiles = [
        match for value in arguments for match in re.findall(r"Ios(SimulatorArm64|Arm64)(Debug|Release)", value)
    ]
    if task_profiles:
        apple.extend("ios" + target for target, _variant in task_profiles)
    if len(set(apple)) > 1:
        raise ValueError("Build Apple platforms separately so SKIE caches have one target per invocation")
    platform = apple[0] if apple else (
        "iosArm64" if os.environ.get("SDK_NAME", "").startswith("iphoneos") else "iosSimulatorArm64"
    )
    variants = option_values(arguments, "-v", "--variant")
    variants.extend(variant.lower() for _target, variant in task_profiles)
    if len(set(variants)) > 1:
        raise ValueError("Build Debug and Release separately")
    variant = variants[0] if variants else os.environ.get("CONFIGURATION", "debug").lower()
    if platform not in ("iosArm64", "iosSimulatorArm64") or variant not in ("debug", "release"):
        raise ValueError("Unsupported SKIE profile: " + platform + "/" + variant)
    return platform, variant


def prepare(arguments):
    for option, expected in (("--project-dir", ROOT), ("--build-dir", ROOT / "build")):
        values = option_values(arguments, option, option)
        if any(Path(value).resolve() != expected for value in values):
            raise ValueError("The project adapter requires the default checkout/build directories")
    configured = os.environ.get("KOTLIN_TOOLCHAIN_BUILD_DIR")
    if configured and Path(configured).resolve() != ROOT / "build":
        raise ValueError("The project adapter requires the default build directory")
    platform, variant = profile(arguments)
    directory = safe_path("build/skie/" + platform + "/" + variant)
    for relative in (
        "analytics", "cache/swift-module-cache", "cache/kotlin-framework",
        "debug/analytics", "debug/logs", "debug/dumps", "swift/generated", "swift/bundled",
        "temp/gradle/merged-custom-swift", "swift-compiler/object-files", "swift-compiler/module",
        "swift-compiler/fake-objc-frameworks", "swift-compiler/apinotes", "swift-compiler/config",
    ):
        safe_path((directory / relative).relative_to(ROOT)).mkdir(parents=True, exist_ok=True)
    configuration = {
        "enabledConfigurationFlags": [
            "Build_ParallelSwiftCompilation", "Build_ConcurrentSkieCompilation", "Build_ParallelSkieCompilation",
            "Debug_VerifyDescriptorProviderConsistency", "Feature_CoroutinesInterop",
        ],
        "groups": [],
    }
    write_if_changed(directory / "configuration.json", json.dumps(configuration, indent=4) + "\n")
    write_if_changed(safe_path(".toolchain/generated/skie.module-template.yaml"),
                     "settings:\n  kotlin:\n    freeCompilerArgs:\n"
                     "      - '-Xexport-library=co.touchlab.skie:runtime-kotlin'\n"
                     "    compilerPlugins:\n      - id: co.touchlab.skie\n"
                     "        dependency: co.touchlab.skie:kotlin-compiler-linker-plugin:0.10.15\n"
                     "        options:\n          skieBuildDirectory: " + json.dumps(str(directory)) + "\n")
    write_if_changed(safe_path(".toolchain/generated/room.module-template.yaml"),
                     "settings:\n  kotlin:\n    ksp:\n      processorOptions:\n"
                     "        room.schemaLocation: " + json.dumps(str(ROOT / "sharedLogic/schemas")) + "\n")

    if any(value in ("build", "test", "run", "package", "task", "tool") for value in arguments):
        state = safe_path("build/incremental.state")
        for path in state.glob("_sharedLogic_compile*-*"):
            if path.is_symlink() or not path.is_file():
                raise ValueError("Unexpected Koin compilation state: " + str(path))
            path.unlink()


@contextlib.contextmanager
def checkout_lock():
    lock = safe_path(".toolchain/prepare.lock")
    lock.parent.mkdir(parents=True, exist_ok=True)
    with lock.open("a+b") as stream:
        if os.name == "nt":
            import msvcrt
            stream.seek(0)
            stream.write(b"0")
            stream.flush()
            stream.seek(0)
            msvcrt.locking(stream.fileno(), msvcrt.LK_LOCK, 1)
        else:
            import fcntl
            fcntl.flock(stream, fcntl.LOCK_EX)
        try:
            yield
        finally:
            if os.name == "nt":
                stream.seek(0)
                msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(stream, fcntl.LOCK_UN)


def main():
    arguments = sys.argv[1:]
    with checkout_lock():
        prepare(arguments)
        environment = dict(os.environ, KMPNEWS_TOOLCHAIN_PREPARED="1")
        command = [str(ROOT / "kotlin.bat")] if os.name == "nt" else ["/bin/sh", str(ROOT / "kotlin")]
        return subprocess.call(command + arguments, cwd=ROOT, env=environment)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError) as error:
        print("Toolchain preparation failed: " + str(error), file=sys.stderr)
        sys.exit(1)
