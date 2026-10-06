# Registration, profile and error feedback audit

Date: 2026-10-06. Scope: all pending tracked and untracked feature changes,
including native production code, presentation/UI tests, shared Kotlin contracts
and implementations, localization, and avatar policy fixtures.

## Review stages

1. Three independent reviewers examined Swift production code, Kotlin/SQL, and
   tests/resources against the repository and module instructions.
2. Targeted fixes addressed semantic spacing, multi-step callbacks, declaration
   ordering, test-double names, reused registration input, API documentation and
   an unexplained test fixture force unwrap. Configured formatters handled layout.
3. Reviewers cross-checked the resulting code independently. A second pass caught
   remaining test/helper ordering, phase spacing and lifecycle documentation gaps.
4. Final verification checks the corrected code and its regression scenarios.

The audit also fixed two catalog defects: network failures now use error styling,
and OTP focus restoration distinguishes invalid codes from temporary failures and
failed resends. The existing single Save control now retains an accessible loading
label. Its visual identity and sizing behavior are unchanged.

## Verification

- Eight native presentation executables passed after the audit changes: Auth,
  ProfileEditing, ErrorFeedback, Feed, Search, FavoriteSave, FavoritesList and
  ComponentCatalog. Catalog tests use a controlled request gate for the added
  verification/resend regression.
- Scoped Kotlin Spotless apply/check passed. Shared JVM and Android host tests,
  shared UI JVM tests, and Android/Desktop Kotlin compilation passed.
- Post-audit iOS build and two iPhone iOS 27 UI tests passed: catalog forms and
  navigation, and dark accessibility-size profile editing with a save failure.
  Result: `/tmp/kmp-audit-iphone.xcresult`. Exported OTP/editor screenshots were
  inspected. Avatar memory-cache regression also passed after the fixture change.
- All 45 changed Swift files passed scoped formatting, strict lint and formatter
  idempotence. Localization JSON parsing and `git diff --check` passed.
- Prior feature acceptance included nine UI tests on iPhone iOS 27 and iPadOS
  26.5, covering both OTP flows, large text, catalog, failed removal and profile
  errors. These are prior-run evidence, separate from post-audit checks.

## Limits

- This is an audit of pending changes, not a whole-repository compliance claim.
  Existing Gradle/compiler deprecation warnings and untranslated older Auth error
  keys remain outside this change.
- Some existing presentation fixtures use short settling delays; they have not
  been redesigned as part of style cleanup.
- The photo fixture proves dimensions, size and GPS metadata removal. Its uniform
  image does not prove crop position or orientation.
- SQL policy tests now include owner, foreign-user and guest deletion assertions;
  the new assertions have not been executed against hosted Storage in this audit.
- Physical haptics, live SMTP, real photo-library/Storage transfers and device
  signing are not established by simulator or fixture checks.
- The previously investigated transient Save-title/spinner overlap remains an
  acknowledged native-toolbar issue; its reverted workaround is not reintroduced.
- Local logs, screenshots and ignored `docs/` records are supporting local evidence,
  not files that reviewers can access from the published repository.
