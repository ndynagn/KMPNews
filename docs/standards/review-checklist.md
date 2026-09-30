# Code standards review checklist

Apply the relevant items to new and modified code. Mark irrelevant items as not
applicable; do not require ceremonial comments or scaffolding to satisfy this list.
This is a review aid, not an automated gate or an assertion about the whole repo.

- Read root, owning module and applicable descendant instructions, including
  cross-source-set feature contracts. Check [architecture](architecture.md).
- Check roles and dependency direction: native/shared presentation ownership,
  domain contracts, data-only DTOs/entities/mapping, composition-root injection.
- Check names at their use sites: purpose, operation verb, implementation role,
  platform acronym style and meaningful collaborator names. See
  [Kotlin](kotlin-style.md) and [Swift](swift-style.md).
- Check file responsibility, principal operations and adjacent related methods/
  overloads. Screen and ViewModel remain separate; avoid unrelated extensions.
- Check required [API documentation](code-documentation.md): behavior, nullable
  semantics, units, failures, cancellation and resource/actor ownership as relevant.
  Do not duplicate interface contracts or add comments repeating obvious syntax.
- Check resource ownership, localization keys, assets and accessibility semantics.
  Automation identifiers are stable, non-sensitive and separate from spoken labels.
- Check [tests and fixtures](verification.md#test-and-fixture-conventions): readable
  scenario names, Arrange–Act–Assert, visible essential inputs/expectations,
  appropriate doubles and behavior-focused assertions.
- Check TODO/FIXME context and completion conditions. Explain narrow exceptions;
  record architecture changes in contracts/standards, not just local comments.
- Run the configured targeted formatting checks and relevant behavioral checks.
  Report commands, failures and unavailable environments. Formatting cannot prove
  architectural correctness or documentation completeness.
- Keep documentation consistent with the changed contract. Preserve unrelated
  changes; do not hide broad renames/reformatting in a feature change.

## Instruction routing examples

| Change | Read and review |
| --- | --- |
| Shared repository | Root → sharedLogic → feature instructions/contract, architecture, Kotlin, documentation, verification; source/verb/mapping and error/cancellation behavior |
| Compose screen | Root → sharedUI → feature; Android/Desktop host rules for integration; Kotlin resource names and test identifiers; ViewModel and component ownership |
| Swift task lifecycle | Root → owning Apple module → feature; Swift and documentation; actor transitions, cancellation and owner disposal |
| Resources | Root → resource-owning module; Kotlin for Compose/Android, Swift for Apple; feature/common scope and labels versus automation identifiers |
| Feature tests | Root → tested module → feature contract (explicitly across source sets); verification and language style; local fixtures and relevant doubles |

[Google code review guidance](https://google.github.io/eng-practices/review/reviewer/looking-for.html)
informs the separation of semantic review from mechanical style checks. The exact
routing and checklist above are KMPNews decisions.

See the [adoption record](../code-standards-adoption.md) for this update’s checks and limits.
