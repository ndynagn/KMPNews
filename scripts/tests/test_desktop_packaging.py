"""Exercise runtime collisions, classpath order, portable resources and atomic failure handling."""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile


def load_script(name, filename):
    specification = importlib.util.spec_from_file_location(name, Path(__file__).resolve().parents[1] / filename)
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


packaging = load_script("desktop_packaging", "package-desktop.py")
preparation = load_script("desktop_preparation", "prepare-toolchain.py")


class DesktopPackagingTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="KMPNews packaging ")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.template = self.root / "original.jar"
        self.output = self.root / "portable package" / "KMPNews.jar"
        manifest = ("Manifest-Version: 1.0\r\n"
                    "Main-Class: org.springframework.boot.loader.launch.JarLauncher\r\n"
                    "Spring-Boot-Classpath-Index: BOOT-INF/classpath.idx\r\n\r\n")
        self.jar(self.template, {
            "META-INF/MANIFEST.MF": manifest.encode(),
            "org/springframework/boot/loader/launch/JarLauncher.class": b"launcher",
            "BOOT-INF/classes/Main.class": b"main",
            "BOOT-INF/lib/runtime.jar": b"lost-library",
            "BOOT-INF/classpath.idx": b"stale index",
        })

    def jar(self, path, entries):
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, "w") as archive:
            for name, contents in entries.items():
                archive.writestr(name, contents)
        return path

    def test_same_basename_jars_keep_bytes_order_and_all_classes(self):
        first = self.jar(self.root / "jetbrains/runtime.jar", {"Adapter.class": b"adapter"})
        second = self.jar(self.root / "androidx/runtime.jar", {"Composer.class": b"composer"})
        original = self.template.read_bytes()

        packaging.repackage(self.template, [first, second], self.output)

        with zipfile.ZipFile(self.output) as archive:
            names = [json.loads(line[2:]) for line in archive.read(packaging.INDEX).decode().splitlines()]
            self.assertEqual(names, ["BOOT-INF/lib/0000-runtime.jar", "BOOT-INF/lib/0001-runtime.jar"])
            for name, source in zip(names, [first, second]):
                self.assertEqual(archive.getinfo(name).compress_type, zipfile.ZIP_STORED)
                self.assertEqual(archive.read(name), source.read_bytes())
            with zipfile.ZipFile(io.BytesIO(archive.read(names[1]))) as nested:
                self.assertEqual(nested.read("Composer.class"), b"composer")
            self.assertEqual(archive.read("BOOT-INF/classes/Main.class"), b"main")
            self.assertNotIn("BOOT-INF/lib/runtime.jar", archive.namelist())
        self.assertEqual(self.template.read_bytes(), original)

    def test_directory_resources_survive_and_repeated_output_is_identical(self):
        resources = self.root / "resource files"
        resources.mkdir()
        (resources / "label.txt").write_text("Привет")

        packaging.repackage(self.template, [resources], self.output)
        original = self.output.read_bytes()
        packaging.repackage(self.template, [resources], self.output)

        self.assertEqual(self.output.read_bytes(), original)
        with zipfile.ZipFile(self.output) as archive:
            with zipfile.ZipFile(io.BytesIO(archive.read("BOOT-INF/lib/0000-resource files.jar"))) as nested:
                self.assertEqual(nested.read("label.txt").decode(), "Привет")

    def test_missing_or_corrupt_runtime_preserves_previous_package(self):
        self.output.parent.mkdir()
        self.output.write_bytes(b"previous package")
        corrupt = self.root / "corrupt.jar"
        corrupt.write_bytes(b"not a zip")

        for entry in [self.root / "missing.jar", corrupt]:
            with self.subTest(entry=entry), self.assertRaises((ValueError, zipfile.BadZipFile)):
                packaging.repackage(self.template, [entry], self.output)

        self.assertEqual(self.output.read_bytes(), b"previous package")
        self.assertEqual(list(self.output.parent.glob(".desktop-*")), [])

    def test_empty_classpath_or_input_overwrite_is_rejected(self):
        for entries, output in [([], self.output), ([self.template], self.template)]:
            with self.subTest(output=output), self.assertRaises(ValueError):
                packaging.repackage(self.template, entries, output)
        self.assertFalse(self.output.exists())

    def test_output_symlink_is_rejected_without_changing_target(self):
        destination = self.root / "outside"
        destination.mkdir()
        self.output.parent.symlink_to(destination, target_is_directory=True)

        with self.assertRaises(ValueError):
            packaging.repackage(self.template, [self.template], self.output)

        self.assertEqual(list(destination.iterdir()), [])


class DesktopPackageDispatchTest(unittest.TestCase):
    def run_cli(self, arguments, results):
        with patch.object(preparation, "prepare"), \
                patch.object(preparation, "checkout_lock", contextlib.nullcontext), \
                patch.object(preparation.sys, "argv", ["prepare-toolchain.py", *arguments]), \
                patch.object(preparation.subprocess, "call", side_effect=results) as calls:
            result = preparation.main()
        return result, [call.args[0] for call in calls.call_args_list]

    def test_successful_desktop_package_runs_fix_and_propagates_failure(self):
        result, calls = self.run_cli(["package", "-m", "desktopApp", "-p", "jvm"], [0, 7])

        self.assertEqual(result, 7)
        self.assertEqual(calls[1][-4:], ["do", "packageDesktop", "-m", "desktopApp"])

    def test_failed_or_unrelated_commands_never_run_desktop_packaging(self):
        for arguments, status in [(["package", "-m", "desktopApp"], 3),
                                  (["package", "-m", "androidApp"], 0),
                                  (["build", "-m", "desktopApp", "-p", "jvm"], 0)]:
            with self.subTest(arguments=arguments):
                result, calls = self.run_cli(arguments, [status])

                self.assertEqual(result, status)
                self.assertEqual(len(calls), 1)


if __name__ == "__main__":
    unittest.main()
