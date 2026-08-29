# Exploration: Customizable Affirmation Placeholders (`[token]` inline editing)

## Current State

### Data model

- `Affirmation` (`app/src/main/java/com/pirxhio/affirmity/data/AffirmityAppState.kt:127-133`) has flat `title`/`subtitle: String` fields — no template/placeholder concept, no `AnnotatedString`/tap-to-edit pattern exists anywhere in the codebase today.
- Persistence: `AffirmationEntity` (Room, `app/src/main/java/com/pirxhio/affirmity/data/local/AffirmationEntity.kt`) <-> `RoomAffirmationRepository`/`FirestoreAffirmationRepository`, both implementing `AffirmationRepository`, mirrored via `FirestoreMappers.kt` into `users/{uid}/affirmations`.
- `AffirmationDao` only has `insert`/`deleteById`/`deleteAll` — **no update query exists**. No Room `TypeConverter` exists in this database today.

### Critical constraint

`DataSession` (`app/src/main/java/com/pirxhio/affirmity/data/repository/DataSession.kt`) is a sealed Local (Room) / Remote (Firestore) session, swapped atomically per the project's documented "swap granularity" decision — never half-swapped. Any override storage added to `DataSession.affirmations` must work symmetrically in both the Room and Firestore implementations.

### Existing precedent for local-only (non-synced) storage

`AffirmationGroupPreferences` (DataStore, `app/src/main/java/com/pirxhio/affirmity/data/local/AffirmationGroupPreferences.kt`) is explicitly commented as deliberately outside `DataSession`/Firestore sync — a precedent for "device-local, not synced" state, but see rejection rationale below.

### Rendering

`AffirmationsScreen.kt` -> `AffirmationCard` renders `title`/`subtitle` as plain `Text` — no span handling, no click handling, nothing to reuse for tap-to-edit today.

## Approaches

### (a) Parsing the bracket syntax

1. **Parse-at-render-time regex over the plain string.** Low effort, but token identity is string-offset based (fragile — a token's "identity" shifts if surrounding text changes) and ambiguous with literal brackets in affirmation text.
2. **Structured template model** (`List<TemplateSegment>`, parsed once at save time). Medium effort, stable token IDs, requires a Room migration + the codebase's first `TypeConverter`.

**Recommended:** parse bracket syntax once at save time into structured segments; keep the authoring UI as free-text bracket syntax (author still types `"Gano [10k] [dolares] al [mes]"`, the app parses that into segments once and stores the structured form).

### (b) Where to persist user overrides

1. **Separate Room table** keyed by `(affirmationId, tokenKey)` + a matching Firestore subcollection. Most "correct" relationally, but highest effort — Firestore subcollections don't cascade-delete, so `FirestoreAffirmationRepository.deleteAll()` already has to manually batch-delete for this exact reason; a new subcollection repeats that maintenance burden.
2. **DataStore Preferences**, piggybacking the `AffirmationGroupPreferences` pattern. **Rejected** — this project's own `firebase-migration/exploration.md` already flagged "dual-source-of-truth drift" as a real bug class here (see `TrackerPreferences` history). A user's override value is durable personal data tied to a specific affirmation, not a device-local UI preference — using DataStore would reintroduce that exact risk.
3. **Embedded `Map<String, String>` field** directly on `AffirmationEntity`/the Firestore doc. Lowest complexity for this feature's scale (a handful of tokens per affirmation), follows the existing `groupId` "add a field to the entity" precedent already used in this codebase, but requires introducing the codebase's first Room `TypeConverter` and careful attention to Firestore `SetOptions.merge()` nested-map semantics (merging a nested map isn't a plain shallow merge).

**Recommended:** embedded `Map<String, String>` field on the same entity/doc — option (b3), paired with option (a2) for parsing.

## Recommendation

Structured template parsed at save-time (a2) + overrides stored as an embedded `Map<String, String>` field on the same `AffirmationEntity`/Firestore doc (b3), not a separate table/subcollection. Net schema effect: `Affirmation.title` becomes a parsed template plus an `overrides: Map<String, String>` field, added as a new field to the existing schema — following the same "add a field" precedent already used for `groupId` — rather than standing up a new storage subsystem.

## Risks

- No Room `TypeConverter` exists in `AffirmityDatabase` yet — genuinely new infrastructure, needs its own tests (`strict_tdd` is active for this project).
- `AffirmationDao` has no update query — needs adding (or rely on `OnConflictStrategy.REPLACE` on insert).
- A Room schema migration is required (versioned, following the existing `groupId` `@ColumnInfo(defaultValue)` precedent).
- Bracket-escaping ambiguity is unresolved (what happens if an affirmation needs a literal `[` or `]`?) — needs a product decision before spec.
- Firestore `SetOptions.merge()` nested-map semantics need explicit design attention — nested-map merge behavior is not "it just works" and needs to be verified/designed, not assumed.
- The JSON import format (`AFFIRMATIONS_JSON_EXAMPLE`) is not yet updated to document bracket syntax for authors.

## Open Questions for Proposal

1. Bracket-escaping syntax for literal `[`/`]` characters in affirmation text.
2. Confirm overrides should sync via Firestore (durable, per-user, cross-device) rather than stay device-local — the exploration recommends Firestore-synced, but this is a product call the proposal should lock in explicitly.

---

**Status:** partial (exploration complete; two open product questions above need answers before proposal locks scope)
**Next recommended:** sdd-propose (after confirming bracket-escaping and override-sync-scope with the user)
