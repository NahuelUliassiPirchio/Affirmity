# Data Sync Specification

## Purpose

Account-scoped Firestore persistence for affirmations, daily completions, and
preferences, replacing Room/DataStore for signed-in users via a one-time
migration and a hard single-writer cutover. Signed-out users are unaffected;
`user-auth`'s `uid` is consumed unchanged as the scoping key.

## Requirements

### Requirement: Per-User Collection Schema

The system SHALL persist signed-in user data under `users/{uid}` with
`affirmations/{id}` (mirrors `AffirmationEntity` 1:1), `dailyCompletions/{epochDay}`
(mirrors `DailyCompletionEntity` 1:1, doc id = stringified `epochDay`),
`settings/preferences` (`meditationDurationSeconds`, both notification
channels), and `meta/migrated` (migration marker). The system MUST NOT store
a cached streak field anywhere in this schema.

#### Scenario: Streak stays re-derived, never cached

- GIVEN a signed-in user's `dailyCompletions` documents in Firestore
- WHEN `DailyCompletionStats` computes the current streak
- THEN it MUST walk raw per-day rows exactly as it does for Room today
- AND no document in the schema MUST contain a precomputed streak value

### Requirement: Migrate-on-First-Sign-In

On an `authState` transition from `SignedOut` to `SignedIn(uid)`, the system
SHALL check for `users/{uid}/meta/migrated`. If absent, it MUST snapshot the
current Room/DataStore state once and batch-write it to Firestore, writing
the migration marker last, in the same batch, as confirmation.

#### Scenario: First sign-in copies existing data

- GIVEN a device with existing affirmations, completions, and preferences in
  Room/DataStore, and a user who has never signed in
- WHEN the user signs in for the first time
- THEN all existing affirmations, completions, and preferences MUST appear
  under `users/{uid}/...` in Firestore
- AND `users/{uid}/meta/migrated` MUST exist afterward

#### Scenario: Migration is idempotent on retry

- GIVEN a batch write failed before the migration marker was committed
- WHEN the user signs in again (or the app retries)
- THEN migration MUST re-run without producing duplicate or conflicting
  documents, using deterministic document IDs

#### Scenario: Already-migrated user skips migration

- GIVEN `users/{uid}/meta/migrated` already exists
- WHEN the user signs in again
- THEN the system MUST NOT re-run migration or re-write existing data

### Requirement: Single-Writer Cutover

After migration completes for a signed-in `uid`, the system SHALL read and
write exclusively through Firestore for that session. The system MUST NOT
write the same logical data to both Room and Firestore concurrently for the
same signed-in user at any point after migration; permanent dual-write MUST
NOT be introduced as an architecture.

#### Scenario: Post-migration writes land only in Firestore

- GIVEN a signed-in, already-migrated user
- WHEN the user creates a new affirmation or records a completion
- THEN the new data MUST appear in Firestore
- AND the new data MUST NOT appear in the local Room database

#### Scenario: Dual-write is disallowed

- GIVEN a signed-in, already-migrated user
- WHEN any write path is inspected for that user's session
- THEN it MUST target exactly one store (Firestore) per logical write
- AND no code path MUST write the same entity to both Room and Firestore
  outside the one-time migration batch itself

### Requirement: Signed-Out Users Stay on Room

The system SHALL keep signed-out users on the existing Room/DataStore path,
unmodified in behavior, with no Firestore reads or writes performed.

#### Scenario: Signed-out behavior is unchanged

- GIVEN a user who has never signed in, or has signed out
- WHEN using affirmations, streaks, meditation, and preferences
- THEN all data MUST be read from and written to Room/DataStore exactly as
  before this change
- AND no Firestore call MUST be made

### Requirement: Sign-Out Reverts to Stale Room Snapshot

The system SHALL, on sign-out, resume reading and writing Room/DataStore as
the active store, without copying Firestore data back into Room.

#### Scenario: Sign-out shows pre-migration data (accepted)

- GIVEN a user signed in, migrated, and created new data that now lives only
  in Firestore
- WHEN the user signs out
- THEN the app MUST show the Room snapshot as it was at migration time,
  not the Firestore-only data created while signed in
- AND this MUST NOT be treated as data loss — the Firestore data remains
  intact and reappears on the next sign-in

### Requirement: Image Metadata-Only Sync

The system SHALL sync `backgroundType` and `backgroundValue` verbatim to
Firestore without uploading image bytes or introducing Firebase Storage.

#### Scenario: Image affirmation may render broken on a second device (accepted)

- GIVEN an image-type affirmation whose `backgroundValue` is a local file
  path on device A
- WHEN the user signs in on device B after migration/sync
- THEN the affirmation document MUST sync with its original `backgroundValue`
  path
- AND the image MAY fail to render on device B — this is an accepted
  limitation, not a defect, and MUST NOT trigger a crash or data corruption

### Requirement: Firestore Security Rules

The system SHALL ship a repo-root `firestore.rules` file restricting all
reads and writes under `users/{uid}/**` to requests where
`request.auth.uid == uid`, deployed manually (no CI automation).

#### Scenario: Owner can read and write their own data

- GIVEN an authenticated user with uid `A`
- WHEN they read or write any document under `users/A/**`
- THEN the operation MUST be allowed by the deployed rules

#### Scenario: A user cannot access another user's data

- GIVEN an authenticated user with uid `A`
- WHEN they attempt to read or write any document under `users/B/**`
  (`B != A`)
- THEN the operation MUST be denied by the deployed rules
