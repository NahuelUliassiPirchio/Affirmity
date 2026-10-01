# Design: Earn and save two streak healers

## Technical Approach

Implement the approved `specs/streak-healers/spec.md` through bounded, deterministic replay in existing pure Kotlin/TypeScript logic. Keep completion/use repositories, Room/Firestore shapes, Compose routing, and notification consumers. Inventory becomes 0–2; earning progress becomes 0–1. General streak remains activity OR healing; earning requires meditation AND affirmations.

## Architecture Decisions

| Choice | Rejected alternative / tradeoff | Rationale |
|---|---|---|
| Recalculate existing history | Versioned grants/checkpoints require migration | Approved historical requalification; smallest sound change |
| Consume on `healedEpochDay` | Audit-timestamp ordering needs reliable local completion timestamps absent from daily flags | Preserves existing replay and backend `HealerUse` contract |
| Activation day's complete row can start a fresh pair regardless of within-day action order | Excluding that day adds an extra-day delay; timestamp ordering cannot be reconstructed | Explicit calendar-day semantics, consistent across devices |
| Saturating historical consumption | Rejecting orphan uses destroys continuity; debt makes inventory negative | Preserve event identity and healed days despite missing reconstructed credit |
| Computed `healerHeld = healerCount > 0` | Removing Boolean requires unrelated caller rewrites | Widget/UI compatibility with precise count available |

## Interfaces / Contracts

Keep `timeline` and `evaluate` entry points. Replace stored Boolean in `StreakHealerState` with `healerCount: Int` and `pairProgress: Int`, retaining computed `healerHeld`. Proposed TypeScript pure export `deriveHealerInventory(rows, uses, todayEpochDay)` returns a flat interface containing the same count/progress; `shouldFireHealerAlert` and `isHealerExpiringToday` retain Boolean signatures.

Replay unique healed-day keys, ascending from max(today−370, August 4, 2026), inclusive, initialized count/progress zero:

1. A recorded use takes precedence: decrement count with floor zero, reset progress, skip earning that day. Preserve healing even with conflicting activity rows.
2. Otherwise, at capacity reset progress to zero; never bank credit.
3. Otherwise, a complete day advances progress; reaching two grants one and resets progress. Partial/missing days reset progress.

Thus days 1–4 yield 0,1,1,2. A missed/healed day spends 2→1; its next-day activation day's completion starts progress 1, and the following complete day refills 2. Completing before versus after clicking produces identical final replay. Solely healed days never earn. Audit timestamps remain untouched and unused.

Keep existing activation predicate: yesterday is zero-activity, within bounds, preceded by activity/healing or the floor; already healed returns `UsedToday`; otherwise positive inventory permits `Available`.

## Data Flow

```mermaid
sequenceDiagram
    participant UI
    participant App as AffirmityAppState
    participant Repo as Active session repositories
    participant Core as StreakHealerStats
    UI->>App: activateStreakHealer()
    App->>Repo: ready(); fresh completion/use reads
    App->>App: recheck day and session after suspensions
    App->>Core: evaluate fresh history
    Core-->>App: Available / UsedToday / Unavailable
    App->>Repo: recordUse(yesterday) only if Available
    Repo-->>App: combined session flow emission
    App->>Core: replay
    App-->>UI: count and grant feedback
```

Abort stale activation if day/session changed before writing; retain notification cancellation/attribution only after a successful write. This adds no transactional multi-device guarantee.

Inside existing `session.flatMapLatest`, reset initialization and pending celebration on session change. Compare established same-session counts: increases, including 1→2, set `healerJustGranted`; equal/decreasing counts and first emissions do not. Existing acknowledgement and guide precedence remain.

## File Changes

Paths below are relative to their stated roots; all modify existing files.

| Root | Files | Responsibility |
|---|---|---|
| `app/src/main/java/com/pirxhio/affirmity/` | `data/StreakHealerStats.kt`, `data/AffirmityAppState.kt` | Replay, initialization, grant detection, click validation |
| Same root | `ui/progress/ProgressScreen.kt`, `ui/healer/StreakHealerGrantedScreen.kt` | Count in held/available/used states; refreshed grant explanation |
| `app/src/main/res/` | `values/strings.xml`, `values-en/strings.xml` | Localized counts including zero; cap two, AND earning, OR streak, onboarding |
| `app/src/test/java/com/pirxhio/affirmity/data/` | `StreakHealerStatsTest.kt`, `AffirmityAppStateSwapTest.kt` | Domain and session-flow regressions |
| `functions/` | `src/healer.ts`, `test/healer.test.ts`, `test/planner.test.ts`, `test/sendPolicy.test.ts` | Pure parity and remaining-inventory notification regressions |

## Testing Strategy

Strict RED→GREEN→REFACTOR: add failing behavior cases before implementation. Mirror Kotlin/TypeScript vectors: four-day balances; each interruption; long at-cap runs; both same-day action orders; two successive uses; duplicate keys; orphan/conflicting uses; decline/expired windows; inclusive bounds. Flow tests cover second grant, repeat emission, spend, initialization, account switch and pending celebration reset; activation tests cover stale-day/session no-op. Manually inspect localized UI states and guide precedence.

Commands: `./gradlew :app:testDebugUnitTest`; from `functions/`, `npm test -- test/healer.test.ts test/planner.test.ts test/sendPolicy.test.ts`, then `npm run build`. No tests/builds run during design.

## Migration / Rollout

No migration required; minSdk 24 unchanged, no dependencies. Coordinate Android/backend updates: older clients calculate capacity one. Rollback both implementations/copy together, retaining records; prior exact inventory cannot be recovered. Bounded replay can lose old credit. Estimated 350–550 changed lines including tests/copy; tasks must resolve single-PR budget before apply.

## Open Questions

None blocking. Calendar-day ordering is intentional; existing long-lived collector day capture and multi-device enforcement remain limitations.
