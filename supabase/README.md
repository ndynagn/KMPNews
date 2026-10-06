# News feed mediator

Project: `KMPNews`, ref `awupjlsmdnbhpfbmykrv`, London (`eu-west-2`).
The public client URL is `https://awupjlsmdnbhpfbmykrv.supabase.co`.
No authentication UI, server article cache, favorites or background polling is introduced.

Server-side account preparation is documented separately in the
[Auth contract](../docs/contracts/auth.md). Auth does not gate the public feed.

## HTTP contract

`GET /functions/v1/news-feed` accepts only an optional, non-empty `page` query
parameter. Duplicate or unknown parameters are rejected. The cursor is opaque.
Send the public project key in `apikey`, never as an Authorization bearer token.
`verify_jwt = false` is intentional: the handler validates the key against the
platform-injected `SUPABASE_PUBLISHABLE_KEYS` map. This identifies a public client,
not a trusted user, and does not prevent third parties from copying requests.

Only the function contacts `https://newsdata.io/api/1/latest`, using
`language=en`, `size=10`, `timezone=UTC`, and `X-ACCESS-KEY` from its secret.
It follows no redirects and retries neither RPC nor provider requests. RPC timeout
is 5 seconds; upstream timeout is 20 seconds including body reading.

Success: `{status:"success", totalResults:number, results:ArticleDto[], nextPage:string|null}`.
Article fields: `article_id` (required string), optional/nullable `title`, `link`,
`description`, `image_url`, `source_id`, `source_name`, `pubDate`, `pubDateTZ`.
Other provider fields are omitted. UTC date validation remains in the client mapper.
Responses use `Cache-Control: no-store`.

Errors: `{status:"error", results:{code:string}}`, with no raw provider messages.

| HTTP    | Code               | Client failure     |
|---------|--------------------|--------------------|
| 401/403 | AccessDenied       | ACCESS_DENIED      |
| 400/405 | InvalidRequest     | INVALID_REQUEST    |
| 429     | RateLimitExceeded  | RATE_LIMITED       |
| 429     | ApiLimitExceeded   | QUOTA_EXCEEDED     |
| 502     | InvalidResponse    | INVALID_RESPONSE   |
| 503     | ServiceUnavailable | SERVER_UNAVAILABLE |
| 504     | UpstreamTimeout    | TIMEOUT            |

Budget rejections include `Retry-After` in seconds. Provider errors are mapped
from their code/status; the proxy does not invent a retry time for them.
Missing server secrets and unavailable/malformed RPC responses fail closed with
503 before contacting NewsData. Failed updates preserve the client's Room snapshot.

## Budget and access

`public.reserve_news_request()` is SECURITY INVOKER and executable only by
`service_role`. It obtains transaction advisory lock 875001, then reads the wall
clock, removes attempts at least 24 hours old, checks both rolling windows, and
reserves an attempt atomically: **180 / 24 hours, 25 / 15 minutes**.
Failed, timed-out and cancelled attempts are not refunded. Requests denied by the
budget are not counted. When both limits apply, return the daily quota code and
the longer wait. This protects NewsData credits, not Supabase invocation counts
or equitable access among callers. Use a dedicated NewsData key; external calls
with the same key would bypass these counters.

The private table stores timestamps only. RLS is enabled without client policies
(intentionally deny-by-default), and client grants are revoked. The service role
has SELECT/INSERT/DELETE and bypasses RLS. There is no SECURITY DEFINER budget RPC.
The second migration revokes client EXECUTE on the Dashboard-created automatic-RLS
event trigger function; it does not disable that event trigger. The existence guard also
allows replay in local databases without that Dashboard helper.

## Configuration and deployment

Client public settings: root `feed.properties` for Android and
`iosApp/Configuration/Feed.xcconfig` for iOS. Keep the URL/key pair consistent.
Never put provider keys or privileged Supabase keys in either file, BuildConfig,
Info.plist, test fixtures or logs. No Kotlin Supabase SDK is needed; Ktor calls HTTP.

Set `NEWSDATA_API_KEY` through Dashboard > Edge Functions > Secrets. Do not paste it
in chat or command arguments. Supabase injects `SUPABASE_URL`,
`SUPABASE_SERVICE_ROLE_KEY` and `SUPABASE_PUBLISHABLE_KEYS`; missing values fail closed.
The service-role key stays inside the function and is used only for the budget RPC.

Apply tracked migrations before deploying `news-feed`. Keep public Data API enabled
for the service-only RPC; never expose `private` as a Data API schema. Use CLI command
help for the installed version. CLI migration filenames must match remote history;
the checked-in versions match the project's applied MCP migrations.

GitHub automatic branching and production deployment are not required. Do not enable
either incidentally. If automation is later selected, protect the deployment branch
and require tests before applying migrations/functions. First deployment here uses
MCP; subsequent CLI deployments can use `supabase functions deploy news-feed
--project-ref awupjlsmdnbhpfbmykrv` after verifying the current CLI help.

## Verification

From repository root (Deno 2.9.6; no external runtime dependencies):

```sh
deno task --config supabase/functions/news-feed/deno.json check
deno task --config supabase/functions/news-feed/deno.json test
deno fmt --check supabase/functions/news-feed
deno lint supabase/functions/news-feed
python3 supabase/tests/check_budget.py
```

The database test creates a disposable PostgreSQL 17.11 Docker container, no published
ports or volumes, and removes it in a finally block. It verifies rolling boundaries,
real role denial and 40 concurrent sessions accepting exactly 25 reservations.
`tests/budget.sql` rolls back all fixtures; use only in an isolated test database or
before live traffic, because it temporarily replaces counters inside its transaction.
Do not run destructive quota tests against a live project.

Live smoke: one first-page request and one request using its returned cursor; report
status/counts only. Verify missing key and unknown parameters without spending credits.
Run Supabase Security Advisors, shared tests, Swift interop and affected client builds.
Report current commands, results and limitations in chat/PR/MR, with logs in a
temporary directory outside the repository. See the [feed contract](../docs/contracts/news-feed.md).

## Operational limits

No proof of the account's actual purchased quota or content redistribution rights is
implied by successful HTTP requests. Confirm both before public distribution. If an
old provider key was shipped in a binary, replace/revoke it after the server uses a
new dedicated key; old distributed binaries cannot be made secret retroactively.
