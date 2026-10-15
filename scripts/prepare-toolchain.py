#!/usr/bin/env python3
"""Prepare checkout-specific SKIE/Room paths and fresh Koin compilation, then delegate to the official wrapper."""

import contextlib
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent


def safe_path(relative):
    """Reject symlinks before writing inside this checkout."""
    relative = Path(relative)
    if relative.is_absolute() or ".." in relative.parts:
        raise ValueError("Toolchain paths must stay inside the checkout: " + str(relative))
    current = ROOT
    for part in relative.parts:
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
    variants.extend(match.lower() for value in arguments for match in re.findall(r"Android(Debug|Release)", value))
    if len(set(variants)) > 1:
        raise ValueError("Build Debug and Release separately")
    default_variant = "release" if "package" in arguments else "debug"
    variant = variants[0] if variants else os.environ.get("CONFIGURATION", default_variant).lower()
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
    prepare_android(variant)
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


def prepare_android(variant):
    """Stage only variant-owned catalog files; reject edits to generated outputs."""
    inputs = safe_path("androidApp/configuration")
    if not inputs.exists():
        return
    state = safe_path(".toolchain/generated/android-files.json")
    previous = json.loads(state.read_text()) if state.exists() else {}
    def owned_output(relative):
        path = safe_path(relative)
        is_source = relative.startswith(("androidApp/src/generated/", "androidApp/test/generated/"))
        is_resource = re.fullmatch(
            r"androidApp/res/(?:values(?:-[\w-]+)?/kmpnews_catalog_[\w.-]+|drawable(?:-[\w-]+)?/catalog_[\w.-]+)",
            relative,
        )
        if not (is_source or is_resource or relative == "androidApp/src/AndroidManifest.xml"):
            raise ValueError("Invalid generated Android output: " + relative)
        return path

    observed = {}
    for relative, digest in previous.items():
        path = owned_output(relative)
        digests = digest if isinstance(digest, list) else [digest]
        if not 1 <= len(digests) <= 2 or any(not re.fullmatch(r"[0-9a-f]{64}", item) for item in digests):
            raise ValueError("Invalid generated Android digest: " + relative)
        if path.exists():
            actual = hashlib.sha256(path.read_bytes()).hexdigest()
            if actual not in digests:
                raise ValueError("Edited generated Android output: " + relative)
            observed[relative] = actual

    contents = {}
    for source_directory, output_directory in (
        (inputs / variant / "src", "androidApp/src/generated/variant"),
        (inputs / variant / "test", "androidApp/test/generated/variant"),
        (inputs / variant / "res", "androidApp/res"),
    ):
        for source in sorted(source_directory.rglob("*")):
            safe_path(source.relative_to(ROOT))
            if not source.is_file():
                continue
            relative = source.relative_to(source_directory)
            if output_directory.endswith("/res") and relative.parts[0].startswith("values"):
                relative = relative.with_name("kmpnews_catalog_" + relative.name)
            output_relative = str(Path(output_directory) / relative)
            output = owned_output(output_relative)
            if output.exists() and output_relative not in previous:
                raise ValueError("Refusing to replace a handwritten Android file: " + str(output))
            contents[output_relative] = source.read_bytes()

    ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
    manifest = ET.parse(safe_path("androidApp/configuration/main/AndroidManifest.xml"))
    overlay = safe_path(Path("androidApp/configuration") / variant / "AndroidManifest.xml")
    if overlay.exists():
        manifest.getroot().find("application").extend(ET.parse(overlay).getroot().find("application"))
    manifest_relative = "androidApp/src/AndroidManifest.xml"
    if owned_output(manifest_relative).exists() and manifest_relative not in previous:
        raise ValueError("Refusing to replace an unowned Android manifest")
    contents[manifest_relative] = (
        '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(manifest.getroot(), encoding="unicode") + "\n"
    ).encode()
    generated = {relative: hashlib.sha256(data).hexdigest() for relative, data in contents.items()}

    # Journal old/new hashes before mutation. An interrupted write can resume;
    # an unrelated edit still fails closed instead of being overwritten.
    pending = dict(previous)
    for relative, digest in generated.items():
        old = [observed[relative]] if relative in observed else []
        pending[relative] = list(dict.fromkeys([*old, digest]))
    write_if_changed(state, json.dumps(pending, indent=4) + "\n")
    for relative in previous.keys() - contents.keys():
        owned_output(relative).unlink(missing_ok=True)
    for relative, data in contents.items():
        output = owned_output(relative)
        if output.exists() and output.read_bytes() == data:
            continue
        output.parent.mkdir(parents=True, exist_ok=True)
        temporary = safe_path(str(output.relative_to(ROOT)) + ".tmp")
        temporary.write_bytes(data)
        temporary.replace(output)
    write_if_changed(state, json.dumps(generated, indent=4) + "\n")


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
        result = subprocess.call(command + arguments, cwd=ROOT, env=environment)
        modules = option_values(arguments, "-m", "--module")
        platforms = option_values(arguments, "-p", "--platform")
        desktop_package = "package" in arguments and (not modules or "desktopApp" in modules)
        if result == 0 and desktop_package and (not platforms or "jvm" in platforms):
            return subprocess.call(command + ["do", "packageDesktop", "-m", "desktopApp"],
                                   cwd=ROOT, env=environment)
        return result


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError) as error:
        print("Toolchain preparation failed: " + str(error), file=sys.stderr)
        sys.exit(1)
