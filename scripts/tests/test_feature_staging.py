"""Verify isolated source staging and refusal of out-of-checkout verification inputs."""

import importlib.util
import json
import plistlib
from pathlib import Path
import tempfile
import unittest

specification = importlib.util.spec_from_file_location(
    "feature_staging", Path(__file__).resolve().parents[1] / "verify-ios-features.py")
staging = importlib.util.module_from_spec(specification)
specification.loader.exec_module(staging)


class FeatureStagingTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="KMPNews staging ")
        self.addCleanup(self.temporary.cleanup)
        self.original_root = staging.ROOT
        self.addCleanup(setattr, staging, "ROOT", self.original_root)
        self.root = Path(self.temporary.name).resolve() / "checkout"
        self.root.mkdir()
        staging.ROOT = self.root
        for name in ("project.yaml", "libs.versions.toml", "kotlin", "kotlin.bat", ".swift-format", ".editorconfig"):
            self.write(name, name)
        for module in staging.MODULES:
            self.write(module + "/module.yaml", "product: test")
        self.write("iosApp/module.xcodeproj/project.pbxproj", "project")
        self.write("iosApp/module.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme", "scheme")
        self.write("scripts/prepare-toolchain.py", "prepare")
        self.write("scripts/package-desktop.py", "packager")
        self.destination = Path(self.temporary.name).resolve() / "staged"
        self.destination.mkdir()

    def write(self, relative, content):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        return path

    def test_generated_owned_resources_and_local_settings_are_not_copied(self):
        self.write("androidApp/res/values/product.xml", "handwritten")
        self.write("androidApp/res/values/kmpnews_catalog.xml", "generated")
        self.write("androidApp/src/generated/variant/Catalog.kt", "generated")
        self.write("androidApp/src/Feature.kt", "production")
        self.write("androidApp/src/AGENTS.md", "local rules")
        self.write(".idea/local.xml", "local settings")
        self.write(".toolchain/generated/android-files.json", json.dumps({
            "androidApp/res/values/kmpnews_catalog.xml": "hash"}))

        staging.stage_project(self.destination)

        self.assertEqual("production", (self.destination / "androidApp/src/Feature.kt").read_text())
        self.assertEqual("handwritten", (self.destination / "androidApp/res/values/product.xml").read_text())
        self.assertFalse((self.destination / "androidApp/res/values/kmpnews_catalog.xml").exists())
        self.assertFalse((self.destination / "androidApp/src/generated").exists())
        self.assertFalse((self.destination / "androidApp/src/AGENTS.md").exists())
        self.assertFalse((self.destination / ".idea").exists())

    def test_nested_fixture_symlink_is_rejected(self):
        outside = self.destination / "outside.kt"
        outside.write_text("outside checkout")
        fixture = self.write("sharedLogic/verification/android/Owner.kt", "fixture")
        fixture.unlink()
        fixture.symlink_to(outside)

        with self.assertRaises(ValueError):
            staging.validate_input(fixture.parent)

    def test_parent_symlink_and_external_source_are_rejected(self):
        (self.root / "iosApp/presentationTests").symlink_to(self.destination, target_is_directory=True)

        with self.assertRaises(ValueError):
            staging.validate_input(self.root / "iosApp/presentationTests/Test.swift")
        with self.assertRaises(ValueError):
            staging.validate_input(self.destination / "outside.swift")
        with self.assertRaises(ValueError):
            staging.validate_input(self.root / "../outside.swift")

    def test_public_server_configuration_is_removed_only_from_the_disposable_copy(self):
        document = {"SupabaseURL": "https://fixture.supabase.co", "SupabasePublishableKey": "public-fixture"}
        source = self.write("iosApp/src/Info.plist", plistlib.dumps(document).decode())
        android = self.write("androidApp/res/values/client_configuration.xml", "configured input")

        staging.stage_project(self.destination)

        staged = plistlib.loads((self.destination / "iosApp/src/Info.plist").read_bytes())
        self.assertNotIn("SupabaseURL", staged)
        self.assertNotIn("SupabasePublishableKey", staged)
        self.assertEqual(document, plistlib.loads(source.read_bytes()))
        self.assertEqual("configured input", android.read_text())

    def test_symlinked_ownership_manifest_is_rejected_before_staging(self):
        source = self.write(".toolchain/generated/android-files.json", "{}")
        source.unlink()
        outside = self.destination / "ownership.json"
        outside.write_text("{}")
        source.symlink_to(outside)

        with self.assertRaises(ValueError):
            staging.stage_project(self.destination)
        self.assertFalse((self.destination / "project.yaml").exists())

    def test_ui_success_requires_executed_tests(self):
        staging.verify_ui_summary({"result": "Passed", "passedTests": 7, "failedTests": 0})

        for document in (
            {"result": "Passed", "passedTests": 0, "skippedTests": 2},
            {"result": "Failed", "passedTests": 7, "failedTests": 1},
            {},
        ):
            with self.subTest(document=document), self.assertRaises(ValueError):
                staging.verify_ui_summary(document)


if __name__ == "__main__":
    unittest.main()
