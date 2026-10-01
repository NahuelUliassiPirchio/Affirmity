## Exploration: Two-day streak healers

Recommend replacing the derived Boolean inventory with a count capped at two, awarding on non-overlapping pairs of consecutive complete days. Keep completion rows and the existing healer-use event log as the source of truth, and mirror the algorithm in Android and the notification backend. Product confirmation is needed for historical recalculation and progress at the cap.

### Current State

- `app/src/main/java/com/pirxhio/affirmity/data/StreakHealerStats.kt`: `timeline` distinguishes activity (meditation OR affirmation) from a complete day (meditation AND affirmation). `evaluate` derives `healerHeld: Boolean`; two consecutive complete days grant one, and the cap is one. The qualifying run keeps increasing while held. Simply changing the cap and granting whenever the run is `>= 2` would grant the second healer on day three, violating the requested four-day total.
- Missing days and partial days reset the complete-day run. Healed days preserve the general streak but are not complete days for earning. The general streak advances with either habit; this is separate from healer earning.
- Consumption is replayed on each `healedEpochDay`, clearing the Boolean inventory. Activation is explicit, only on the day immediately after a zero-activity break. A missed activation window retains the healer. The current effective-day predicate includes healed days, so two held healers could cover successive missed days through separate next-day activations; bulk repair is not implemented.
- Eligibility is replayed from `max(today - 370, 2026-08-04)`. Pre-rollout completions can contribute to the visible general streak but cannot grant healers. Inventory and qualifying progress are not persisted independently; the rolling window can eventually omit old grants and alter reconstructed inventory.
- `functions/src/healer.ts`: `shouldFireHealerAlert` duplicates the Boolean inventory simulation; `isHealerExpiringToday` delegates to it. Planner and send-time policy use that result. Backend parity matters after spending the first of two healers.
- `AffirmityAppState.kt` combines completions and uses within the session flow, suppresses celebration on its initial emission, and celebrates only a false-to-true ownership transition. A one-to-two count increase would currently produce no grant screen. `activateStreakHealer` reads fresh data and rechecks eligibility before recording the break day.
- Room and Firestore persist uses keyed by healed day, with an audit timestamp. Duplicate activation of the same day replaces the same row/document. There is no stored balance to migrate. `MigrationPlan.build` copies uses to Firestore; `AffirmityAppState` snapshots uses only within the healer window. Already-migrated accounts skip migration, and sign-out resumes the stale local Room snapshot by existing design.
- Progress copy hard-codes one saved healer; grant explanation and onboarding also hard-code capacity one in both English and Spanish resources. Onboarding incorrectly says the general streak requires both habits, whereas code and the main push-notification specification use OR. Correct that explanation while describing AND specifically for earning.
- Existing Kotlin tests cover the one-healer cap, two-day grant, activation, retention after declining, and rollout floor. Backend tests cover basic availability and expiry. Neither inspected pure-core suite asserts inventory counts, four-day earning, cap progress, or remaining balance after a first spend. No builds or tests were run during exploration.

### Affected Areas

| Area | Files and impact |
|---|---|
| Android domain | `app/src/main/java/com/pirxhio/affirmity/data/StreakHealerStats.kt`: expose inventory count, pair progress, bounded earning, and one-unit consumption; retain a computed ownership predicate if useful to callers. |
| Application orchestration | `app/src/main/java/com/pirxhio/affirmity/data/AffirmityAppState.kt`: initial state, count-increase grant detection, account-swap suppression, fresh activation validation. |
| Progress and explanation | `app/src/main/java/com/pirxhio/affirmity/ui/progress/ProgressScreen.kt`, `app/src/main/java/com/pirxhio/affirmity/ui/healer/StreakHealerGrantedScreen.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`: dynamic inventory copy and accurate earning/cap explanation. |
| Backend eligibility | `functions/src/healer.ts`: identical inventory replay; its planner/send-policy consumers retain Boolean availability interfaces. |
| Regression tests | `app/src/test/java/com/pirxhio/affirmity/data/StreakHealerStatsTest.kt`, `functions/test/healer.test.ts`: paired earning and spending sequences; application-flow tests for second-grant celebration and session initialization. Existing cancellation/attribution tests remain relevant. |
| Integration review | `app/src/main/java/com/pirxhio/affirmity/widget/WeeklyTrackerWidget.kt` consumes `evaluate`; Room/Firestore repositories, `MigrationPlan.kt`, and `firestore.rules` use the existing event shape and need review rather than an automatic schema change. |

Main specs currently include data sync and push notifications but no dedicated healer domain file. A proposal should establish the healer reward contract explicitly rather than infer it from comments.

### Approaches

| Approach | Pros | Cons | Effort |
|---|---|---|---|
| Recompute a capped count from existing history | Smallest change; no Room version, Firestore shape, or migration rewrite; deterministic offline behavior | Reinterprets historical awards/uses; old clients and new backend disagree; preserves bounded-history limitation | Low–medium |
| Replay legacy rules before a fixed effective day, new pair rules afterward | Preserves old earning semantics; clear rollout boundary; same event schema | Requires shared effective day and progress carry/reset decision; replay still bounded; mixed versions still disagree | Medium |
| Persist versioned grants or authoritative balance/checkpoints | Explicit award history; can retain inventory beyond rolling history and support server validation | New persistence/sync and migration design, offline/concurrency rules, broader scope | High |

### Recommendation

Use existing-history replay if historical requalification is acceptable. Simulate a balance of 0–2 and a pair counter of 0–1: a complete day advances progress; a non-complete day resets it; reaching two consumes that pair and grants one only when below capacity. This gives balances 0, 1, 1, 2 after consecutive complete days 1–4. Each persisted use spends one unit, never clears the whole inventory. Preserve next-day activation, general-streak rules, per-habit completion records, and existing event identity.

Recommend discarding earning progress while already at capacity; after a spend, require a fresh pair of complete days. This avoids hidden banked rewards and immediate refill. This is a recommendation, not an approved product requirement. Apply the same sequencing in both languages, including use replay on the healed day rather than its audit timestamp. Avoid silently turning an invalid historical use into a newly valid award; specify how insufficient reconstructed inventory is handled while preserving historical healed-day continuity.

If historical replay changes are unacceptable, choose the effective-day option before proposal. Do not re-run sign-in migration or rewrite all users merely to raise a derived cap.

### Risks

- **Historical reinterpretation:** changing pair cadence and capacity changes reconstructed inventory for existing users; old one-cap history has no grant ledger to recover exactly when a grant occurred. Compare representative historical sequences containing repeated earn/spend cycles before selecting rollout semantics.
- **Mixed versions:** old clients still reconstruct capacity one while the new backend uses two. The shared use schema remains readable, but semantic compatibility is not guaranteed. Coordinate deployment and describe expected behavior for users on older app versions.
- **Bounded replay:** 370-day replay and migration snapshots cannot promise permanent inventory. Retaining that limit keeps scope small; permanent ownership would require a separate design.
- **Celebration and copy:** a Boolean transition misses the second grant; dynamic counts and both resource languages are necessary. Remote flow updates can increase counts too, so preserve initialization/account-swap suppression.
- **Existing timing/concurrency:** the long-lived collection captures its day once, whereas activation reads today's day again; multi-device use writes are idempotent per healed day but not transactional inventory enforcement. These are existing constraints, not requirements to expand this change.

### Ready for Proposal

Yes, after a short product question round. Confirm (1) whether existing post-August-4 history should immediately qualify for two healers or rules begin on a new effective day; (2) whether progress is discarded at capacity or banked; and (3) whether the existing separate next-day activation rule should also allow healing two successive missed days using the two saved healers. Recommended defaults: historical replay, no banked progress, existing activation behavior. No proposal or implementation is authorized by this exploration.
