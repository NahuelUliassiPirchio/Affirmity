# Delta for Data Sync

## MODIFIED Requirements

### Requirement: Per-User Collection Schema

The system SHALL persist signed-in user data under `users/{uid}` with
`affirmations/{id}` (mirrors `AffirmationEntity` 1:1, including an
`overrides: Map<String, String>` field keyed by token identity for
placeholder token overrides), `dailyCompletions/{epochDay}` (mirrors
`DailyCompletionEntity` 1:1, doc id = stringified `epochDay`),
`settings/preferences` (`meditationDurationSeconds`, both notification
channels, and a persisted IANA timezone id field), `meta/migrated` (migration
marker), and `fcmTokens/{tokenId}` (registered FCM device tokens for the
user). The system MUST NOT store a cached streak field anywhere in this
schema.
(Previously: `affirmations/{id}` had no `overrides` field.)

#### Scenario: Streak stays re-derived, never cached

- GIVEN a signed-in user's `dailyCompletions` documents in Firestore
- WHEN `DailyCompletionStats` computes the current streak
- THEN it MUST walk raw per-day rows exactly as it does for Room today
- AND no document in the schema MUST contain a precomputed streak value

#### Scenario: Timezone field is part of settings

- GIVEN a signed-in user's `settings/preferences` document
- WHEN the document is read
- THEN it MUST include an IANA timezone id field alongside existing
  preference fields

#### Scenario: FCM tokens are stored per-user, not per-field

- GIVEN a signed-in user registers a device for push notifications
- WHEN the token is persisted
- THEN it MUST be written under `users/{uid}/fcmTokens/{tokenId}`, not
  embedded as a single field on another document
- AND multiple devices for the same user MAY each have their own token
  document

#### Scenario: Affirmation document carries an overrides map

- GIVEN a signed-in user's `affirmations/{id}` document
- WHEN the document is read
- THEN it MUST include an `overrides` field mapping token keys to their
  overridden string values
- AND an affirmation with no active overrides MUST have an empty (or absent)
  `overrides` map, never a null placeholder entry

## ADDED Requirements

### Requirement: Override Sync via Firestore

The system SHALL sync the `overrides` map field on
`users/{uid}/affirmations/{id}` using explicit whole-map field replacement on
write, not a nested-merge write, so that removing a key (e.g. reverting to an
empty input) is reflected on every other signed-in device.

#### Scenario: Override deletion propagates across devices

- GIVEN a user removes an override on device A (saves an empty input)
- WHEN device B next reads the affirmation from Firestore
- THEN device B MUST NOT show the previously deleted override
- AND the `overrides` map on device B MUST match the map written by device A

#### Scenario: Overrides survive reinstall

- GIVEN a signed-in user with persisted overrides in Firestore
- WHEN the user reinstalls the app and signs in on a new device
- THEN the affirmation MUST render with the same overrides as before
  reinstall

### Requirement: Overrides Field in Room Schema

The system SHALL add an `overrides` column to the local Room `affirmations`
table, additive via a versioned migration with a non-null default, mirroring
the Firestore `overrides` map field for the signed-out/local-only path.

#### Scenario: Existing local affirmations migrate with empty overrides

- GIVEN an existing local database without the `overrides` column
- WHEN the migration runs
- THEN every existing row MUST receive an empty-map default for `overrides`
- AND no existing affirmation data MUST be lost or altered
