# JVM Desktop client

Read [Kotlin style](../docs/standards/kotlin-style.md),
[architecture](../docs/standards/architecture.md) and
[verification](../docs/standards/verification.md).

- Shared presentation belongs in sharedUI; read its [rules](../sharedUI/AGENTS.md).
  This host owns windows, menus, keyboard integration and JVM-specific adapters;
  keep platform-only feature code in `feature/<name>`.
- Provide a window/navigation owner for shared Compose ViewModels and release
  its ViewModel store and owned scopes on close. Do not assume Android lifecycle
  services or introduce an Android dependency into common/JVM code.
- Keep state updates on the appropriate UI dispatcher. Give coroutine scopes and
  subscriptions explicit disposal and cancel them when their owner closes.
  Closing one window must not destroy app-scoped services needed by others.
- Recomposition is not a load trigger. Define repeatability, keys and cancellation
  for effects. Components accept state/callbacks rather than whole ViewModels.
- Use shared Compose resources/components for common screens and platform-local
  resources for host-specific controls. Apply the appropriate theme tokens.
- Check resize/minimum size, scrolling, focus order, keyboard shortcuts and window
  close/reopen for relevant changes. Keep menu actions and shortcuts consistent.
- Native macOS and JVM Desktop are different clients. A passing macOS build or
  DMG packaging does not verify this application's behavior on Windows or Linux.
