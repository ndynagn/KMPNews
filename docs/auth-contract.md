# KMPNews Auth contract

Scope: server preparation for the existing Supabase project
`awupjlsmdnbhpfbmykrv`. Mobile screens, Kotlin SDK selection, secure session
storage, profiles and favorites are a separate integration stage. This is a pet
project; no paid plan, social login, anonymous account or MFA enrollment is added.

## Identity and configuration

Email is the login identifier. Registration uses email and password; the user
confirms ownership with an email code. Subsequent login uses email and password.
Email verification and password recovery are not a second authentication factor.
The built-in Supabase email provider also has passwordless capabilities; omitting
them from the future UI does not disable its public API. Do not claim password-plus-OTP MFA.

| Setting                                       | Target                                                                    |
|-----------------------------------------------|---------------------------------------------------------------------------|
| Registration / Email provider / Confirm email | Enabled                                                                   |
| Anonymous users / social providers            | Disabled                                                                  |
| Minimum password length / composition         | 8 characters / no additional composition requirements                     |
| Email OTP length / validity                   | 6 digits / 600 seconds                                                    |
| Minimum resend interval                       | 60 seconds per recipient                                                  |
| Auth email budget                             | 30 emails per hour per project                                            |
| Secure email change                           | Confirm at both old and new addresses                                     |
| SMTP                                          | Existing Gmail, port 587, sender name KMPNews; credentials Dashboard-only |

Other hosted limits are preserved: token refresh 150 requests per 5 minutes per
IP; OTP/link verification 30 per 5 minutes per IP; signup/login 30 per 5 minutes
per IP. Disabled-provider limits are unchanged: SMS 30/hour, anonymous users
30/hour per IP, Web3 30 requests per 5 minutes per IP. IP forwarding remains off.
Supabase rate limits are independent of the NewsData request budget.

## Client HTTP contract

Base: `https://awupjlsmdnbhpfbmykrv.supabase.co/auth/v1`.
Send `apikey: <publishable key>` and JSON content type. Use an access token in
`Authorization: Bearer <access_token>` for authenticated operations. Never send
service-role credentials. Passwords, OTPs, access/refresh tokens and request bodies
must not appear in logs. Examples below are shapes, not runnable credentials.

| Operation        | Request                                                     | Expected behavior                                                                                                   |
|------------------|-------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| Register         | `POST /signup` with `email`, `password`                     | Starts confirmation; do not assume a session or reveal whether the account already exists                           |
| Confirm signup   | `POST /verify` with `email`, `token`, `type: signup`        | Correct unexpired signup code confirms email and returns a session                                                  |
| Resend signup    | `POST /resend` with `email`, `type: signup`                 | Requests another confirmation code, subject to server limits                                                        |
| Login            | `POST /token?grant_type=password` with `email`, `password`  | Returns a session only after email confirmation                                                                     |
| Request recovery | `POST /recover` with `email`                                | Always show the same acknowledgement for known and unknown addresses                                                |
| Verify recovery  | `POST /verify` with `email`, `token`, `type: recovery`      | Returns a session used for password reset; it is a real authenticated session, not a password-reset-only capability |
| Set new password | `PUT /user` with `password`, authenticated                  | Requires a valid session; recovery UI proceeds directly to this step after code verification                        |
| Refresh session  | `POST /token?grant_type=refresh_token` with `refresh_token` | Replace the stored session with the returned tokens; serialize concurrent refreshes                                 |
| Current user     | `GET /user`, authenticated                                  | Returns the server-validated user                                                                                   |
| Sign out here    | `POST /logout?scope=local`, authenticated                   | Revoke this session's refresh capability and clear local tokens; do not sign out other devices                      |

The signup and recovery verification types must match their originating request;
do not use passwordless `POST /otp` as a substitute for signup confirmation.
OTP is a string: preserve leading zeroes. Do not trim or normalize passwords.
No redirect URL, deep link or embedded confirmation link is required for these
code-entry flows. Other unused templates and existing redirect configuration remain unchanged.

Expired or already-used codes must not create a session. A successful resend is
not proof of mailbox delivery. Let Supabase determine code validity rather than
maintaining client-side codes or a custom code table. The deferred live matrix
must also establish how a previous code behaves after resend; do not invent that guarantee.

## Sessions and failures

Supabase owns `auth.users`, password hashing, verification and refresh tokens.
No public user table or migration is needed at this stage. Preserve hosted JWT
and session settings: access-token expiry 3600 seconds, refresh replay detection
enabled with a 10-second reuse interval, single-session enforcement off, no session
timebox or inactivity timeout. Logout does not retroactively invalidate an already-issued
access JWT before its expiry; future protected resources must enforce their own
authorization and ownership policies.

Future clients should expose typed outcomes rather than raw provider messages:
invalid credentials (`invalid_credentials`), unconfirmed email
(`email_not_confirmed`), weak password (`weak_password`), invalid/expired OTP
(`otp_expired` and applicable validation errors), rate limited (HTTP 429),
unavailable service (5xx), and network/timeout failure. Refresh-token failure
requires login; a transient network failure alone must not erase a session.
Do not identify accounts through registration/recovery response variations.

The feed remains anonymous-accessible through its existing publishable-key check;
do not attach an Auth requirement or change `news-feed` JWT settings. Only
`service_role` may execute the NewsData budget RPC or access its private table.

## Local versus hosted application

`supabase/config.toml` and `supabase/templates/` describe local Auth. The local
stack retains its mail catcher; it must not use hosted Gmail credentials or send
real mail. Local defaults for unrelated session settings are not evidence of the
hosted configuration and must not overwrite it.

For the hosted project, use Authentication > Sign In / Providers > Email to
apply password/OTP settings, Rate Limits for the project email budget, and
Emails > Templates for the following exact subject/body pairs:

- Confirmation: `Confirm your KMPNews email`, `supabase/templates/confirmation.html`.
- Recovery: `Reset your KMPNews password`, `supabase/templates/recovery.html`.

Read values back after saving, and compare both template bodies with the checked-in
files. Editing the local config does not deploy hosted Auth settings. Preserve SMTP
secrets and session/JWT configuration. Replacing SMTP later does not migrate users.

## Verification and deferred acceptance

Record hosted readback, local configuration validation, template comparison,
secret-free diff review, and NewsData permission checks. No mobile build or UI
acceptance is implied by this server-only change.

Real email sending is explicitly deferred. Once the user provides a test address,
verify registration delivery, login denial before confirmation, correct/incorrect/
expired/replayed codes, leading-zero codes, resend behavior and throttling, login,
recovery delivery, new-password login, old-password rejection, session refresh and
local logout. Unknown-email recovery must retain the same user-facing acknowledgement.
Do not save real passwords, codes, tokens or message bodies in the evidence report.

Until that run, report: **Configuration prepared; SMTP delivery and complete Auth
flows not verified.** A saved SMTP password is not proof that Gmail accepts it.

### Preparation evidence (2026-09-30)

- Hosted Email settings saved and read back: OTP 6 digits / 600 seconds,
  password minimum 8, no composition requirements, secure email change enabled.
- Email budget already 30/hour; filling the same value left Save disabled.
  Other limits and session settings were inspected without saving changes.
- Both templates applied through Dashboard and reloaded; full editor contents
  matched the intended HTML, with the recovery preview checked for removal of the
  old link. No test emails or test accounts created.
- Public Auth settings confirm signup and Email enabled, confirmation required,
  anonymous users disabled. Public feed checked separately with publishable key only.
- SQL privilege check: anon/authenticated have no SELECT on the private attempts
  table and no EXECUTE on the reservation RPC; service_role retains EXECUTE.
- Security Advisors: no WARN/ERROR; the existing deny-by-default private budget
  table retains its intentional RLS-without-policies INFO.
- Local TOML parsing, template path/content checks and `git diff --check` passed.
  CLI `supabase status` reached container inspection but exited with
  `StatusDbInspectError`: `supabase_db_KMPNews` is not running. This is not a local
  Auth runtime pass. No mobile code changed or mobile tests claimed by this preparation.

References: [password Auth](https://supabase.com/docs/guides/auth/passwords),
[email templates](https://supabase.com/docs/guides/auth/auth-email-templates),
[Auth rate limits](https://supabase.com/docs/guides/auth/rate-limits),
[local config](https://supabase.com/docs/guides/local-development/cli/config).
