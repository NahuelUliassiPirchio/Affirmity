# Affirmation Catalog Specification

## Purpose

Shared, read-only, 2,712-item Spanish affirmation catalog: Firestore storage,
Room offline cache, a stable ID scheme, 14 new Universe-derived groups
replacing the 3 removed legacy groups in the selector, collection-level access
gating including a repeating time-limited ad-unlock policy, a pre-import
sanitization gate, and an idempotent seeding mechanism. Catalog rows are not
user-owned, not editable, not deletable.

## Requirements

### Requirement: Shared Catalog Storage Path

The system SHALL store catalog affirmations at a shared, flat top-level
Firestore path `catalogAffirmations/{catalogAffirmationId}`, each document
carrying `groupId`, `themeId`, and `collectionId` fields for querying,
world-readable and client-write-denied.

#### Scenario: Any signed-in or signed-out user can read the catalog

- GIVEN a user, signed in or signed out
- WHEN the client reads `catalogAffirmations/{id}`
- THEN the read MUST succeed regardless of auth state

#### Scenario: Client writes to the catalog are denied

- GIVEN any authenticated client (non-privileged)
- WHEN it attempts to write to `catalogAffirmations/{id}`
- THEN the write MUST be denied by Firestore security rules

### Requirement: Room Catalog Cache Table

The system SHALL cache catalog affirmations locally in a new Room table
`catalog_affirmations`, introduced via additive migration `MIGRATION_8_9`
(database version `8 -> 9`), separate from `AffirmationEntity`/`affirmations`.
Catalog rows MUST render from this local cache without a network round trip
once synced.

#### Scenario: Catalog renders offline after first sync

- GIVEN the catalog has synced into `catalog_affirmations` at least once
- WHEN the device goes offline
- THEN catalog affirmations MUST still render from the local Room cache

#### Scenario: Migration is additive and non-destructive

- GIVEN a device on database version 8
- WHEN `MIGRATION_8_9` runs
- THEN all existing tables and rows MUST remain unchanged
- AND the new `catalog_affirmations` table MUST exist and be empty pre-sync

### Requirement: Stable Catalog ID Scheme

The system SHALL identify every catalog affirmation with a permanent id
formed by prefixing `cat_` to the source catalog's own dotted id verbatim
(e.g. `cat_self_worth.feeling_enough.intrinsic_worth.001`), NOT a derived
`{universeSlug}_{themeSlug}_{nnn}` scheme — the source's per-collection
numbering restarts at `001` within each theme (e.g. `self_worth.feeling_enough`
alone holds 6 collections with independently restarting sequences), so a
scheme that drops the collection level collides. The `cat_` prefix MUST
guarantee zero collision with any `UUID.randomUUID()`-generated id used for
user-owned content, since UUIDs never contain underscores in that position.

#### Scenario: Catalog id never collides with a generated UUID

- GIVEN the set of all catalog ids and the set of all possible
  `UUID.randomUUID()` outputs
- WHEN any catalog id is compared against any UUID string
- THEN no catalog id MUST ever equal a valid UUID string

#### Scenario: Catalog ids are permanent

- GIVEN a catalog affirmation referenced by a favorite or an override
- WHEN the catalog is re-seeded or updated
- THEN that affirmation's id MUST NOT change

### Requirement: Universe-Derived Groups in the Selector

The system SHALL add 14 new `AffirmationGroup` entries derived from the
source catalog's Universes to `selectableAffirmationGroups()`, growing the
selector from 1 (`personalizadas`, always-selected) to approximately 15
entries, selectable and deselectable identically to `personalizadas`'s
thematic-selection peers.

#### Scenario: New groups appear in the selector

- GIVEN the group selector is opened
- WHEN the list of groups is rendered
- THEN all 14 Universe-derived groups MUST appear alongside `personalizadas`

#### Scenario: A Universe-derived group is selectable and deselectable

- GIVEN a Universe-derived group the user has access to
- WHEN the user selects it, then deselects it
- THEN the group's selected state MUST toggle each time

#### Scenario: Selecting a Universe-derived group renders real content

- GIVEN a Universe-derived group with seeded catalog affirmations
- WHEN the user selects that group
- THEN the feed MUST render actual catalog affirmations for that group, not
  an empty state

### Requirement: Legacy Placeholder Groups Are Removed

The system SHALL remove the 3 placeholder groups `bienestar`, `autocuidado`,
and `fuerza_de_voluntad` from `selectableAffirmationGroups()` entirely, with
no fallback or migration path — these groups existed only as scaffolding and
carried no real content or user data. `personalizadas` (the always-selected
custom-affirmation group) is NOT affected by this removal.

#### Scenario: Legacy groups no longer appear in the selector

- GIVEN the group selector is opened
- WHEN the list of groups is rendered
- THEN `bienestar`, `autocuidado`, and `fuerza_de_voluntad` MUST NOT appear

#### Scenario: `personalizadas` is unaffected by the legacy-group removal

- GIVEN the legacy groups have been removed from
  `selectableAffirmationGroups()`
- WHEN the selector is opened
- THEN `personalizadas` MUST still appear, always-selected, exactly as
  before the removal

### Requirement: ContentType Extension for Collection-Level Access

The system SHALL add a new `ContentType` enum constant,
`AFFIRMATION_COLLECTION`, for collection-level gating — the source catalog
declares `access{tier, rewardedUnlockHours}` on collections only, never on
themes, so gating resolves at the collection, not the theme. The constant's
`wireName` MUST NOT contain an underscore character, preserving the
`ContentKey.storageKey`/`ContentKey.parse` invariant that splits on the first
underscore.

#### Scenario: New wireName contains no underscore

- GIVEN the new `ContentType` constant's `wireName`
- WHEN the string is inspected
- THEN it MUST NOT contain `_`

#### Scenario: storageKey and parse round-trip for the new type

- GIVEN a `ContentKey` built with the new `ContentType` and a dotted
  collection id containing underscores (e.g.
  `self_worth.feeling_enough.intrinsic_worth`)
- WHEN `storageKey` is computed and then passed to `ContentKey.parse`
- THEN `parse` MUST return a `ContentKey` equal to the original
- AND the recovered `type` and `id` MUST exactly match the originals

### Requirement: Effective Access Resolution — Collection Falls Back to Group

The system SHALL resolve a catalog affirmation's effective access as the
most-restrictive of its parent group's `ContentAccess` and its own
collection's `ContentAccess` when declared. A collection with no declared
`ContentAccess` contributes pure inheritance from the group.

#### Scenario: Collection with its own tier gates independently

- GIVEN a collection with a declared Pro `ContentAccess` inside a Free group
- WHEN a free-tier user attempts to access that collection's content
- THEN access MUST be denied per the collection's own tier, even though the
  group itself is Free

#### Scenario: Collection without a declared tier inherits the group's

- GIVEN a collection with no declared `ContentAccess`
- WHEN effective access is resolved
- THEN it MUST equal the parent group's `ContentAccess`

### Requirement: Repeating Time-Limited Ad Unlock (TIMED_REPEATABLE)

The system SHALL support a new `AdUnlockPolicy.TIMED_REPEATABLE`, distinct
from the existing one-time lifetime `ONE_TIME_TRIAL` grant: watching a
rewarded ad grants access to the gated content for a declared duration (in
hours), and once that grant expires the content MUST re-lock and MAY be
unlocked again by watching another ad. The source catalog's 75 Pro
collections declaring `rewardedUnlockHours: 24` SHALL map to
`ContentAccess(AccessTier.PRO, AdUnlockPolicy.TIMED_REPEATABLE)` with a
24-hour grant duration. This is additive: it MUST NOT change the behavior of
existing `ONE_TIME_TRIAL` or `PER_USE` gated content (meditations, custom
affirmation slots).

#### Scenario: Watching a rewarded ad grants a time-limited unlock

- GIVEN a `TIMED_REPEATABLE` collection with a declared N-hour duration
- WHEN the user watches a rewarded ad for that collection
- THEN the collection MUST become accessible immediately
- AND the grant MUST be durably recorded with an expiration N hours from
  grant time

#### Scenario: An expired grant re-locks and can be re-earned

- GIVEN a `TIMED_REPEATABLE` grant whose expiration has passed
- WHEN the user attempts to access that collection
- THEN it MUST be locked again
- AND the user MUST be able to watch another rewarded ad to re-unlock it for
  a new N-hour window

#### Scenario: Existing ad-unlock policies are unaffected

- GIVEN content gated by `ONE_TIME_TRIAL` or `PER_USE`
- WHEN the `TIMED_REPEATABLE` policy is introduced
- THEN the existing content's grant behavior MUST remain unchanged

### Requirement: Pre-Import Bracket Sanitization Gate

The system SHALL scan all catalog source text for literal `[`/`]`
characters before import and strip or rewrite every occurrence. The import
MUST fail if any literal `[`/`]` survives in the produced seed artifact.
`AffirmationTemplateParser` MUST NOT be modified to support escaping.

#### Scenario: Import fails when a literal bracket survives

- GIVEN a source text containing an unintended literal `[` or `]`
  character after sanitization
- WHEN the import/seed-build step runs
- THEN the build MUST fail before any Firestore write occurs

#### Scenario: Sanitized catalog contains zero literal brackets

- GIVEN the final sanitized seed artifact
- WHEN every affirmation's title/subtitle is scanned
- THEN zero literal `[` or `]` characters MUST be found outside intended
  token delimiters

### Requirement: Idempotent Chunked Catalog Seeding

The system SHALL seed the catalog via a dedicated seed plan that chunks
writes (reusing the `chunkWithMarkerLast` discipline from `MigrationPlan`),
writing a `catalogVersion` marker document last, gated on that marker.
Seeding MUST NOT route through `FirestoreMigrator`.

#### Scenario: Re-running seeding produces the same end state

- GIVEN the catalog has already been fully seeded and the version marker
  written
- WHEN the seed plan is executed again
- THEN the resulting Firestore state MUST be identical to running it once

#### Scenario: A partial seed (network drop) is safely resumable

- GIVEN a seed run stopped mid-batch before the version marker was written
- WHEN the seed plan runs again
- THEN it MUST re-apply safely without duplicating documents
- AND the absence of the version marker MUST mean "not yet seeded"

## Out of Scope

- Theme/Collection as separate UI browse levels — the selector stays
  group-only; themes are content metadata and access units only.
- Locale/i18n infrastructure — Spanish only, no `locale` field.
- Metadata registries (`tones`, `semanticAngles`, `desiredStates`,
  `contexts`, `moments`, `conceptTags`) — no consumer exists.
