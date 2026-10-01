# Streak Healers Specification

## Purpose

Earn and hold up to two healers, each protecting one missed calendar day through explicit activation.

## Requirements

### Requirement: Non-overlapping complete-day pairs

The system MUST award one healer per non-overlapping pair of consecutive calendar days with meditation AND affirmations completed. Partial, missing, or solely healed days MUST break earning progress. General-streak qualification MUST remain meditation OR affirmations, with healed-day continuity preserved.

#### Scenario: Four consecutive complete days

- GIVEN empty inventory and no earning progress
- WHEN four consecutive complete days occur without spending
- THEN balances after days 1–4 MUST be 0, 1, 1, 2.

#### Scenario: Interrupted pair

- GIVEN a complete day followed by a meditation-only, affirmation-only, missing, or solely healed day
- WHEN the next day is complete
- THEN that day MUST NOT finish an earning pair
- AND either habit or an existing heal MUST still preserve general-streak continuity.

### Requirement: Capacity and fresh progress

Inventory MUST remain between zero and two. Earning progress MUST stop at capacity without banking credit. After spending from full, refilling MUST require a fresh pair of consecutive complete days once capacity is available; complete days credited while full MUST NOT contribute. Same-day activation/completion ordering is a design decision.

#### Scenario: Spending after a long complete run

- GIVEN two healers and additional complete days while full
- WHEN one healer is activated and a fresh consecutive complete-day pair qualifies with capacity available
- THEN activation MUST leave one healer, the first qualifying day MUST leave one, and the second MUST refill to two without using at-cap credit.

### Requirement: Explicit one-day activation

Each activation MUST spend exactly one healer and heal only yesterday's zero-activity day, within existing eligibility bounds, following an active/healed day or at the eligibility floor. An already healed day MUST NOT consume again. No inventory, activity yesterday, or an expired window MUST prevent activation; declining MUST retain inventory.

#### Scenario: Two consecutive missed days

- GIVEN two healers and an active day followed by missed days A and B
- WHEN A is explicitly healed on B and B is explicitly healed on the following day
- THEN balances MUST become one and zero respectively, preserving streak continuity.

#### Scenario: Ineligible or repeated activation

- GIVEN zero inventory, an active yesterday, an older unhealed break, or an already healed yesterday
- WHEN activation is attempted
- THEN no additional heal or consumption MUST occur.

#### Scenario: Declining

- GIVEN an available activation
- WHEN its next-day window closes without activation
- THEN inventory MUST remain unchanged and that missed day MUST remain unhealed.

### Requirement: Bounded historical recalculation

Qualifying history MUST count immediately from max(today minus 370 days, August 4, 2026), inclusively. Earlier completions MUST NOT earn credit. Recalculation MUST preserve source event identity and historical healed-day continuity, without negative inventory when a recorded use lacks reconstructed credit. No persistence migration or rollout versioning SHALL be required.

#### Scenario: History and bounds

- GIVEN four complete days inside the bounds and additional completions outside them
- WHEN inventory is reconstructed without uses
- THEN two healers MUST be available, with no outside earning credit.

#### Scenario: Historical credit shortage

- GIVEN an existing healer-use event without reconstructed credit
- WHEN history is replayed
- THEN its identity and healed-day continuity MUST survive, and inventory MUST remain nonnegative.

### Requirement: Counts, feedback, and parity

Existing English/Spanish UI MUST display accurate inventory and explain capacity, AND earning, and OR streak rules. Each observed grant, including one-to-two, MUST celebrate; initialization/account changes MUST suppress celebrations. Android/backend activation eligibility MUST agree for identical history and calendar day.

#### Scenario: Grant versus initialization

- GIVEN an established account observation or an initializing/switched account
- WHEN inventory increases to two
- THEN only a grant observed within the established account MUST celebrate, and UI counts and Android/backend eligibility MUST agree.
