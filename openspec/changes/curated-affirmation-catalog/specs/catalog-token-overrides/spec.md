# Catalog Token Overrides Specification

## Purpose

Per-user placeholder-token overrides on shared, read-only catalog
affirmations, keyed by `(uid, catalogAffirmationId)`, so the shipped
tap-to-edit token feature keeps working on catalog content without
mutating the shared row.

## Requirements

### Requirement: Per-User Override Sync Surface

The system SHALL persist catalog token overrides at
`users/{uid}/catalogOverrides/{catalogAffirmationId}` in Firestore and in a
new local Room table `catalog_affirmation_overrides`
(`catalogAffirmationId` primary key, `overrides: Map<String, String>`),
distinct from the existing `AffirmationEntity.overrides` column used by
owned rows.

#### Scenario: Override is written to the per-user override surface

- GIVEN a signed-in user edits a token on a catalog affirmation
- WHEN the override is saved
- THEN it MUST be written to
  `users/{uid}/catalogOverrides/{catalogAffirmationId}`
- AND the shared `catalogAffirmations/{catalogAffirmationId}` document MUST
  remain unchanged

#### Scenario: Two users override the same catalog affirmation independently

- GIVEN two different users both open the same catalog affirmation
- WHEN each edits and saves a different value for the same token
- THEN each user's Room and Firestore override rows MUST reflect only their
  own edit
- AND neither user's override MUST be visible to the other

### Requirement: Tap-to-Edit Works Unchanged on Catalog Rows

The system SHALL apply `AffirmationTemplateParser` and the existing
tap-to-edit inline input identically to catalog affirmations, resolving the
token's current effective value from the catalog override map (if present)
else the catalog row's original authored text.

#### Scenario: Tapping a token on a catalog affirmation opens the editor

- GIVEN a rendered catalog affirmation containing a bracketed `[token]`
- WHEN the user taps that token
- THEN the inline edit input MUST open, pre-filled with the current
  effective value

#### Scenario: Saved catalog override persists per user, not globally

- GIVEN a user saves a non-empty override on a catalog affirmation's token
- WHEN the app is restarted
- THEN the affirmation MUST render with that user's saved override
- AND no other user's copy of the same catalog affirmation MUST be affected

#### Scenario: Empty-input save reverts to the catalog's original value

- GIVEN a catalog affirmation token currently overridden
- WHEN the user opens the input, clears it, and saves
- THEN the token MUST render the catalog's original authored value
- AND the stored catalog override for that token MUST be removed
