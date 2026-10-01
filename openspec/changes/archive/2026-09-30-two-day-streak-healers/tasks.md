# Tasks: Earn and save two streak healers

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 350–550 additions + deletions, including tests/copy |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | Replay/parity → activation/feedback/UI |
| Delivery strategy | single-pr |
| Chain strategy | pending; none approved |

Decision needed before apply: No (resolved: user approved size:exception for one PR)
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

Require approved `size:exception` before apply under single-pr; splitting requires a changed delivery decision. No implementation authorization is implied.

### Suggested Work Units

| Unit | Start → finish | Verification / rollback |
|------|----------------|-------------------------|
| 1 | Existing replay → Kotlin/TypeScript count parity | Domain/notification tests; revert both replay implementations together |
| 2 | Count contract → activation, feedback, localized UI | Session tests/manual inspection; revert orchestration/copy together |

These are proposed review boundaries, not approved PRs. Unit 2 depends on unit 1; retain source records on rollback.

## References and ordering

Requirements in [spec](specs/streak-healers/spec.md): R1=Non-overlapping complete-day pairs; R2=Capacity and fresh progress; R3=Explicit one-day activation; R4=Bounded historical recalculation; R5=Counts, feedback, and parity.

Paths: K=`app/src/main/java/com/pirxhio/affirmity`; T=`app/src/test/java/com/pirxhio/affirmity/data`; S=`app/src/main/res`. Expand these prefixes when applying.

One writer owns each unit. Kotlin/backend replay cycles are parallel-capable logically, but use one writer unless isolated parallel work is approved. All other tasks are sequential. Repeat RED→GREEN→REFACTOR per behavior, never batch all tests first.

## Phase 1: Replay and parity (unit 1)

- [x] 1.1 RED: `T/StreakHealerStatsTest.kt`: balances 0,1,1,2; partial/missing/healed interruptions; cap/no banking; both activation-day orders; successive/duplicate/orphan/conflicting uses; inclusive bounds and OR continuity. [R1–R4]
- [x] 1.2 GREEN: `K/data/StreakHealerStats.kt`: count/pair state, computed held compatibility, healed-day consumption floor zero, preserved identity/history. Depends 1.1. [R1–R4]
- [x] 1.3 REFACTOR: same files; simplify replay only while green, rerun domain tests. Depends 1.2. [R1–R4]
- [x] 1.4 RED: `functions/test/{healer,planner,sendPolicy}.test.ts`: mirror vectors, decline/expiry/repeated activation and remaining-inventory alerts. [R1–R5]
- [x] 1.5 GREEN: `functions/src/healer.ts`: derive inventory and eligibility parity; retain Boolean alert interfaces/source schemas. Depends 1.4. [R1–R5]
- [x] 1.6 REFACTOR: backend replay/tests; rerun focused tests/build and compare Kotlin vectors. Depends 1.3,1.5. [R5]

## Phase 2: Activation and presentation (unit 2)

- [x] 2.1 RED: `T/AffirmityAppStateSwapTest.kt`: 1→2 grant, repeat/spend suppression, initialization/swap/pending reset, stale-day/session activation no-op. Depends 1.6. [R3,R5]
- [x] 2.2 GREEN: `K/data/AffirmityAppState.kt`: fresh activation reads/rechecks, count-based same-session grants, successful-write notification effects. Depends 2.1. [R3,R5]
- [x] 2.3 REFACTOR: same files; retain acknowledgement/guide precedence and rerun session tests. Depends 2.2. [R5]
- [x] 2.4 Update `K/ui/{progress/ProgressScreen,healer/StreakHealerGrantedScreen}.kt`, `S/{values,values-en}/strings.xml`: counts 0–2, cap/AND earning/OR streak/onboarding. Inspect both locales and held/available/used states. Depends 2.3. [R5]

## Phase 3: Verification

- [x] 3.1 Run `./gradlew :app:testDebugUnitTest`, `npm --prefix functions test`, `npm --prefix functions run build`; preserve focused RED/GREEN evidence. Depends 2.4. [R1–R5]
- [x] 3.2 Verify every spec scenario against final behavior; confirm unchanged schemas, bounded-history/multi-device/collector limitations. Depends 3.1. [R1–R5]
- [x] 3.3 Obtain fresh review before commit/PR; measure actual additions/deletions against approved delivery decision. Depends 3.2. [R1–R5]

## Apply authorization

User approved `size:exception` for the entire change in one PR. Delivery strategy: single-pr; chain strategy: none. The original high-risk forecast and suggested split above remain for the review record. No commit/push/PR is authorized in this apply batch.

Fresh review: `review_healer_implementation` PASS, no actionable code defects; `git diff --check` passed. Feature diff: 401 additions + 53 deletions = 454 lines, covered by approved `size:exception`.

## Approved verification remediation (C1/C2)

- [x] 4.1 Replace the possibly-empty planner reflection assertion loop with deterministic production-planner surviving-slot and suppression assertions. Focused planner suite: 35/35 pass.
- [ ] 4.2 Runtime-prove rendered healer counts 0/1/2 in English/Spanish and Unavailable/Available/UsedToday states. Added `ProgressScreenHealerCountTest.kt`; compilation blocker corrected with two invalid import removals; Android 17 AVD fails before rendering in Espresso `InputManager.getInstance`; connected Android 11 Redmi retry stalls at 0/2 and was interrupted (exit 130). No passing runtime result yet.

User approved only C1/C2 remediation. No verify/archive/commit/push/PR in this batch. Root explicitly authorized minimal onboarding compilation repair; completed (two imports removed). Runtime harness/device compatibility remains unresolved.

- [x] 4.3 Authorized prerequisite: remove two invalid top-level assertion imports in `OnboardingScreenGuideGateTest.kt`; assertions are verified Compose 1.10.0 `SemanticsNodeInteraction` member methods. Instrumented Kotlin compilation passes.
