"""Exercise the real limiter in disposable PostgreSQL, including concurrent sessions.

Requires Docker. No host ports or persistent volumes, and no provider requests.
"""
import json
import subprocess
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

root = Path(__file__).resolve().parents[1]
container = "kmpnews-budget-" + uuid.uuid4().hex[:10]


def sql(query):
    result = subprocess.run(
        ["docker", "exec", "-i", container, "psql", "-U", "postgres", "-At", "-v", "ON_ERROR_STOP=1"],
        input=query, text=True, capture_output=True, check=True,
    )
    return result.stdout.strip()


subprocess.run([
    "docker", "run", "--detach", "--rm", "--name", container,
    "-e", "POSTGRES_HOST_AUTH_METHOD=trust", "postgres:17.11",
], check=True, stdout=subprocess.DEVNULL)
try:
    for _ in range(60):
        ready = subprocess.run(["docker", "exec", container, "pg_isready", "-U", "postgres"], capture_output=True)
        if ready.returncode == 0:
            break
        time.sleep(0.5)
    sql("create role anon; create role authenticated; create role service_role bypassrls;")
    for migration in sorted((root / "migrations").glob("*.sql")):
        sql(migration.read_text())
    sql((root / "tests/budget.sql").read_text())
    with ThreadPoolExecutor(max_workers=30) as pool:
        results = list(
            pool.map(
                lambda _: json.loads(
                    sql("set role service_role; select public.reserve_news_request();").splitlines()[-1]
                ),
                range(40),
            )
        )
    accepted = sum(item["allowed"] for item in results)
    assert accepted == 25, results
    assert all(item["allowed"] or item["code"] == "RateLimitExceeded" for item in results), results
    assert sql("select count(*) from private.news_request_attempts") == "25"
    for role in ("anon", "authenticated"):
        for query in ("select public.reserve_news_request()", "select * from private.news_request_attempts"):
            try:
                sql(f"set role {role}; {query};")
            except subprocess.CalledProcessError:
                pass
            else:
                raise AssertionError(f"Unexpected access for {role}")
    print("PASS: rolling windows, role isolation, 40 concurrent calls / exactly 25 reservations")
finally:
    subprocess.run(["docker", "stop", container], check=True, stdout=subprocess.DEVNULL)
