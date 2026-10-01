# User Collections Specification

## Purpose

Local, user-built sets of affirmations (owned or catalog) that can be toggled on as feed sources. Device-local; no cloud sync.

## Requirements

### Requirement: Collection naming

A collection name MUST be trimmed, non-blank, at most 40 characters, and unique case-insensitively among existing collections. Create and rename MUST reject violations and leave state unchanged.

#### Scenario: Name is trimmed

- GIVEN no collections
- WHEN the user creates "  Calm  "
- THEN a collection named "Calm" exists

#### Scenario: Duplicate rejected

- GIVEN a collection "Calm"
- WHEN the user creates or renames another to "cALM"
- THEN the operation is rejected and no change is persisted

#### Scenario: Blank or too long rejected

- GIVEN any state
- WHEN the name is blank or 41 characters after trimming
- THEN the operation is rejected

### Requirement: Tier limit

A Free user MUST NOT exceed 2 collections; a Pro user MAY create unlimited collections. On downgrade, existing collections MUST be kept fully usable (membership, toggle, rename, delete); only new creation MUST be blocked while the count is at or above the limit.

#### Scenario: Free blocked at third

- GIVEN a Free user with 2 collections
- WHEN they create a third
- THEN creation is rejected

#### Scenario: Downgrade keeps collections

- GIVEN a downgraded user with 5 collections
- WHEN they toggle or add items to any of them
- THEN the operation succeeds
- AND creating a new one is rejected until the count is below 2

### Requirement: Creation and initial state

A new collection MUST have a UUID id, start enabled, and have `lastUsedAtMillis` equal to its creation time. Creation MAY include an initial affirmation, which MUST become a member.

#### Scenario: Create with affirmation

- GIVEN a Pro user and affirmation A
- WHEN they create "Calm" with A
- THEN "Calm" is enabled, contains A, and `lastUsedAtMillis` equals `createdAtMillis`

### Requirement: Membership

The system MUST allow adding and removing any affirmation id (owned or catalog) to a collection, idempotently. An affirmation MAY belong to several collections. Empty collections MUST be allowed. Hiding an affirmation MUST NOT remove membership. Item counts MUST be computed from resolved (existing) affirmations only.

#### Scenario: Add and remove

- GIVEN collection C without A
- WHEN A is added twice, then removed
- THEN C contains A once after the adds and not after the removal

#### Scenario: Orphan not counted

- GIVEN C holds a catalog id no longer in the catalog
- WHEN the count is read
- THEN the orphan is excluded

### Requirement: Enabled toggle and chip order

Collections MUST be listed ordered by `lastUsedAtMillis` descending. Toggling ON MUST set `lastUsedAtMillis` to the current time; toggling OFF MUST NOT change it. Toggles MUST apply immediately and be serialized.

#### Scenario: Toggle ON reorders

- GIVEN collections X (older) and Y, both disabled
- WHEN X is toggled ON
- THEN X is listed before Y

#### Scenario: Toggle OFF keeps order

- GIVEN X listed first and enabled
- WHEN X is toggled OFF
- THEN `lastUsedAtMillis` and position are unchanged

### Requirement: Deletion and cleanup

Deleting a collection MUST remove its memberships (cascade) and require UI confirmation. Deleting an owned affirmation MUST remove it from all collections. A JSON import with replace MUST clear memberships only for the owned affirmations it deletes.

#### Scenario: Owned delete cleans memberships

- GIVEN owned A in collections C1 and C2
- WHEN A is deleted
- THEN neither collection contains A

#### Scenario: Import replace

- GIVEN collections with members
- WHEN a JSON import replaces existing affirmations
- THEN memberships of the deleted owned affirmations are cleared; collections and catalog memberships remain

### Requirement: Persistence and migration

Collections MUST persist locally across app restarts and sign-out. Migration v13 to v14 MUST be additive, create both tables matching the exported v14 schema, and preserve all existing data.

#### Scenario: Upgrade

- GIVEN a v13 database with data
- WHEN migrated to v14
- THEN existing data is intact and both tables exist, empty

#### Scenario: Sign-out

- GIVEN collections exist
- WHEN the user signs out
- THEN collections remain
