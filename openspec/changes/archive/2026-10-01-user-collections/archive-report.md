# Archive Report: user-collections

**Archived**: 2026-10-01
**Mode**: openspec (engram report mirrored)
**Archive status**: INTENTIONAL-WITH-WARNINGS (user-approved: archived with unverified items)

## Summary of delivery

Local-only (Room) user collections: users build named sets of affirmations (owned or catalog) and toggle them as additive feed sources via chips. Includes a Free limit of 2 (Pro unlimited, downgrade keeps existing), enabled toggle with last-used chip ordering, membership cleanup on owned delete and JSON import replace, an "empty feed" state pointing to collections, and feed-sheet validity that counts enabled collections.

## Slices and commits

| Slice | Scope | Commit |
|-------|-------|--------|
| A | Data layer + Room migration v13 to v14 | e4bd3d4 |
| B | AppState integration + feed union | 0d2a1da |
| C | UI: collection picker, chips, empty state | 81a0881 |

Docs commits: df5cdac (change artifacts), 67b58c2 (progress after slice B), bb1c564 (progress after slice C).

## Delivery decision

Single PR with `size:exception` (~1500 changed lines, 29 tasks) chosen by the user over the recommended 3 chained PRs. Work-unit commits per slice. Nothing pushed yet; no PR created yet.

## Verification summary

Verdict: PASS WITH WARNINGS, 0 CRITICAL (5 WARNING, 4 SUGGESTION). Full report: `openspec/changes/user-collections/verify-report.md`.

- 1061 unit tests green, 0 failures
- `assembleDebug` OK; `compileDebugAndroidTestKotlin` OK
- Migration SQL matches exported `14.json`

## UNVERIFIED items accepted by the user

The user explicitly chose to archive now, accepting that these were NOT executed:

- All androidTests: DAO, migration v13 to v14, chips, name dialog (compile-only evidence)
- Task 3.8 manual visual check
- `lintDebug`

This is accepted risk, not a pass. Task checkboxes were not altered.

## Deferred items

- Per-read id-set caching
- Retry after flow `.catch`
- Shared test fixture
- Lock-and-launch boilerplate helper
- Shared AffirmationActionRow
- String key merges
- Stale-name snapshot in delete dialog
- Collection detail screen
- Firestore sync with enabled-flag split

## Known limitations

- Local collections persist across sign-out and account switch on a shared device.
- Collection names leak across accounts on the same device (consequence of the above).

## Rollback note

Migration v14 is additive (two new tables). Before release, rollback is a plain revert. After release, rollback MUST be a forward v15 migration dropping both tables; Room does not support downgrade.

## Spec sync

| Domain | Action | Details |
|--------|--------|---------|
| user-collections | Created | New capability; full spec copied to `openspec/specs/user-collections/spec.md` (7 requirements, 12 scenarios). |
| feed-composition | Created | New capability; full spec copied to `openspec/specs/feed-composition/spec.md` (7 requirements, 9 scenarios). |

No removals or modifications of existing main specs (data-sync, push-notifications, streak-healers untouched); no destructive merge.

## Engram traceability (project affirmity)

- explore #1752
- proposal #1753
- spec #1754
- design #1755
- product decisions #1756
- tasks #1757
- delivery decision #1758
- apply-progress #1759
- verify-report #1760
- archive-report: topic_key `sdd/user-collections/archive-report`

## Scope notes

No git, tests, or builds were run during archive. The orchestrator moves this folder to `openspec/changes/archive/2026-10-01-user-collections`. Unrelated workspace changes and exports were not touched.
