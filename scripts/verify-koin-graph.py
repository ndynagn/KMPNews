#!/usr/bin/env python3
"""Verify fresh compilation and repeated rejection of the unique mandatory Auth clock binding."""

import importlib.util
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main():
    specification = importlib.util.spec_from_file_location("feature_checks", ROOT / "scripts/verify-ios-features.py")
    checks = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(checks)
    evidence = Path(tempfile.mkdtemp(prefix="kmpnews-koin-")).resolve()
    checkout = evidence / "project"
    checkout.mkdir()
    checks.stage_project(checkout)
    environment = dict(os.environ)
    environment.pop("KMPNEWS_TOOLCHAIN_PREPARED", None)
    command = [str(checkout / "kotlin"), "build", "-m", "sharedLogic", "-p", "jvm"]
    print("Evidence:", evidence, flush=True)

    def compile_graph(name):
        with (evidence / name).open("w") as output:
            return subprocess.run(command, cwd=checkout, env=environment,
                                  stdout=output, stderr=subprocess.STDOUT).returncode

    if compile_graph("positive.log"):
        raise SystemExit("The unchanged graph did not compile")
    source = checkout / "sharedLogic/src/com/ndynagn/kmp/news/feature/auth/di/AuthDependencies.kt"
    text = source.read_text()
    # Favorites also registers AccountSessionAccess. Use a unique dependency because the
    # pinned compiler validates the compilation's registrations, not each isolated graph.
    binding = "single<AuthClock> { AuthClock { Clock.System.now().epochSeconds } }"
    if text.count(binding) != 1:
        raise SystemExit("Expected exactly one Auth clock binding; update this feature check with DI changes")
    source.write_text(text.replace(binding, ""))

    for iteration in range(2):
        name = f"negative-{iteration + 1}.log"
        result = compile_graph(name)
        diagnostic = (evidence / name).read_text()
        expected = "[Koin][KOIN-D001] Missing dependency: com.ndynagn.kmp.news.feature.auth.data.AuthClock"
        if not result or expected not in diagnostic:
            raise SystemExit("Missing Auth clock binding was not rejected: " + str(evidence / name))
    print("PASS: valid graphs compile; missing Auth clock binding fails on both fresh builds")


if __name__ == "__main__":
    main()
