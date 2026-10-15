#!/usr/bin/env python3
"""Retain Toolchain's executable launcher and preserve every resolved runtime entry."""

import io
import json
import os
from pathlib import Path
import sys
import tempfile
import zipfile

LIBRARIES = "BOOT-INF/lib/"
INDEX = "BOOT-INF/classpath.idx"
EPOCH = (1980, 1, 1, 0, 0, 0)


def archive_entry(name, compression=zipfile.ZIP_STORED):
    entry = zipfile.ZipInfo(name, EPOCH)
    entry.compress_type = compression
    entry.external_attr = 0o100644 << 16
    return entry


def runtime_bytes(path):
    """Keep dependency JAR bytes intact; turn compilation/resource directories into nested JARs."""
    if path.is_file():
        with zipfile.ZipFile(path) as source:
            if source.testzip() is not None:
                raise ValueError("Corrupt runtime JAR: " + str(path))
        return path.read_bytes()
    if not path.is_dir():
        raise ValueError("Missing runtime entry: " + str(path))

    data = io.BytesIO()
    with zipfile.ZipFile(data, "w") as target:
        for child in sorted(path.rglob("*")):
            if child.is_symlink():
                raise ValueError("Symlink in runtime directory: " + str(child))
            if child.is_file():
                name = child.relative_to(path).as_posix()
                target.writestr(archive_entry(name, zipfile.ZIP_DEFLATED), child.read_bytes())
    return data.getvalue()


def repackage(template, classpath, output):
    """Atomically produce a portable nested-JAR executable, retaining order and duplicate basenames."""
    if not classpath:
        raise ValueError("The resolved runtime classpath must not be empty")
    if output.resolve() == template.resolve() or any(output.resolve() == p.resolve() for p in classpath):
        raise ValueError("The output must not replace an input")
    for parent in (output, *output.parents):
        if parent.is_symlink():
            raise ValueError("Symlink in output path: " + str(parent))

    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(dir=output.parent, prefix=".desktop-", suffix=".jar")
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        with zipfile.ZipFile(template) as source, zipfile.ZipFile(temporary, "w") as target:
            manifest = source.read("META-INF/MANIFEST.MF").decode("utf-8")
            if "Main-Class: org.springframework.boot.loader.launch.JarLauncher" not in manifest:
                raise ValueError("Unsupported Toolchain executable JAR launcher")
            if "Spring-Boot-Classpath-Index: " + INDEX not in manifest:
                raise ValueError("Unsupported Toolchain classpath index")
            if "org/springframework/boot/loader/launch/JarLauncher.class" not in source.namelist():
                raise ValueError("Missing executable JAR launcher")

            seen = set()
            for entry in source.infolist():
                if entry.filename.startswith(LIBRARIES) or entry.filename == INDEX:
                    continue
                if entry.filename in seen:
                    raise ValueError("Duplicate template entry: " + entry.filename)
                seen.add(entry.filename)
                target.writestr(entry, source.read(entry))

            names = []
            for index, path in enumerate(classpath):
                basename = path.name if path.is_file() else path.name + ".jar"
                name = LIBRARIES + f"{index:04d}-" + basename
                target.writestr(archive_entry(name), runtime_bytes(path))
                names.append(name)
            target.writestr(archive_entry(INDEX), "".join("- " + json.dumps(n) + "\n" for n in names))
        temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)
    print("Desktop executable JAR:", output, f"({len(classpath)} runtime entries)")


if __name__ == "__main__":
    try:
        repackage(Path(sys.argv[1]), [Path(p) for p in sys.stdin.read().split("\0") if p], Path(sys.argv[2]))
    except (OSError, ValueError, zipfile.BadZipFile, KeyError) as error:
        print("Desktop packaging failed: " + str(error), file=sys.stderr)
        sys.exit(1)
