# Delta for Affirmation Favorites

## MODIFIED Requirements

### Requirement: Local Favorite Persistence

The system SHALL persist favorite status in a standalone local Room table
`favorite_affirmations` (`affirmationId` primary key, `favoritedAtMillis`),
following the `ad_unlock` / `daily_completion` standalone-table convention —
not a boolean column on `AffirmationEntity`. `affirmationId` MAY reference
either a user-owned affirmation id (`UUID.randomUUID()`) or a catalog
affirmation id (`cat_*`). Favorites MUST survive app restart.
(Previously: `affirmationId` only ever referenced a user-owned
`AffirmationEntity.id`.)

#### Scenario: Favoriting persists across app restart

- GIVEN the user has favorited an affirmation
- WHEN the app is fully restarted
- THEN the affirmation MUST still be favorited
- AND it MUST still appear in the Favorites list

#### Scenario: Unfavoriting persists across app restart

- GIVEN the user has unfavorited a previously favorited affirmation
- WHEN the app is fully restarted
- THEN the affirmation MUST remain unfavorited

#### Scenario: A catalog affirmation id can be favorited

- GIVEN a catalog affirmation with a `cat_*` id
- WHEN the user favorites it
- THEN `favorite_affirmations` MUST store that `cat_*` id as
  `affirmationId`, following the same schema as owned affirmations

## ADDED Requirements

### Requirement: Favorites Merge Personal and Catalog ID Spaces

The system SHALL resolve the Favorites list by looking up each
`favorite_affirmations.affirmationId` against both the owned
`affirmations` table and the `catalog_affirmations` cache, merging results
into a single list ordered by `favoritedAtMillis`, without orphaning
catalog-sourced favorites.

#### Scenario: Favorites list shows both personal and catalog favorites together

- GIVEN the user has favorited one owned affirmation and one catalog
  affirmation
- WHEN the Favorites screen is opened
- THEN both MUST appear in a single merged list ordered by
  `favoritedAtMillis`

#### Scenario: A favorited catalog affirmation renders with overrides applied

- GIVEN a favorited catalog affirmation with a saved per-user token override
- WHEN it is rendered on the Favorites screen
- THEN it MUST render using the same effective-value resolution (override
  if present, else catalog original) as the main feed

#### Scenario: A stale favorite pointing at removed content is dropped, not crashed

- GIVEN a `favorite_affirmations` row whose `affirmationId` matches neither
  an owned affirmation nor a catalog affirmation (e.g. archived catalog
  content)
- WHEN the Favorites list is resolved
- THEN that row MUST be silently excluded from the rendered list
- AND resolution MUST NOT crash or throw
