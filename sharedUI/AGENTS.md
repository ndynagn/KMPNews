# Transitional shared UI starter

Read [Kotlin style](../docs/standards/kotlin-style.md) and
[verification](../docs/standards/verification.md).

- This module exists only for the current Android/JVM starter. It is not the
  destination for new screens, ViewModels, feature state or UI-kit components.
- Preserve current behavior when fixing an existing defect. Document why an edit
  here is necessary; develop new presentation in the owning client instead.
- Removing the module or changing Android/Desktop dependency boundaries requires
  a separately approved migration. Do not silently perform it during style work.
- Changes affect both Kotlin UI clients; compile both and check the relevant
  scenarios. Follow Compose conventions while this temporary module remains.
