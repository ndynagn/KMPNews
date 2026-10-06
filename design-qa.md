# Profile design QA — 2026-10-05

## Visual target and evidence

Source: the third displayed Product Design result, explicitly selected by the user:
`/Users/ndynagn/.codex/generated_images/01a10b4d-0d53-7a82-96fe-e1d98aeda7f2/exec-6c9d7935-ad5e-413f-8b35-ca07c3739270.png`.
The user's amendment keeps exactly one edit action. It is placed in the trailing
navigation toolbar; the duplicate under the avatar is omitted.

Local implementation screenshots are in `docs/evidence/profile-details/`.
This evidence directory is ignored in this checkout and is not included in the
merge request; the filenames below describe the local validation record:

- `iphone-light.png`: populated profile, iPhone 15 Pro, 393x852 points,
  approximately 1179x2556 pixels at 3x (export reports 1178 pixels wide).
- `iphone-dark-large-text.png`: long name and accessibility XXXL text.
- `iphone-editor-keyboard.png`, `iphone-logout.png`: editing and confirmation.
- `ipad-light.png`: 834x1210 points, 1668x2420 pixels at 2x.
- `ipad-dark-large-text.png`, `ipad-save-error.png`: dark theme, large text,
  long values and a retained editor draft after a failed save.

The 853x1844 source normalizes to approximately 393x850 points. Source and final
iPhone screenshot were opened together in one comparison input. The source omits
real status/navigation insets; the implementation retains the existing native
navigation and tab bars, including the existing Search action. Native chrome is
not reconstructed from the image. These constraints explain vertical offsets and
the need to scroll to the logout row on a narrow/short device.

Both compared screens show an authenticated, populated account. The reference has
a sample portrait; the implementation fixture intentionally has no photo and uses
the required initials state. The portrait is not shipped as a user's image. The
fixture's email differs from the mock. This comparison does not validate a real
remote image transfer or SMTP delivery.

## Findings and iterations

1. Initial capture: P2 untranslated contact/middle-name labels, and an icon-only
   edit action where explicit text was preferable. Added RU/EN strings and forced
   the single toolbar action to display its localized title.
2. Post-fix captures: labels and the blue Edit title are visible and readable.
   The first theme test used an OS launch preference that did not reliably change
   appearance. A DEBUG-only, explicit fixture override now selects light/dark;
   the final iPhone and iPad dark captures confirm actual rendered colors.
3. Final comparison: no actionable P0/P1/P2 findings for the approved native scope.
   iPhone and iPad interaction tests passed for cancel/discard, save, logout cancel
   and confirmation. The failure scenario preserves input and visibly exposes the
   error and Save action, including at accessibility XXXL.

## Required fidelity surfaces

- Typography: native system title/body hierarchy, bold centered identity, readable
  secondary values. Long names wrap naturally; at accessibility sizes the screen
  scrolls instead of truncating the identity. Native section headings use current
  system capitalization/weight rather than forcing the raster mock's uppercase.
- Layout: centered circular 112-point avatar, grouped personal/contact cards,
  separate destructive row. iPad content is capped at 680 points. Rows switch to
  stacked labels and values when horizontal space is insufficient. Edit remains
  in the navigation toolbar and is not duplicated in the list.
- Colors: semantic grouped background, primary/secondary text, blue edit/initials
  and destructive red. Actual light and dark captures were inspected.
- Images: no-photo initials are sharp and circular; a missing name uses the system
  person icon. Remote avatars use Nuke with ephemeral networking and a bounded
  memory-only image cache isolated by account and login generation; there is no
  disk cache. Real avatar upload/display remains a separate service acceptance check.
- Copy: names, optional patronymic, read-only email and native localized actions;
  no new stats, subscriptions or unrelated features.

Focused region crops were unnecessary: toolbar labels, profile rows, editor
keyboard, confirmation and error text were directly legible in the opened full
resolution captures. The editor and alert were inspected separately as focused
interaction states.

## Implementation checklist

- [x] Selected third reference, preserving native system components.
- [x] Single top-right Edit action, no duplicate below the avatar.
- [x] Profile view, separate editor, draft/error handling and confirmation.
- [x] iPhone/iPad, light/dark, keyboard, long text and Dynamic Type evidence.
- [x] Corrected initial visual findings and inspected post-fix captures.

P3: very large accessibility text requires more scrolling, as expected; there is
no artificial type-size cap. Live service and photo-library acceptance gaps are
recorded locally in the ignored `docs/profile-details-verification.md`, not treated
as visual passes or evidence available from the merge request alone.

final result: passed
