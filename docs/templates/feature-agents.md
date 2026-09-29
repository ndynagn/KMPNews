# Feature instruction template (not active instructions)

Copy the body below into an actual feature's `AGENTS.md` only when that feature
needs rules beyond its module. Replace every placeholder and delete irrelevant
sections. Add an adjacent `CLAUDE.md` containing `@AGENTS.md`. Do not create a
feature merely to host this template. Markdown instructions within Apple source
folders must be excluded from target membership and checked in the built bundle.

## <Feature name>

### Scope and contract

- Purpose: <agreed user capability>.
- Canonical product/API contract: <relative link and relevant section>.
- Owned paths and public entry points: <actual implementation and test paths>.
- Permitted dependencies: <public domain contracts / shared services>.
- Platform-specific instructions: <only additional behavior, not copied rules>.

### Invariants and behavior

- <identity, ordering, validation and persistence rules actually in the contract>.
- <supported state transitions, loading/empty/error/content and recovery>.
- <concurrency/conflict behavior; do not invent pagination or offline semantics>.

### Lifecycle and failures

- <task/subscription owner, initial-load trigger, repeatability and cancellation>.
- <expected errors and UI representation, including transient effect semantics>.
- <platform differences and window/navigation ownership>.

### Verification

- <focused test commands and deterministic fixtures>.
- <observable acceptance scenarios and relevant UI/runtime checks>.
- <known blocker, confirmed source, affected scope and exact removal condition>.

Keep one canonical shared contract. Platform instructions link to it and only add
local differences. Source-set boundaries do not inherit each other's instructions;
link the canonical feature rules when changing corresponding platform code/tests.
