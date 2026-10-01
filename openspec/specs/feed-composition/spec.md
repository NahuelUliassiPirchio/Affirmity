# Feed Composition Specification

## Purpose

Defines which affirmations enter the feed from own, favorites, themes, and enabled user collections.

## Requirements

### Requirement: Additive collection source

Members of every enabled collection MUST be added to the feed on top of themes, favorites, and own sources. Disabled collections MUST contribute nothing.

#### Scenario: Union

- GIVEN themes yield T1, and enabled collection C holds A
- WHEN the feed is built
- THEN it contains T1 and A

#### Scenario: Disabled

- GIVEN C holds A and is disabled, and A is in no other source
- WHEN the feed is built
- THEN A is absent

### Requirement: De-duplication

An affirmation reachable via several sources or several enabled collections MUST appear exactly once.

#### Scenario: Two collections

- GIVEN A is in enabled C1 and C2
- WHEN the feed is built
- THEN A appears once

### Requirement: Hidden and access precedence

Hidden affirmations MUST be excluded even if in an enabled collection. Pro-locked catalog rows MUST be excluded from the feed while remaining in the collection.

#### Scenario: Hidden outranks

- GIVEN A is hidden and in enabled C
- WHEN the feed is built
- THEN A is absent and still a member of C

#### Scenario: Locked after downgrade

- GIVEN a Free user and enabled C holding a Pro catalog row P
- WHEN the feed is built
- THEN P is absent and remains in C

### Requirement: Pre-resolution branch

Before the theme selection is resolved, enabled collections MUST contribute only owned affirmation ids.

#### Scenario: Unresolved themes

- GIVEN themes are unresolved and enabled C holds owned O and catalog K
- WHEN the feed is built
- THEN O is present and K is absent

### Requirement: Feed-sheet validity

The "Your feed" sheet MUST treat at least one enabled collection as a valid feed source, in addition to themes, favorites, and own.

#### Scenario: Collections only

- GIVEN no themes, favorites off, own off, one collection enabled
- WHEN the draft validity is evaluated
- THEN it is valid

#### Scenario: Nothing

- GIVEN no themes, favorites off, own off, no enabled collection
- WHEN evaluated
- THEN it is invalid

### Requirement: Empty feed after last collection off

Turning off any collection chip, including the last enabled one, MUST NOT be blocked. If the feed becomes empty, the app MUST show a dedicated empty state that points the user to collections.

#### Scenario: Last chip off

- GIVEN no themes, favorites off, own off, and one enabled collection
- WHEN the user toggles that chip OFF
- THEN the toggle is applied and the feed is empty
- AND the empty state pointing to collections is shown

#### Scenario: Re-enable

- GIVEN the empty state after the last chip was turned off
- WHEN the user toggles a collection ON
- THEN its members appear in the feed

### Requirement: Live toggles and stable ordering

Collection toggles MUST take effect immediately, independent of the sheet draft/commit flow. Toggling MUST NOT reshuffle the relative order of affirmations already in the feed.

#### Scenario: No reshuffle

- GIVEN a feed ordered [A, B, C]
- WHEN collection C2 adding D is toggled ON, then OFF
- THEN A, B, C keep their relative order both times
