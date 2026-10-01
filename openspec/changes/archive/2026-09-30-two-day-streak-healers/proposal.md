# Proposal: Earn and save two streak healers

## Intent

Protect against two missed days: each non-overlapping pair of consecutive days completing meditation AND affirmations earns one healer, capped at two. The current single-healer balance limits protection.

## Proposal question round

Confirmed during the product question round: qualifying history counts immediately; progress stops at capacity without banking; each missed day requires its own next-day activation. The user approved these assumptions.

## Scope

### In Scope

- Derive inventory 0–2 and pair progress from existing completion/use records, including qualifying history within existing eligibility bounds.
- Spend one per activation; allow two successive missed days through separate next-day activations. Require a fresh consecutive complete-day pair after spending from full.
- Keep Android and backend eligibility aligned; celebrate the second earned healer and show accurate counts, earning rules, and general-streak copy in existing English/Spanish resources.
- Preserve historical healed-day continuity and source records.

### Out of Scope

- General streak changes: meditation OR affirmations still qualifies.
- Automatic/bulk healing, banked progress, versioned rollout, persistence migration, new dependencies, and concurrent-device enforcement.
- Expanding the existing 370-day replay window or August 4, 2026 eligibility floor.

## Capabilities

### New Capabilities

- `streak-healers`: earning, capacity, consumption, activation, historical replay, grant feedback, and backend availability parity. No dedicated main healer spec exists.

### Modified Capabilities

None. Existing `data-sync` and `push-notifications` requirements retain their contracts.

## Approach

Replay existing history with a capped count and non-overlapping pair progress in Kotlin and TypeScript. Partial/missing days reset progress; healed days preserve continuity without earning. This avoids schema changes. Uses currently replay on the healed day, not their audit timestamp; design must reconcile that sequencing with the fresh-pair outcome and historical uses without sufficient reconstructed inventory.

## Affected Areas

| Area | Impact |
|---|---|
| `app/src/main/java/com/pirxhio/affirmity/data/StreakHealerStats.kt` | Count/pair replay |
| `app/src/main/java/com/pirxhio/affirmity/data/AffirmityAppState.kt` | Activation and celebrations |
| `app/src/main/java/com/pirxhio/affirmity/ui/`, `app/src/main/res/` | Inventory and localized explanations |
| `functions/src/healer.ts`, Android/backend tests | Parity and regressions |

## Risks

- Bounded history may lose old awards; document the retained limitation.
- Mixed client/backend versions disagree; coordinate the current user's updates.
- Single-PR scope may exceed 400 changed lines; tasks must forecast before implementation.

## Rollback Plan

Revert Android/backend logic and copy together. Retain completion/use records; reverting restores single-cap replay, not an exact prior balance.

## Dependencies

No new dependency; retain minSdk 24 compatibility.

## Success Criteria

- [ ] Complete days 1–4 yield balances 0,1,1,2 from empty.
- [ ] Spending leaves one; fresh pairs refill without banked progress.
- [ ] Successive explicit activations work; historical continuity remains.
- [ ] Second grant celebrates; initialization/account changes do not.
- [ ] Android/backend agree; existing localized copy explains OR versus AND.
