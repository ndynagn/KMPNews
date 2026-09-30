"""Seed/restore only an empty simulator cache for the opt-in cached-card UI test."""
import argparse
from pathlib import Path
import sqlite3
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("action", choices=["seed", "restore"])
parser.add_argument("--device", required=True)
parser.add_argument("--backup", type=Path, required=True)
args = parser.parse_args()
bundle = "com.ndynagn.kmp.news.KMPNews"
container = Path(subprocess.check_output(
    ["xcrun", "simctl", "get_app_container", args.device, bundle, "data"], text=True
).strip())
if "CoreSimulator/Devices" not in str(container) or not container.is_dir():
    raise SystemExit("Expected an installed simulator application")
subprocess.run(["xcrun", "simctl", "terminate", args.device, bundle], capture_output=True)
database = container / "Library/Application Support/news-feed.db"
if not database.is_file():
    raise SystemExit("Launch the app once to create its Room database before seeding")
if args.action == "seed":
    if args.backup.exists():
        raise SystemExit("Backup already exists; restore it or choose another path")
    with sqlite3.connect(database) as connection:
        if connection.execute("SELECT count(*) FROM articles").fetchone()[0] != 0:
            raise SystemExit("Refusing to replace a nonempty cache")
        args.backup.parent.mkdir(parents=True, exist_ok=True)
        with sqlite3.connect(args.backup) as backup:
            connection.backup(backup)
        for index in range(12):
            connection.execute("INSERT INTO articles VALUES (?,?,?,?,?,?,?,?,?,?)", (
                f"ui-fixture-{index}",
                None if index == 2 else f"Test news {index}: A headline that can expand to show the article summary",
                None,
                "Deterministic UI fixture. The description appears when the headline is expanded.",
                "https://example.invalid/image.png" if index == 0 else None,
                None, "Test source" if index != 2 else None,
                1790755200000 - index * 10000, index, 1,
            ))
        connection.execute("INSERT OR REPLACE INTO feed_metadata VALUES (1,?,NULL)", (1790755200000,))
else:
    if not args.backup.is_file():
        raise SystemExit("Backup is missing")
    with sqlite3.connect(database) as connection:
        unexpected = connection.execute(
            "SELECT count(*) FROM articles WHERE id NOT LIKE 'ui-fixture-%'"
        ).fetchone()[0]
        if unexpected:
            raise SystemExit("Cache contains non-fixture articles; restore manually after review")
        with sqlite3.connect(args.backup) as backup:
            backup.backup(connection)
print(f"Simulator fixture {args.action} completed; backup retained at {args.backup}")
