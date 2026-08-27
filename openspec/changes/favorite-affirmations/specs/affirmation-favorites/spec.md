# Affirmation Favorites Specification

## Purpose

Zero-friction double-tap-to-favorite gesture on `AffirmationCard` in the
`AffirmationsScreen` feed, backed by a standalone local Room table, plus a
Favorites screen listing what the user kept. Local-only this slice — no
Firestore sync. Applies to all users regardless of entitlement.

## Requirements

### Requirement: Double-Tap Toggle on Affirmation Card

The system SHALL let a user double-tap anywhere on an `AffirmationCard` in
the `AffirmationsScreen` feed — including directly on a bracketed `[token]`
— to toggle that affirmation's favorite status. The card's double-tap
detector MUST win over the token's single-tap-to-edit gesture; single-tap
token editing MUST remain unchanged.

#### Scenario: Double-tapping an unfavorited card favorites it

- GIVEN an affirmation card that is not currently favorited
- WHEN the user double-taps anywhere on the card
- THEN the affirmation MUST become favorited
- AND it MUST appear in the Favorites list

#### Scenario: Double-tapping a favorited card unfavorites it

- GIVEN an affirmation card that is currently favorited
- WHEN the user double-taps anywhere on the card
- THEN the affirmation MUST become unfavorited
- AND it MUST no longer appear in the Favorites list

#### Scenario: Double-tapping directly on a token toggles favorite, not the editor

- GIVEN an affirmation card containing a bracketed `[token]`
- WHEN the user double-taps directly on that token's rendered region
- THEN the favorite status MUST toggle
- AND the token's inline edit input MUST NOT open

#### Scenario: Single tap on a token still opens the editor

- GIVEN an affirmation card containing a bracketed `[token]`
- WHEN the user single-taps that token
- THEN the token's inline edit input MUST open as before
- AND the favorite status MUST NOT change

#### Scenario: Double-tapping mid token-edit toggles favorite and leaves the edit untouched

- GIVEN a token's inline edit input is currently open with in-progress,
  unsaved text
- WHEN the user double-taps the card (on the token or elsewhere on the card)
- THEN the favorite status MUST toggle
- AND the in-progress token edit MUST be neither committed nor cancelled by
  that gesture

### Requirement: Local Favorite Persistence

The system SHALL persist favorite status in a standalone local Room table
`favorite_affirmations` (`affirmationId` primary key, `favoritedAtMillis`),
following the `ad_unlock` / `daily_completion` standalone-table convention —
not a boolean column on `AffirmationEntity`. Favorites MUST survive app
restart.

#### Scenario: Favoriting persists across app restart

- GIVEN the user has favorited an affirmation
- WHEN the app is fully restarted
- THEN the affirmation MUST still be favorited
- AND it MUST still appear in the Favorites list

#### Scenario: Unfavoriting persists across app restart

- GIVEN the user has unfavorited a previously favorited affirmation
- WHEN the app is fully restarted
- THEN the affirmation MUST remain unfavorited

### Requirement: Migration 7 to 8 Is Additive and Non-Destructive

The system SHALL introduce the `favorite_affirmations` table via an
additive Room migration `MIGRATION_7_8` (`CREATE TABLE`), bumping the
database version from 7 to 8, without altering, dropping, or migrating data
in any existing table or column.

#### Scenario: Existing installs migrate without data loss

- GIVEN a device on database version 7 with existing affirmations, ad
  unlocks, and daily completions
- WHEN the app upgrades and runs `MIGRATION_7_8`
- THEN all pre-existing tables and rows MUST remain unchanged
- AND the new `favorite_affirmations` table MUST exist and be empty

#### Scenario: Fresh installs create the table via schema, not migration

- GIVEN a fresh install with no prior database
- WHEN the database is created at version 8
- THEN the `favorite_affirmations` table MUST exist as part of the initial
  schema

### Requirement: Deleting an Affirmation Cascades to Its Favorite Row

The system SHALL hard-delete the corresponding `favorite_affirmations` row,
if present, immediately when `removeAffirmation` deletes the source
affirmation. No orphaned favorite rows MUST persist.

#### Scenario: Removing a favorited affirmation deletes its favorite row

- GIVEN an affirmation is currently favorited
- WHEN the user removes that affirmation via `removeAffirmation`
- THEN the corresponding `favorite_affirmations` row MUST be deleted
- AND the affirmation MUST NOT appear in the Favorites list

#### Scenario: Removing a non-favorited affirmation is a no-op on favorites

- GIVEN an affirmation is not favorited
- WHEN the user removes that affirmation via `removeAffirmation`
- THEN no `favorite_affirmations` row deletion is required
- AND the Favorites list MUST be unaffected

### Requirement: Favorites Screen Entry Point and Presentation

The system SHALL provide a Favorites screen reachable via a menu item,
rendered as a full-screen overlay (`Scaffold` + `BackHandler`) before the
`NavigationSuiteScaffold` branch, following the existing
`MyAffirmationsScreen` overlay pattern. The Favorites screen MUST NOT be
added as a new `AppDestinations` entry.

#### Scenario: Opening Favorites from the menu shows the overlay

- GIVEN the user is on any main screen
- WHEN the user selects the Favorites menu item
- THEN a full-screen Favorites overlay MUST be displayed
- AND the underlying `NavigationSuiteScaffold` destinations MUST NOT change

#### Scenario: Back gesture dismisses the Favorites overlay

- GIVEN the Favorites overlay is open
- WHEN the user triggers the back gesture or back button
- THEN the Favorites overlay MUST close
- AND the user MUST return to the screen shown before opening Favorites

### Requirement: Favorites List Ordering

The system SHALL order the Favorites list by `favoritedAtMillis`, most
recently favorited first.

#### Scenario: Most recently favorited affirmation appears first

- GIVEN the user has favorited affirmation A, then later affirmation B
- WHEN the Favorites list is displayed
- THEN affirmation B MUST appear before affirmation A

#### Scenario: Re-favoriting updates order to most recent

- GIVEN affirmation A was favorited before affirmation B
- WHEN the user unfavorites and then re-favorites A
- THEN A MUST appear before B in the Favorites list, reflecting the new
  `favoritedAtMillis`

### Requirement: Favorites Empty State

The system SHALL render an empty state on the Favorites screen when no
affirmations are currently favorited.

#### Scenario: Favorites screen shows empty state with zero favorites

- GIVEN the user has not favorited any affirmation
- WHEN the user opens the Favorites screen
- THEN an empty state MUST be displayed instead of a list

#### Scenario: Empty state disappears once a favorite exists

- GIVEN the Favorites screen is showing the empty state
- WHEN the user favorites an affirmation while the list is next observed
- THEN the Favorites screen MUST render the list instead of the empty state

### Requirement: Instant Unlike With No Confirmation or Undo

The system SHALL remove a favorite immediately upon an unlike action from
the Favorites list, with no confirmation dialog and no undo affordance
(e.g. snackbar).

#### Scenario: Unliking from the Favorites list removes it immediately

- GIVEN an affirmation is shown in the Favorites list
- WHEN the user triggers the unlike action on that item
- THEN the item MUST be removed from the Favorites list immediately
- AND no confirmation dialog MUST be shown
- AND no undo snackbar or equivalent MUST be shown

#### Scenario: Re-favoriting after an accidental unlike requires the normal gesture

- GIVEN the user has unliked an affirmation from the Favorites list
- WHEN the user wants it back
- THEN the user MUST double-tap the affirmation card again in the
  `AffirmationsScreen` feed to re-favorite it
- AND no undo path MUST exist on the Favorites screen itself

### Requirement: Gesture Scope Limited to AffirmationsScreen Feed

The system SHALL apply the double-tap-to-favorite gesture only to
`AffirmationCard` instances rendered within the `AffirmationsScreen` feed.
`MyAffirmationsScreen` cards MUST NOT gain this gesture in this change.

#### Scenario: Double-tapping a card in MyAffirmationsScreen does nothing

- GIVEN the user is viewing an affirmation card in `MyAffirmationsScreen`
- WHEN the user double-taps that card
- THEN no favorite toggle MUST occur
- AND no favorite state MUST change as a result

### Requirement: Local-Only Scope, No Sync

The system SHALL NOT sync favorite data to Firestore or any remote store in
this change. Favorites are local-only and MAY be lost on reinstall or
device change; this is accepted behavior, not a defect.

#### Scenario: Favorites are not written to Firestore

- GIVEN the user favorites an affirmation
- WHEN the favorite is persisted
- THEN no Firestore write MUST occur for the favorite
- AND the `data-sync` per-user Firestore schema MUST remain unchanged

## Out of Scope

- Firestore sync of favorites (acknowledged future step).
- Visual polish of the double-tap feedback and the Favorites list (heart
  animation, colors, typography) — routed separately through the
  `impeccable` skill. This spec covers functional/structural shape only.
- Favorites in the Glance widget, notifications, or the affirmation feed
  filter.
- Favoriting from `MyAffirmationsScreen`.
- User-controlled ordering/reordering, folders, or export of favorites.
- Entitlement gating — favoriting is available to all users regardless of
  Pro status.
