"""Verify that the component catalog cannot leak across Android build variants."""

import importlib.util
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path


spec = importlib.util.spec_from_file_location(
    "preparation", Path(__file__).resolve().parents[1] / "prepare-toolchain.py"
)
preparation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preparation)


class AndroidPreparationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="KMPNews checkout ")
        self.addCleanup(self.temporary.cleanup)
        self.original_root = preparation.ROOT
        self.addCleanup(setattr, preparation, "ROOT", self.original_root)
        preparation.ROOT = Path(self.temporary.name)
        self.root = preparation.ROOT
        self.write("androidApp/configuration/main/AndroidManifest.xml",
                   '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application/></manifest>')
        self.write("androidApp/configuration/debug/AndroidManifest.xml",
                   '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                   '<application><activity android:name=".Catalog" android:exported="false"/></application></manifest>')
        self.write("androidApp/configuration/debug/src/Catalog.kt", "class Catalog\n")
        self.write("androidApp/configuration/debug/res/values/strings.xml",
                   '<resources><string name="catalog_title">Catalog</string></resources>')
        self.write("androidApp/configuration/release/src/Action.kt", "fun action() = Unit\n")

    def write(self, relative, text):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def test_release_removes_catalog_sources_resources_and_manifest_entry(self):
        preparation.prepare_android("debug")
        self.assertTrue((self.root / "androidApp/src/generated/variant/Catalog.kt").exists())

        preparation.prepare_android("release")

        self.assertFalse((self.root / "androidApp/src/generated/variant/Catalog.kt").exists())
        self.assertFalse((self.root / "androidApp/res/values/kmpnews_catalog_strings.xml").exists())
        self.assertNotIn(".Catalog", (self.root / "androidApp/src/AndroidManifest.xml").read_text())
        self.assertTrue((self.root / "androidApp/src/generated/variant/Action.kt").exists())

    def test_repeated_debug_preparation_preserves_identical_content(self):
        preparation.prepare_android("debug")
        before = {str(path.relative_to(self.root)): path.read_bytes()
                  for path in self.root.rglob("*") if path.is_file()}

        preparation.prepare_android("debug")

        after = {str(path.relative_to(self.root)): path.read_bytes()
                 for path in self.root.rglob("*") if path.is_file()}
        self.assertEqual(before, after)

    def test_png_catalog_resource_is_staged_only_for_debug(self):
        source = self.root / "androidApp/configuration/debug/res/drawable/catalog_observatory.png"
        source.parent.mkdir(parents=True)
        source.write_bytes(b"\x89PNG\r\n\x1a\n")
        output = self.root / "androidApp/res/drawable/catalog_observatory.png"

        preparation.prepare_android("debug")

        self.assertEqual(source.read_bytes(), output.read_bytes())

        preparation.prepare_android("release")

        self.assertFalse(output.exists())
        self.assertTrue(source.exists())

    def test_edited_generated_source_is_preserved_and_rejected(self):
        preparation.prepare_android("debug")
        path = self.root / "androidApp/src/generated/variant/Catalog.kt"
        path.write_text("handwritten edit\n")

        with self.assertRaisesRegex(ValueError, "Edited generated Android output"):
            preparation.prepare_android("release")

        self.assertEqual("handwritten edit\n", path.read_text())

    def test_output_symlink_cannot_write_outside_checkout(self):
        outside = self.root / "outside"
        outside.mkdir()
        generated = self.root / "androidApp/src/generated"
        generated.parent.mkdir(parents=True, exist_ok=True)
        generated.symlink_to(outside, target_is_directory=True)

        with self.assertRaisesRegex(ValueError, "must not be symlinks"):
            preparation.prepare_android("debug")

        self.assertEqual([], list(outside.iterdir()))

    def test_release_packaging_and_task_names_select_release_inputs(self):
        self.assertEqual("release", preparation.profile(["package", "-m", "androidApp"])[1])
        self.assertEqual("release", preparation.profile(["task", ":androidApp:buildAndroidRelease"])[1])

    def test_crafted_ownership_cannot_delete_a_file_outside_checkout(self):
        import hashlib
        import json

        self.write("victim.txt", "preserve\n")
        digest = hashlib.sha256(b"preserve\n").hexdigest()
        self.write(".toolchain/generated/android-files.json", json.dumps({
            "androidApp/src/generated/../../../victim.txt": digest,
        }))

        with self.assertRaisesRegex(ValueError, "must stay inside the checkout"):
            preparation.prepare_android("debug")

        self.assertEqual("preserve\n", (self.root / "victim.txt").read_text())

    def test_manifest_edits_are_preserved_and_rejected(self):
        preparation.prepare_android("debug")
        path = self.root / "androidApp/src/AndroidManifest.xml"
        path.write_text("handwritten manifest edit\n")

        with self.assertRaisesRegex(ValueError, "Edited generated Android output"):
            preparation.prepare_android("release")

        self.assertEqual("handwritten manifest edit\n", path.read_text())

    def test_malformed_overlay_preserves_outputs_and_allows_retry_after_fix(self):
        preparation.prepare_android("release")
        self.write("androidApp/configuration/debug/AndroidManifest.xml", "<broken")
        before = (self.root / "androidApp/src/AndroidManifest.xml").read_bytes()

        with self.assertRaises(preparation.ET.ParseError):
            preparation.prepare_android("debug")

        self.assertEqual(before, (self.root / "androidApp/src/AndroidManifest.xml").read_bytes())
        self.write("androidApp/configuration/debug/AndroidManifest.xml", "<manifest><application/></manifest>")
        preparation.prepare_android("debug")
        self.assertTrue((self.root / "androidApp/src/generated/variant/Catalog.kt").exists())

    def test_interrupted_write_can_resume_from_ownership_journal(self):
        preparation.prepare_android("debug")
        original_replace = Path.replace

        def interrupt_manifest(path, target):
            if str(target).endswith("src/AndroidManifest.xml"):
                raise OSError("interrupted write")
            return original_replace(path, target)

        with patch.object(Path, "replace", interrupt_manifest):
            with self.assertRaisesRegex(OSError, "interrupted write"):
                preparation.prepare_android("release")

        preparation.prepare_android("debug")

        self.assertTrue((self.root / "androidApp/src/generated/variant/Catalog.kt").exists())
        self.assertFalse((self.root / "androidApp/src/generated/variant/Action.kt").exists())


if __name__ == "__main__":
    unittest.main()
