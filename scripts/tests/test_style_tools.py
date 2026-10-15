"""Check real pinned rule engines on disposable sources, without rewriting project code."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class StyleToolsTest(unittest.TestCase):
    def setUp(self):
        required = ("KTLINT_CLASSPATH", "DETEKT_JAR", "SWIFTLINT_BIN", "STYLE_JAVA")
        if not all(os.environ.get(key) for key in required):
            self.skipTest("Run ./kotlin do verifyStyleTools -m style for pinned analyzer regression tests")
        temporary = tempfile.TemporaryDirectory(prefix="KMPNews lint samples ")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        for name in (".editorconfig", ".swift-format", ".swiftlint.yml", "detekt.yaml"):
            shutil.copyfile(ROOT / name, self.root / name)
        self.java = os.environ["STYLE_JAVA"]
        self.ktlint = [self.java, "-cp", os.environ["KTLINT_CLASSPATH"], "com.pinterest.ktlint.Main"]
        self.detekt = [self.java, "-jar", os.environ["DETEKT_JAR"], "--config", str(self.root / "detekt.yaml"),
                       "--analysis-mode", "light", "--fail-on-severity", "Warning"]
        self.swiftlint = [os.environ["SWIFTLINT_BIN"], "lint", "--strict", "--no-cache", "--quiet",
                          "--config", str(self.root / ".swiftlint.yml")]

    def run_tool(self, command):
        return subprocess.run(command, cwd=self.root, text=True, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, timeout=90)

    def source(self, name, text):
        path = self.root / name
        path.write_text(text)
        return str(path)

    def test_ktlint_positive_platform_compose_and_portable_test_names(self):
        examples = {
            "Driver.ios.kt": "package sample\n\nactual fun platformName(): String = \"iOS\"\n",
            "Contract.kt": "package sample\n\nexpect fun platformName(): String\n",
            "Screen.kt": "package sample\n\n@Composable\nfun Screen() {\n    Content()\n}\n",
            "ContractTest.kt": "package sample\n\nfun readsUnicodeQueryWithoutChangingItsMeaning() = Unit\n",
            "verify.kts": "println(\"verification\")\n",
        }
        files = [self.source(name, text) for name, text in examples.items()]

        result = self.run_tool(self.ktlint + files)

        self.assertEqual(result.returncode, 0, result.stdout)

    def test_ktlint_rejects_import_naming_layout_and_syntax_errors(self):
        for text, rule in [
            ("package sample\n\nimport sample.models.*\n\nfun value(): Model = Model()\n", "no-wildcard-imports"),
            ("package sample\n\nimport java.util.*\n\nfun value(): Date = Date()\n", "no-wildcard-imports"),
            ("package sample\n\nfun BadName() = Unit\n", "function-naming"),
            ("package sample\n\nfun value()=1\n", "op-spacing"),
            ("package sample\n\nfun broken( {\n", ""),
        ]:
            with self.subTest(rule=rule):
                path = self.source("Sample.kt", text)

                result = self.run_tool(self.ktlint + [path])

                self.assertNotEqual(result.returncode, 0, result.stdout)
                if rule:
                    self.assertIn(rule, result.stdout)

    def test_kotlin_formatting_is_scoped_idempotent_and_read_only_checks_preserve_bytes(self):
        selected = Path(self.source("Selected.kt", "package sample\n\nfun value()=1\n"))
        untouched = Path(self.source("Untouched.kt", "package sample\n\nfun other()=2\n"))
        original = selected.read_bytes(), untouched.read_bytes()

        check = self.run_tool(self.ktlint + [str(selected)])

        self.assertNotEqual(check.returncode, 0)
        self.assertEqual(original, (selected.read_bytes(), untouched.read_bytes()))
        first = self.run_tool(self.ktlint + ["--format", str(selected)])
        formatted = selected.read_bytes()
        second = self.run_tool(self.ktlint + ["--format", str(selected)])

        self.assertEqual(first.returncode, 0, first.stdout)
        self.assertEqual(second.returncode, 0, second.stdout)
        self.assertEqual(formatted, selected.read_bytes())
        self.assertEqual(original[1], untouched.read_bytes())

    def test_ktlint_enforces_project_layout_settings(self):
        cases = [
            ("fun value() {\n  work()\n}\n", "indent"),
            ("fun value(\n    input: Int\n): Int = input\n", "trailing-comma-on-declaration-site"),
            ("val result = value(\n    1\n)\n", "trailing-comma-on-call-site"),
            ("fun value() = 1", "final-newline"),
            ("val " + "a" * 125 + " = 1\n", "max-line-length"),
        ]
        for text, rule in cases:
            with self.subTest(rule=rule):
                path = self.source("Sample.kt", "package sample\n\n" + text)

                result = self.run_tool(self.ktlint + [path])

                self.assertNotEqual(result.returncode, 0, result.stdout)
                self.assertIn(rule, result.stdout)

    def test_editorconfig_disables_generated_files_for_ide_engine(self):
        directory = self.root / "sharedLogic/generated/nested"
        directory.mkdir(parents=True)
        path = directory / "Generated.kt"
        path.write_text("package generated\n\nfun BadName()=1\n")

        result = self.run_tool(self.ktlint + [str(path)])

        self.assertEqual(result.returncode, 0, result.stdout)

    def test_every_selected_detekt_rule_detects_a_real_violation(self):
        cases = [
            ("fun sample() { try { work() } catch (failure: Exception) {} }", "EmptyCatchBlock"),
            ("fun sample() { try { work() } catch (failure: Exception) { throw IllegalStateException(\"failed\") } }",
             "SwallowedException"),
            ("fun sample() { for (item in items) { break } }", "UnconditionalJumpStatementInLoop"),
        ]
        for text, rule in cases:
            with self.subTest(rule=rule):
                path = self.source("Sample.kt", text)

                result = self.run_tool(self.detekt + ["--input", path])

                self.assertNotEqual(result.returncode, 0, result.stdout)
                self.assertIn(rule, result.stdout)

    def test_detekt_accepts_preserved_errors_and_rejects_syntax_errors(self):
        path = self.source("Sample.kt", "fun sample() { try { work() } catch (failure: Exception) { throw failure } }")

        result = self.run_tool(self.detekt + ["--input", path])

        self.assertEqual(result.returncode, 0, result.stdout)
        Path(path).write_text("fun broken( {")
        # Detekt light mode tolerates malformed PSI. The Toolchain gate runs ktlint first.
        result = self.run_tool(self.ktlint + [path])
        self.assertNotEqual(result.returncode, 0, result.stdout)

    def test_swiftlint_checks_every_selected_rule(self):
        cases = [
            ("let value = optional!\n", "force_unwrapping"),
            ("let value = try! work()\n", "force_try"),
            ("let value = object as! String\n", "force_cast"),
            ("var value: String!\n", "implicitly_unwrapped_optional"),
            ("if ready {} else if ready {}\n", "duplicate_conditions"),
            ("enum Value { case first; case first }\n", "duplicate_enum_cases"),
            ('let value = ["key": 1, "key": 2]\n', "duplicated_key_in_dictionary_literal"),
            ("// swiftlint:disable force_try\nlet value = 1\n", "blanket_disable_command"),
        ]
        for text, rule in cases:
            with self.subTest(rule=rule):
                path = self.source("Sample.swift", text)

                result = self.run_tool(self.swiftlint + [path])

                self.assertNotEqual(result.returncode, 0, result.stdout)
                self.assertIn(rule, result.stdout)

    def test_swift_formatting_is_scoped_idempotent_and_parses_swiftui(self):
        path = self.source("Sample.swift", "import SwiftUI\n\nstruct Sample: View {\nvar body: some View { Text(\"Hello\") }\n}\n")
        other = Path(self.source("Untouched.swift", "let other=2\n"))
        original = other.read_bytes()
        command = ["xcrun", "swift-format", "format", "--in-place", "--configuration", str(self.root / ".swift-format"), path]

        first = self.run_tool(command)
        formatted = Path(path).read_bytes()
        second = self.run_tool(command)

        self.assertEqual(first.returncode, 0, first.stdout)
        self.assertEqual(second.returncode, 0, second.stdout)
        self.assertEqual(formatted, Path(path).read_bytes())
        self.assertEqual(original, other.read_bytes())
        lint = self.run_tool(self.swiftlint + [path])
        self.assertEqual(lint.returncode, 0, lint.stdout)
        Path(path).write_text("func broken( {\n")
        lint = self.run_tool(["xcrun", "swift-format", "lint", "--strict", path])
        self.assertNotEqual(lint.returncode, 0, lint.stdout)


if __name__ == "__main__":
    unittest.main()
