# JVM Desktop client

Read [Kotlin style](../docs/standards/kotlin-style.md),
[architecture](../docs/standards/architecture.md) and
[verification](../docs/standards/verification.md).

- New presentation is client-local `feature/<name>/presentation`. Do not add new
  screens to `sharedUI`; its existing starter is a temporary dependency.
- Use a native JVM ViewModel/state holder owned by the window or navigation scope.
  Do not assume Android lifecycle services or introduce an Android dependency.
- Keep state updates on the appropriate UI dispatcher. Give coroutine scopes and
  subscriptions explicit disposal and cancel them when their owner closes.
  Closing one window must not destroy app-scoped services needed by others.
- Recomposition is not a load trigger. Define repeatability, keys and cancellation
  for effects. Components accept state/callbacks rather than whole ViewModels.
- Use the Compose naming/layout rules, platform-local resources and theme tokens.
  Shared UI-kit components remain within this client and avoid domain ownership.
- Check resize/minimum size, scrolling, focus order, keyboard shortcuts and window
  close/reopen for relevant changes. Keep menu actions and shortcuts consistent.
- Native macOS and JVM Desktop are different clients. A passing macOS build or
  DMG packaging does not verify this application's behavior on Windows or Linux.
