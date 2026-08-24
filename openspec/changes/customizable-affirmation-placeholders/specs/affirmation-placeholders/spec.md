# Affirmation Placeholders Specification

## Purpose

Bracket-delimited placeholder tokens (`[token]`) inside affirmation text,
parsed once into structured segments, rendered as visually distinct
tap-to-edit inline inputs, and resolved per-user via a durable, Firestore-synced
override map. Applies uniformly to catalog/bundled and user-created
affirmations, for all users regardless of entitlement.

## Requirements

### Requirement: Bracket Token Parsing

The system SHALL parse `[...]` bracket syntax in affirmation text into an
ordered list of segments (literal text vs. token), performed once at
save/import time and re-derivable via a pure parser with no Android
dependency. Brackets MUST always be treated as token delimiters; the system
MUST NOT support an escape syntax for literal `[` or `]` characters.

#### Scenario: Affirmation with tokens is parsed into segments

- GIVEN authored text `"Gano [10k] [dolares] al [mes]"`
- WHEN the text is parsed
- THEN the result MUST be an ordered list alternating literal and token
  segments matching the source order
- AND each token segment MUST carry the bracketed text as its default value

#### Scenario: Affirmation without brackets parses as a single literal segment

- GIVEN authored text with no `[` or `]` characters
- WHEN the text is parsed
- THEN the result MUST be a single literal segment equal to the original text
- AND rendering MUST be byte-for-byte identical to plain text rendering

#### Scenario: Bracket characters are never treated as literal text

- GIVEN authored text containing `[` or `]`
- WHEN the text is parsed
- THEN every bracket pair MUST be treated as a token delimiter
- AND no escape mechanism MUST be applied or supported

### Requirement: Distinct Token Rendering

The system SHALL render each token segment with visually distinct
background/typography styling, applied consistently in `AffirmationCard` and
`MyAffirmationsScreen` preview surfaces, for both catalog and user-created
affirmations.

#### Scenario: Token renders with distinct styling

- GIVEN an affirmation containing at least one token
- WHEN it is displayed on a rendering surface
- THEN each token segment MUST be visually distinguishable from surrounding
  literal text
- AND literal segments MUST render as plain text unchanged

#### Scenario: Styling is consistent across surfaces

- GIVEN the same affirmation with tokens
- WHEN displayed in `AffirmationsScreen` and in `MyAffirmationsScreen`
- THEN token styling MUST be visually consistent across both surfaces

### Requirement: Tap-to-Edit Inline Input

The system SHALL let a user tap a rendered token to open an inline text
input pre-filled with the token's current effective value (override if
present, else the original authored value).

#### Scenario: Tapping a token opens a pre-filled input

- GIVEN a rendered affirmation with a token whose current value is `"10k"`
- WHEN the user taps that token
- THEN an inline input MUST open pre-filled with `"10k"`

#### Scenario: Tapping an overridden token shows the override

- GIVEN a token with a saved override value `"20k"`
- WHEN the user taps that token
- THEN the inline input MUST be pre-filled with `"20k"`, not the original
  authored value

### Requirement: Override Save and Empty-Input Revert

The system SHALL persist a non-empty edited value as a per-user override
keyed by token, and SHALL revert to the original authored token value when
the user saves an empty input, without persisting an empty override.

#### Scenario: Saving a non-empty value persists an override

- GIVEN the user edits a token's inline input to `"20k"`
- WHEN the user saves
- THEN the rendered affirmation MUST immediately show `"20k"` in place of
  the token
- AND the override MUST be persisted for that user and that token

#### Scenario: Saving an empty value reverts to the original

- GIVEN a token currently overridden to `"20k"`
- WHEN the user opens the inline input, clears it, and saves
- THEN the rendered affirmation MUST show the original authored value
- AND no empty override value MUST be persisted
- AND any previously stored override for that token MUST be removed

### Requirement: Applies to Catalog and User-Created Affirmations

The system SHALL apply token parsing, rendering, and override editing
identically to catalog/bundled affirmations and user-created ("Mis
afirmaciones") affirmations, for both free and Pro accounts.

#### Scenario: Catalog affirmation supports overrides

- GIVEN a bundled catalog affirmation containing a token
- WHEN a free-tier user edits and saves the token
- THEN the override MUST be persisted the same way as for a user-created
  affirmation

#### Scenario: Feature is not Pro-gated

- GIVEN a free-tier (non-Pro) user
- WHEN they tap and edit any token on any affirmation
- THEN the edit MUST succeed without any entitlement check blocking it

### Requirement: Token Identity Drift on Text Edit

The system SHALL derive token identity deterministically from the token's
position and bracketed text at parse time. If a user edits an affirmation's
authored text such that a token's bracketed content changes (e.g. `[10k]` ->
`[20k]`), the system SHALL discard any existing override keyed to the old
token identity and SHALL NOT attempt to remap it to the new token.

#### Scenario: Editing token text drops the stale override

- GIVEN a user-created affirmation with token `[10k]` overridden to `"15k"`
- WHEN the user edits the affirmation text so the token becomes `[20k]`
- THEN the stored override for the old `[10k]` identity MUST be discarded
- AND the token MUST render with the new default value `"20k"` until the
  user sets a new override

#### Scenario: Unmatched override keys never crash rendering

- GIVEN a persisted override map containing a key with no matching token in
  the current parsed template (e.g. after a catalog content update)
- WHEN the affirmation is rendered
- THEN the system MUST ignore the unmatched key
- AND the affirmation MUST render using original token values without
  crashing

### Requirement: Widget and Notification Surfaces Render Original Values

The system SHALL render the original, un-overridden placeholder values on
the Glance widget and in push notification text; these surfaces are out of
scope for override resolution in this change.

#### Scenario: Widget shows original token values

- GIVEN an affirmation with a user override for one of its tokens
- WHEN the affirmation is displayed on the home-screen widget
- THEN the widget MUST render the original authored token value, not the
  override
