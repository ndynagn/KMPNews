# Shared Compose presentation

Scope: shared Android/JVM Desktop presentation, including its source sets.
Read [architecture](../docs/standards/architecture.md),
[accepted stack](../docs/standards/technology-stack.md),
[Kotlin style](../docs/standards/kotlin-style.md) and
[verification](../docs/standards/verification.md).

- Put new shared screens, ViewModels and MVVM/MVI contracts in
  `feature/<name>/presentation`; keep reusable components at the nearest shared
  scope. Read the shared business contract explicitly across module/source-set paths.
- Use Navigation3 for Compose navigation and Coil 3 for images after dependency
  verification. Hosts own platform entry points, permissions, menus and windows.
- Split lifecycle-aware routes from state-and-callback screens. Components accept
  only needed values/callbacks, never a whole ViewModel or a repository.
- Bind ViewModels to a screen/navigation entry or window, never an application
  singleton. Read the host's instructions for lifecycle ownership and disposal.
- Initial loading must not run during recomposition. Specify effect keys, repeat
  policy and cancellation. Keep local visual state local where appropriate.
- Use shared Compose resources/theme tokens for shared UI; keep platform-only
  resources in their host. Preserve caller Modifiers, meaningful accessibility
  semantics, stable list keys and supported text scaling.
- Keep Android APIs out of commonMain. Inject platform operations at real
  boundaries; do not branch business policy on the current UI platform.
- Changes affect both Compose clients: compile Android/Desktop and test relevant
  state transitions, rendering and lifecycle scenarios on each affected host.
