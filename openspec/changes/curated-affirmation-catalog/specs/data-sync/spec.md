# Delta for Data Sync

## MODIFIED Requirements

### Requirement: Firestore Security Rules

The system SHALL ship a repo-root `firestore.rules` file restricting all
reads and writes under `users/{uid}/**` to requests where
`request.auth.uid == uid`, deployed manually (no CI automation). The rules
SHALL additionally allow public read and deny all client writes on the
shared `catalogAffirmations/**` path.
(Previously: rules only covered `users/{uid}/**`; no shared path existed.)

#### Scenario: Owner can read and write their own data

- GIVEN an authenticated user with uid `A`
- WHEN they read or write any document under `users/A/**`
- THEN the operation MUST be allowed by the deployed rules

#### Scenario: A user cannot access another user's data

- GIVEN an authenticated user with uid `A`
- WHEN they attempt to read or write any document under `users/B/**`
  (`B != A`)
- THEN the operation MUST be denied by the deployed rules

#### Scenario: Any client can read the shared catalog

- GIVEN any client, signed in or signed out
- WHEN it reads `catalogAffirmations/{id}`
- THEN the operation MUST be allowed

#### Scenario: No client can write the shared catalog

- GIVEN any authenticated client (non-privileged)
- WHEN it attempts to write `catalogAffirmations/{id}`
- THEN the operation MUST be denied by the deployed rules

## ADDED Requirements

### Requirement: Shared Catalog Collection Outside Per-User Schema

The system SHALL introduce a shared, non-per-user top-level
`catalogAffirmations` collection alongside the existing `users/{uid}`
schema, plus a `catalogVersion` marker document and a per-user
`users/{uid}/catalogOverrides/{catalogAffirmationId}` subcollection. The
shared collection MUST NOT be duplicated per user.

#### Scenario: Catalog is stored once, not per user

- GIVEN N signed-in users
- WHEN the catalog is seeded
- THEN exactly one copy of each catalog affirmation MUST exist under
  `catalogAffirmations/`, independent of N

#### Scenario: Catalog overrides live under the per-user schema

- GIVEN a signed-in user saves a catalog token override
- WHEN the write is persisted
- THEN it MUST be written under `users/{uid}/catalogOverrides/`, not under
  the shared `catalogAffirmations/` collection
