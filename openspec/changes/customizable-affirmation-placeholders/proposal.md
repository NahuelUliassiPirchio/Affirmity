# Proposal: Customizable Affirmation Placeholders

## Intent

Affirmations today are fixed strings, so a catalog line like "Gano 10k dolares al mes" is either right for the user or useless to them. Authors cannot ship one affirmation that adapts, and users cannot personalize without recreating the affirmation by hand in "Mis afirmaciones". This change introduces bracket-delimited placeholder tokens (`"Gano [10k] [dolares] al [mes]"`) rendered as visually distinct, tappable inline inputs, so one affirmation serves every user and personalization survives reinstall and device change.

## Scope

### In Scope
- **Token syntax**: `[...]` bracket parsing, resolved once at save/import time into structured segments (literal vs. token). Brackets are **always** delimiters — no escape syntax for literal `[`/`]` (locked product decision).
- **Rendering**: bracketed tokens rendered with distinct background/typography inside `AffirmationCard` (and `MyAffirmationsScreen` preview surfaces).
- **Inline editing**: tapping a token swaps it for a pre-filled inline text field; saving stores a per-user override keyed by token.
- **Persistence**: overrides stored as an embedded `Map<String, String>` field on `AffirmationEntity` + the `users/{uid}/affirmations/{id}` doc — no new table, no new subcollection. Requires the codebase's first Room `TypeConverter`, a `6 -> 7` migration, and an `AffirmationDao` update path.
- **Empty override = revert**: saving an empty value removes the override and restores the original token value; empty overrides are never persisted.
- **Applies to both catalog/bundled and user-created affirmations.**
- **Available to all users** — free and Pro, no entitlement gate.
- JSON import format (`AFFIRMATIONS_JSON_EXAMPLE`) documents bracket syntax for authors.
- Unit tests (strict TDD) for parsing, override resolution, revert-on-empty, and mappers.

### Out of Scope
- Escape syntax / literal brackets; typed or validated tokens (number, date, enum); default-value or hint metadata beyond the authored text.
- Per-device overrides, override history, or undo.
- Widget (Glance) and notification rendering of overrides — deferred; those surfaces render resolved-with-defaults text.
- Any change to `DataSession` swap granularity, `DayClock`, or entitlement logic.

## Capabilities

### New Capabilities
- `affirmation-placeholders`: bracket token syntax, parsing rules, distinct rendering, inline tap-to-edit, override resolution, and revert-on-empty semantics.

### Modified Capabilities
- `data-sync`: the per-user `affirmations/{id}` document schema gains an `overrides` map field, and the Room `affirmations` table gains the matching column — extending the existing "Per-User Collection Schema" requirement.

## Approach

Parse bracket syntax once (at authoring/import time) into a stable list of segments with deterministic token keys, so token identity does not shift when surrounding text changes. Store the authored text as-is and derive segments on read via a pure parser (`AffirmationTemplate`), keeping the parser unit-testable with no Android dependency. Overrides live as an embedded map on the same entity/document, following the existing `groupId` "add a field" precedent rather than standing up new storage. Firestore writes use explicit whole-map replacement (not nested `SetOptions.merge()`) so override deletion is not silently swallowed by merge semantics — this is the main item for `sdd-design` to lock down.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `data/AffirmityAppState.kt` | Modified | `Affirmation` gains `overrides`; save/update action for token edits |
| `data/local/AffirmationEntity.kt` | Modified | New `overrides` column + `@ColumnInfo(defaultValue = "{}")` |
| `data/local/AffirmityDatabase.kt` | Modified | `MIGRATION_6_7` (ALTER TABLE), first `@TypeConverters` registration |
| `data/local/AffirmationDao.kt` | Modified | Update/upsert path for overrides |
| `data/local/OverrideConverters.kt` | New | Room `TypeConverter` for `Map<String, String>` |
| `data/AffirmationTemplate.kt` | New | Pure bracket parser + override resolution |
| `data/remote/FirestoreMappers.kt`, `FirestoreAffirmationRepository.kt` | Modified | Map overrides field; explicit non-merge write for deletions |
| `data/remote/MigrationPlan.kt`/`FirestoreMigrator.kt` | Modified | Carry overrides through migration |
| `data/AffirmationImport.kt` | Modified | Documented bracket syntax in JSON example |
| `ui/affirmations/AffirmationsScreen.kt`, `ui/myaffirmations/MyAffirmationsScreen.kt` | Modified | Token rendering + inline edit interaction |
| `app/src/test/.../data/` | New | Parser, resolution, mapper, migration-plan tests |
| `app/src/androidTest/.../AffirmityDatabaseMigrationTest.kt` | Modified | 6 -> 7 migration coverage |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Firestore nested-map merge silently keeps deleted overrides | High | Design phase decides explicit field-level replacement; unit test on the mapper/write path |
| First Room `TypeConverter` in the codebase — new infrastructure | Med | Isolated converter class, own unit tests, TDD per `config.yaml` |
| Migration `6 -> 7` fails or backfills wrong default | Med | Additive `ALTER TABLE ... DEFAULT '{}'`, mirrors the proven `MIGRATION_4_5` pattern; `androidTest` migration test |
| No escape syntax: an authored affirmation legitimately needing `[` renders as a token | Med | Accepted, user-confirmed. Document in the import format; authors avoid brackets in literal text |
| Token identity drift if authored text is later edited | Med | Deterministic index+text-derived token keys; stale keys are ignored and fall back to the original value |
| Overrides on catalog affirmations diverge if bundled content is updated | Low | Unmatched override keys are ignored, never crash; original text always renders |
| PR exceeds the 400-line review budget | High | **`single-pr` delivery strategy confirmed by user** — accept a `size:exception` rather than chaining PRs. Downstream `sdd-tasks` MUST NOT plan a chained-PR split |
| Widget/notification surfaces show un-overridden text | Med (by design) | Explicitly out of scope; candidate follow-up change |

## Rollback Plan

1. Revert the feature branch. The `overrides` column is additive with a `'{}'` default; pre-change code ignores it, so a downgraded app still reads every affirmation.
2. Room downgrade: Room rejects an unknown-higher version, so rollback for already-migrated devices requires either keeping `MIGRATION_6_7` in place or shipping a `7 -> 6` no-op drop-column migration — decide in design before merge.
3. Firestore: the `overrides` field is ignored by pre-change mappers; no data deletion is required and nothing is lost.
4. Partial rollback: keep the schema, disable token parsing behind a single constant so all affirmations render as plain text.

## Dependencies

- `firestore-data` (archived) — supplies the `users/{uid}/affirmations/{id}` schema this change extends.
- No new Gradle dependency (parsing uses the Kotlin stdlib `Regex`; no minSdk 24 concern).

## Success Criteria

- [ ] `gradlew.bat assembleDebug` and `gradlew.bat testDebugUnitTest` pass.
- [ ] An affirmation containing `[token]` renders tokens with distinct background/typography; non-bracketed affirmations render byte-for-byte as before.
- [ ] Tapping a token opens an inline input pre-filled with the current value; saving a new value updates the rendered affirmation immediately.
- [ ] Saving an empty value reverts the token to its original authored value and persists no override.
- [ ] Overrides survive app restart, and reinstalling + signing in restores them from Firestore.
- [ ] Editing works identically on catalog/bundled and user-created affirmations, for both free and Pro accounts.
- [ ] Deleting an override on one device removes it on a second device (no stale merge residue).
- [ ] Room `6 -> 7` migration test green; existing `androidTest` suite green.

## Proposal question round

All five product questions were answered and locked by the user before this proposal: no bracket escaping, Firestore-synced overrides, applies to catalog + user-created, empty save reverts to original, available to all users. Two items remain for confirmation before `sdd-spec`:

1. **Widget/notification surfaces are out of scope.** The Glance widget and FCM notification text will render the *original* token values, not the user's overrides — so the same affirmation can read differently on the home screen than in the app. Confirm this is acceptable for the first slice, or say if the widget must respect overrides.
2. **Token identity when authored text is edited.** If a user edits a "Mis afirmaciones" affirmation and changes the text around a token, this proposal drops overrides whose keys no longer match (falls back to the original value) rather than trying to remap them. Confirm, or say if overrides must be preserved across text edits.
