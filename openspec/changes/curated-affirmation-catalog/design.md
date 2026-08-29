# Design: Curated Affirmation Catalog

## Source-Corpus Facts (measured, not assumed)

Everything below is grounded in a direct read of `/Users/pirxhion/Downloads/affirmations-catalog.v1.json`.
Three measurements changed real design decisions, so they lead:

| Measurement | Value | Design consequence |
|---|---|---|
| `"collectionId":` occurrences | **2712** affirmations | Confirms corpus size. |
| `"universeId":` occurrences | 300 total, minus 226 collections = **74 themes**, **226 collections**, **14 universes** | Taxonomy is 14/74/226/2712. |
| Affirmation record shape (line 6739) | `{ id, collectionId, text, tone, semanticAngle, order, status }` | **There is no `title`/`subtitle` split — only `text`.** `AffirmationEntity` has both. See D8. |
| `access` block location (line 2231) | On **`collections`**, never on `themes` | The new `ContentType` constant must be **collection**-scoped, not theme-scoped. Corrects the proposal. See D5. |
| `"tier": "pro"` | **150 of 226** collections | ~66% of the catalog is Pro-gated. Free users see ~34% of any group. Real monetization surface. |
| `"rewardedUnlockHours": null` | 151 → 76 free + **75 pro-with-null**; **75 pro collections carry a non-null hour window** (all `24`) | No existing `AdUnlockPolicy` expresses a repeating window, so this change **adds `TIMED_REPEATABLE`** (user decision, closed). See D16. |
| Literal `[` or `]` inside any `"text"` value | **ZERO** | Regex `"text": ".*[\[\]].*"` → *no matches*. Same for `title`/`description`/`coreNeed`. The 1611 bracket-containing lines are all JSON array syntax. **The corpus is already clean.** See D11 and Open Question 3. |
| Affirmation `id` format | `self_worth.feeling_enough.intrinsic_worth.001` | Already unique, already stable, already deterministic. See D3. |

Two of these invalidate parts of the proposal. Both corrections are HOW-level and are argued in place.

## Technical Approach

Five layers, each with a hard boundary. The organizing principle is the proposal's own:
**catalog and user data never share a storage path**, so "not mine, not editable" is a type-level
fact rather than a runtime flag. The read model unifies them one layer *above* the repositories.

1. **Build-time transform layer** (`tools/catalog/`, JVM-free). The source JSON is transformed once
   into two committed artifacts: a bundled affirmations asset and a compiled Kotlin taxonomy. The
   bracket gate runs here and fails loudly (D11).
2. **Bundled seed + Room cache layer.** `catalog_affirmations` and `catalog_affirmation_overrides`,
   additive `MIGRATION_8_9`. The catalog is seeded from the **bundled asset**, not from the network
   (D2) — Firestore is a delta/refresh channel that costs **one** document read per check, not 2712.
3. **Access layer.** A new `ContentType.AFFIRMATION_COLLECTION` plus a pure
   `AccessDecision` combinator, so effective access is `most-restrictive(group, collection)` — the
   group gate stays an invariant instead of becoming overridable (D6). Plus a new
   `AdUnlockPolicy.TIMED_REPEATABLE` with its **own** durable store, so the existing
   create-only/non-repeatable guarantee on `ONE_TIME_TRIAL` is not weakened to accommodate it (D16).
4. **Read-model layer.** `AffirmityAppState` gains a second, catalog-backed list. `filteredAffirmations`
   and `favoriteAffirmations` both read a merged view, so **`FavoriteAffirmationDao` needs no change
   at all** (D10) and `AffirmationsScreen` needs no new parameter (D9).
5. **Publishing layer** (`functions/tools/seedCatalog.ts`). One-time, admin-privileged, Node.
   Chunked, marker-last, idempotent — the `MigrationPlan.chunkWithMarkerLast` *discipline*, in the
   only runtime that can actually execute it with write privileges (D12).

Satisfies `specs/affirmation-catalog/` and `specs/catalog-token-overrides/`.

## Architecture Decisions

| Decision | Choice | Alternatives rejected | Rationale |
|---|---|---|---|
| **D1. Firestore shape is FLAT, with taxonomy as sibling collections** | `catalogAffirmations/{catalogAffirmationId}` holding `{ text, groupId, themeId, collectionId, sortOrder, status, catalogVersion }`. Taxonomy in three tiny sibling collections: `catalogUniverses/{universeId}`, `catalogThemes/{themeId}`, `catalogCollections/{collectionId}`. Marker at `catalogMeta/version`. | (a) Nested `catalogAffirmations/{universeId}/themes/{themeId}/collections/{collectionId}/affirmations/{id}`; (b) one document per collection holding an array of its ~12 affirmations. | **(a) buys nothing and costs a query.** Firestore bills per *document returned*, never per path depth, so nesting saves zero reads. What it costs is real: fetching one universe's 194 affirmations requires a `collectionGroup("affirmations")` query plus a composite index, and every read needs the full ancestor chain, which the client does not have until it has already read the taxonomy. Flat + a `groupId` field answers the same question with `whereEqualTo("groupId", id)` and a single-field auto-index. It also matches `FirestorePaths`' entire existing convention: **every one of its 13 builders is a flat `parent/{id}/child/{id}` string** — there is no 4-level path anywhere in the file, and introducing one for the first time on the *shared* collection is the worst place to start. (b) is the genuinely cheap option (226 reads instead of 2712) and is rejected only because D2 makes the read count nearly irrelevant, while array-documents lose per-affirmation delta granularity and hit the 1 MiB doc ceiling on the largest collections. **Taxonomy lives in Firestore too** — 14+74+226 = 314 small docs — so a future content update can retier a collection without an app release. |
| **D2. Bundled-asset-first. Firestore is a delta channel, not the cold-start path.** | `app/src/main/assets/catalog.v1.json` (generated, committed, ~550 KB raw / ~150 KB in the APK) is the source of truth at install. `CatalogSeeder` parses it and bulk-inserts into Room in **one transaction** on first launch or whenever the bundled `catalogVersion` exceeds the seeded one. Firestore is consulted only for `catalogMeta/version` (**1 doc read**); a higher remote version triggers a `whereGreaterThan("catalogVersion", local)` delta fetch. | (a) Remote-first: fetch all 2712 docs on first launch; (b) Firestore offline persistence as the cache. | **This closes the proposal's open decision, and the numbers are not close.** Remote-first pays 2712 document reads *per user per install* and — far worse — makes the very first launch a network-blocked empty screen, on an app whose entire value proposition renders in under a second. It also breaks the "catalog renders offline" success criterion for anyone who installs on a plane. Bundled-first costs ~150 KB of APK for a **static corpus that changes maybe twice a year**, gives an instant, offline, deterministic cold start, and drops steady-state cost to one document read per refresh check. (b) was rejected because Firestore's offline cache is populated *by* reads — it does not avoid the first 2712, it only avoids the second. **Consequence worth stating plainly: for v1.0.0 the Firestore catalog is not on any critical path.** Shipping is not blocked on the seeder having run. That is a sequencing gift to `sdd-tasks`, not a scope cut — the collection, the rules, and the seeder all still ship. |
| **D3. Catalog id = `cat_` + the source dotted id, verbatim** | `cat_self_worth.feeling_enough.intrinsic_worth.001`. `const val CATALOG_ID_PREFIX = "cat_"`. Max observed length ~62 chars. | The proposal's `cat_{universeSlug}_{themeSlug}_{nnn}`; a fresh sequential numbering; a hash of the text. | **Correcting the proposal.** `cat_{universe}_{theme}_{nnn}` *loses the collection level*: `self_worth.feeling_enough` contains 6 collections whose per-collection `nnn` all restart at 001, so the scheme either collides or requires a renumbering pass — inventing a numbering authority for ids that must be permanent, to replace ids that are **already permanent, already unique across all 2712, and already deterministic**. It also destroys traceability: with the verbatim scheme, any future content diff maps 1:1 back to the source file by stripping four characters, which is exactly what a content-update channel needs. Rejecting ChatGPT's dotted ids *as the app's ID convention* (exploration's correct call) is not the same as refusing to use them *as an opaque suffix* — `UUID.randomUUID()` remains untouched for every user-owned row, which is the actual constraint. **Collision proof (unit-tested, three independent arguments):** (i) `UUID.toString()` emits only `[0-9a-f-]`, so no UUID can contain `_` or `.` — the first four characters `cat_` are already disqualifying; (ii) `PERSONALIZADAS_GROUP_ID` and the three legacy group ids are *group* ids, never affirmation ids, and none of the 14 universe ids equals any of them; (iii) the 2712 source ids are unique by construction, verified by a set-size assertion in the generator. **Firestore-legality:** no `/`, not `.` or `..`, does not match `__.*__`, well under the 1500-byte id limit. |
| **D4. Backgrounds are DERIVED, never stored** | `ui/affirmations/CatalogBackgrounds.kt`: `fun forCatalogAffirmation(groupId: String, id: String): AffirmationBackground.Color` — a per-universe 4-shade palette, indexed by a stable hash of `id`. Zero background columns in `catalog_affirmations`. | Two `background*` columns as the proposal's Affected-Areas table specifies; a background field in the source JSON. | **The source has no background field at all** (verified: the affirmation record is `id/collectionId/text/tone/semanticAngle/order/status`), so a stored background would be data the import *invents* and then freezes into 2712 rows plus 2712 Firestore docs, in a schema where re-theming the app later means a migration. Deriving it makes the palette a pure, unit-testable function of `(groupId, id)`: deterministic per affirmation (the same card always looks the same), varied within a group, and re-skinnable in one file with no data touched. This deviates from the proposal's `CatalogAffirmationEntity` row spec — deliberately, and it removes two columns and ~40 KB from the bundled asset. |
| **D5. New constant is `AFFIRMATION_COLLECTION("affirmationCollection")` — collection-scoped, not theme-scoped** | Appended to `ContentType` in `access/ContentKey.kt` (**not** a new `ContentType.kt` — the enum lives inside `ContentKey.kt:6-14`; the file the task brief assumed does not exist). | The proposal's `affirmationTheme`; a per-affirmation `AFFIRMATION` type. | **Correcting the proposal, on data.** The source declares `access { tier, rewardedUnlockHours }` on **`collections`** (line 2231) and nowhere else — `themes` carry only `conceptTagIds`/`desiredStateIds`/`order`. A theme-scoped constant would have nothing to read, so the import would have to *synthesize* a theme tier by aggregating its collections, throwing away 226 real editorial decisions to manufacture 74 fake ones. Per-affirmation gating was rejected for the opposite reason: 2712 `ContentKey`s is an ad-unlock table the size of the catalog. **`wireName` invariant (`ContentKey.kt:3-5`, `:22`, `:26-27`): `affirmationCollection` contains no `_`.** ✓ Round-trip on the worst realistic input: `ContentKey(AFFIRMATION_COLLECTION, "self_worth.feeling_enough.intrinsic_worth").storageKey` = `"affirmationCollection_self_worth.feeling_enough.intrinsic_worth"`; `substringBefore('_')` = `"affirmationCollection"` ✓; `substringAfter('_')` = the full dotted id, dots intact ✓. `fromWireName` is exact equality, so `affirmationCollection` cannot shadow the existing `affirmationGroup`. **`firestore.rules:71` also holds**: `contentKey == contentType + '_' + contentId` is satisfied, and a doc id containing dots (but not *being* `.`/`..`) is legal. |
| **D6. Effective access = MOST-RESTRICTIVE(group, collection), via a pure combinator. `resolveAccess` itself is NOT modified.** | New `access/AccessCombination.kt`: `fun mostRestrictive(a: AccessDecision, b: AccessDecision): AccessDecision`. New facade `ui/groups/CatalogAccessPolicy.kt`: `catalogAccessDecision(group, collection, tier, grants, nowMillis)` = `Unlocked` if `group.alwaysSelected`, else `mostRestrictive(groupAccessDecision(...), resolveAccess(ContentKey(AFFIRMATION_COLLECTION, collection.id), collection.access, ...))`. A collection with no declared access contributes `Unlocked` (pure group inheritance). | (a) The source's own `accessInheritance` model — collection access **overrides** the group's; (b) changing `resolveAccess`'s signature to take two `ContentAccess` values. | **(a) is a security hole, not a preference.** Under override, a `free` collection inside a Pro group is `Unlocked`, so its affirmations become reachable through the feed merge, through Favorites, and through any future deep link — while the selector still renders the group as locked. The group gate would stop being an invariant and become a *suggestion*, silently, at 226 places. 76 free collections are spread across all 14 universes, so this is not hypothetical. Most-restrictive is the only rule under which "a locked group leaks nothing" survives as a provable property. (b) was rejected because `resolveAccess` is pure, total, and already unit-tested as *the* single decision point; widening it forces every existing single-level caller (`groupAccessDecision`, `meditationAccessDecision`, `customAffirmationDecision`) to pass a second argument they do not have. A separate combinator over its **output** composes instead of mutating, and yields a 4×4 truth table that is exhaustively testable. **Combinator precedence (in order): `LockedNeedsPro` is absorbing → `LockedAdUnlockable` beats any unlocked state → `UnlockedByAd` beats `Unlocked` (provenance is load-bearing for `AccessDecision`'s own doc comment) → `Unlocked`.** When both sides offer an ad, the stricter *policy* wins on the total order **`ONE_TIME_TRIAL` > `TIMED_REPEATABLE` > `PER_USE`** (once-ever is stricter than once-per-window, which is stricter than always-re-earnable). |
| **D7. Locked catalog content is filtered OUT of the feed, not rendered locked** | `filteredAffirmations` excludes any catalog row whose `catalogAccessDecision` is not `isUnlocked`. | Rendering locked cards with a paywall overlay in the pager. | A vertical pager of affirmations is a *flow* surface; interleaving 66% locked cards into it would make the core loop unusable for Free users and turn the paywall into an interruption rather than an offer. The upsell surface already exists and is the right one: the selector sheet's locked rows with their `Lock`/`PlayCircle` CTAs. **Consequence to state honestly: a Free user selecting a group gets only its free collections — roughly a third of its content.** That is the intended monetization shape, and it is the direct reason D6 must be evaluated per affirmation at read time rather than once per group. |
| **D8. Catalog `text` maps to `title`, `subtitle` is empty** | `CatalogAffirmationEntity.text` → `Affirmation(title = text, subtitle = "")`. One column, not two. | Splitting `text` on its first sentence boundary into title/subtitle; storing `tone`/`semanticAngle` as the subtitle. | The source has exactly one authored string per affirmation. Sentence-splitting would fabricate an editorial structure the author did not write, and on a corpus where most entries are a single sentence it would produce an empty subtitle anyway. Rendering `tone`/`semanticAngle` (`"powerful"`, `"identity"`) as user-facing copy would leak internal taxonomy vocabulary into the UI in English, in a Spanish-only product. **This is a UI verification item, not a free lunch**: `AffirmationCard` must be checked to render a blank subtitle without a layout gap — it is the first content in the app with an empty subtitle. Carried as an explicit acceptance task. |
| **D9. The catalog *cache* lives OUTSIDE `DataSession`; catalog *overrides* live INSIDE it. `AffirmationsScreen` gains no parameter** | `CatalogAffirmationRepository` (read-only) is a direct `AffirmityAppState` constructor param with a `NoOp` default, a sibling of `favorites`. `CatalogOverrideRepository` is a **`DataSession` member**, with `Room` and `Firestore` implementations swapped on auth — a direct consequence of shipping the full override sync surface (Open Question 1, CLOSED). | Putting **both** in `DataSession`; putting **both** outside it (this design's previous position, valid only while overrides were Room-only). | Same argument that settled favorites' D4 — and the split falls out of it exactly. `DataSession.Remote` is documented as "backed **exclusively** by Firestore" and exists so a sign-in/sign-out swap is atomic across per-user stores. The catalog cache is **not per-user** — byte-identical signed-out and signed-in — so it has no swap semantics, and putting it in the bundle would make the type lie while pointlessly cancelling and re-subscribing a 2712-row Room flow on every auth transition. **Overrides are the mirror image**: `users/{uid}/catalogOverrides` is per-user by definition, so leaving it outside the session would mean one user's edits surviving a sign-out into another account's session — the precise bug `DataSession` exists to make unrepresentable. The two halves of "the catalog" genuinely have opposite lifetimes, and the type system should say so. `AffirmationsScreen` already takes `List<Affirmation>`; catalog rows arrive as ordinary `Affirmation`s, so **the feed composable is untouched**. |
| **D10. Favorites merge happens in `AffirmityAppState`, with ZERO DAO/SQL change** | `private val allAffirmations get() = affirmations + catalogAffirmations`; `favoriteAffirmations` derives from `allAffirmations.associateBy { it.id }`. `FavoriteAffirmationDao`, `FavoriteAffirmationEntity`, and `FavoriteAffirmationRepository` are **not modified**. | A `LEFT JOIN`/`UNION ALL` DAO query across `affirmations` and `catalog_affirmations`; a `source` column on `favorite_affirmations`. | This is the payoff of favorites' D5 ("no foreign key") and D7 ("derived by intersection, never stored") — that design was built for exactly this and the debt now comes due as zero. A cross-table SQL merge is also **wrong for signed-in users**: when the session is `Remote`, personal affirmations are not in Room at all, so a `UNION` over `affirmations` would silently return an empty personal half. The id spaces are disjoint by D3, so a single `associateBy` over the concatenated list resolves both spaces, and an id in neither still `mapNotNull`s away — "orphans never render" survives unchanged across two ID spaces. Unfiltered by access on purpose: **a favorite the user made while Pro stays visible after a downgrade.** Flagged as a product question (Open Question 4) rather than silently decided the other way. |
| **D11. The bracket gate is a VERIFICATION gate, not a rewrite step — because the corpus is already clean** | `CatalogTextSanitizer.findIllegalBrackets(text): List<Int>` (pure, `app/src/main/java/.../data/catalog/`). The generator **fails the build** on any hit; a JVM unit test re-asserts it over the committed asset. **No rewrite/strip logic is written.** | The proposal's "strip/rewrite every hit"; extending `AffirmationTemplateParser` with an escape syntax. | **Measured, not assumed: `"text": ".*[\[\]].*"` returns zero matches across all 2712 affirmations, and the same holds for every `title`, `description`, and `coreNeed`.** The 1611 bracket-bearing lines are JSON array punctuation, which is precisely why a naive `rg '\['` is worthless here and a field-aware scan is mandatory. Writing rewrite logic for a set that is empty would ship untested, unexercised code on the highest-risk path in the change. So the gate stays — it is cheap and it must survive future content drops — but it is **fail-closed, not fix-forward**: if a future `catalog.v2.json` introduces a bracket, the build breaks and a human edits the content. `AffirmationTemplateParser` is untouched, as scoped. **This downgrades the proposal's top risk from High to Low on measurement**, and `sdd-tasks` should re-budget slice 1 accordingly. |
| **D12. The Firestore seeder is a TypeScript script in `functions/tools/`, NOT a Kotlin `CatalogSeedPlan.kt`** | `functions/tools/seedCatalog.ts`, run once by the developer as `cd functions && npx tsx tools/seedCatalog.ts --catalog ../app/src/main/assets/catalog.v1.json`, authenticating via `GOOGLE_APPLICATION_CREDENTIALS`. Chunks at 450 ops/batch, taxonomy first, affirmations next, `catalogMeta/version` **last**. Idempotent `set(..., { merge: true })`. Covered by `vitest` against the Firestore emulator. | The proposal's `data/remote/CatalogSeedPlan.kt`; a Gradle JVM task; a debug-only admin code path in the app. | **A Kotlin planner would have no runtime that can execute it.** `firestore.rules` denies all client writes to `catalogAffirmations` (correctly), so the app can never run the plan; a Gradle task would need the firebase-admin *Java* SDK pulled into a single-module Android build purely for a script; and a debug-only in-app admin path is the one option that is actively dangerous — it means shipping a code path whose entire purpose is writing the shared collection. Meanwhile `functions/` **already has** `firebase-admin ^14.2.0`, `typescript`, `vitest`, and a working `test:rules` emulator harness (`functions/package.json:14,18,23-28`). The seeder is ~80 lines in an environment that already runs privileged writes and already has tests. What is reused from `MigrationPlan.kt:79-92` is the **discipline** — chunk, marker strictly last, idempotent merge — not the code, which is what the exploration recommended. Per D2 this script is **not** on the release critical path. **This deviates from the proposal's Affected-Areas row for `data/remote/CatalogSeedPlan.kt`; that file is not created.** |
| **D13. The Room seed marker lives in DataStore and is written AFTER the transaction** | `CatalogPreferences.seededCatalogVersion: Flow<String?>` (DataStore, sibling of `AffirmationGroupPreferences`). `CatalogSeeder`: `@Transaction { deleteAll(); insertAll(rows) }` **then** `savedSeededVersion = bundledVersion`. | A third `catalog_meta` Room table inside the same transaction. | The same marker-last discipline as D12 and `MigrationPlan`, applied locally. A crash between the committed transaction and the DataStore write costs one redundant re-seed on next launch — the transaction is a full replace, so re-running is a no-op by construction. The inverse order is the unsafe one: a marker written first turns a crashed seed into a **permanently half-empty catalog** with no signal that anything is wrong. A `catalog_meta` table would make it atomic, but at the price of a third table in `MIGRATION_8_9` and a schema surface for one string, to defend against a failure mode whose worst case is already benign and self-healing. |
| **D14. `setTokenOverride` routes on the id prefix; `Affirmation` gains a typed `source` for the UI** | `Affirmation(..., val source: AffirmationSource = AffirmationSource.OWNED)` with `enum class AffirmationSource { OWNED, CATALOG }`. `setTokenOverride` branches on `affirmationId.startsWith(CATALOG_ID_PREFIX)`. `removeAffirmation` **returns early** on a catalog id. | A single prefix check everywhere; a boolean `isReadOnly` flag. | Two consumers with different needs. Write routing must key off the **storage-level ground truth**, which is the id prefix (D3) — a presentation flag could drift from the row's actual home and send a catalog write into `users/{uid}/affirmations`. The UI, by contrast, should never sniff strings: `source == CATALOG` is what hides a delete affordance. `removeAffirmation`'s guard is the load-bearing one and it is asserted, not assumed: without it, `ready().affirmations.deleteById("cat_…")` is a silent no-op against Room but a **real per-user Firestore document write** when the session is `Remote`, quietly creating a tombstone in a collection that should never contain catalog ids. |
| **D15. No `MIGRATION_9_8`** | Ship `MIGRATION_8_9` only; `fallbackToDestructiveMigrationOnDowngrade` stays disabled. Post-release rollback is the *partial* rollback: drop the 14 groups from `selectableAffirmationGroups()`, keep schema and repositories. | The proposal's Rollback item 2 option "keep `MIGRATION_8_9` registered on the rollback build". | **Resolving the proposal's open item in the negative, for the same reason the favorites design resolved 7→8.** Room selects a path from the *on-disk* version to its *compiled* version; on a build compiled at `version = 8`, a registered `MIGRATION_8_9` is never consulted — Room needs a `9 → 8` path and throws on open. So that option is a no-op as written. A real `DROP TABLE` downgrade is also *less* safe here than it looks: it destroys `catalog_affirmation_overrides`, which is the only user-authored data in this change. The partial rollback makes the catalog unreachable with zero data loss and is a three-line change. |
| **D16. New `AdUnlockPolicy.TIMED_REPEATABLE`, with the window on `ContentAccess` and a SEPARATE durable store** | Enum stays payload-free: `TIMED_REPEATABLE` is a bare constant. The duration lives on the *content*: `ContentAccess(requiredTier, adUnlock, unlockWindowHours: Int? = null)`, with `ProOrAdTimed(hours)`. Grants persist to a **new** `timed_ad_unlock` Room table and a **new** `users/{uid}/timedUnlocks/{contentKey}` Firestore path — `ad_unlock` / `users/{uid}/adUnlocks` are **byte-identical to today**. `AdUnlockState` gains a third field `timedUnlocks`. | (a) `TIMED_REPEATABLE(hours: Int)` as an enum with a constructor arg; (b) reuse the existing `adUnlocks` store and relax it to allow updates; (c) rules discriminating by `contentType == 'affirmationCollection'` to allow update only there; (d) the previously-recommended `ONE_TIME_TRIAL` mapping. | **(a) is not expressible.** A Kotlin enum constant is a singleton, so `TIMED_REPEATABLE(24)` would freeze one duration into the type — a second window (12h, 48h) would need a second constant. Duration is a property of the *content's offer*, not of the *policy kind*, so it belongs on `ContentAccess`, which is already the per-instance carrier. All 75 collections declare `24` today, but nothing in the design assumes it. **(b) and (c) are the real trap, and they are why this decision is bigger than one enum case.** Both storage layers are deliberately **create-only**: `AdUnlockDao.insertIfAbsent` is `@Insert(onConflict = IGNORE)` and `firestore.rules:77` is `allow update, delete: if false`, both documented as *the* proof that a spent `ONE_TIME_TRIAL` can never be re-earned or back-dated by a modified client. Re-earning a timed window is, by definition, an overwrite. Relaxing that rule for everyone (b) converts a structural guarantee into a rules expression and silently makes every meditation trial repeatable. (c) keeps the guarantee but encodes a `contentType → policy` table inside `firestore.rules`, which no compiler checks and which rots the first time an `affirmationCollection` ships as `ONE_TIME_TRIAL`. A separate path keeps "a durable trial record is immutable" as a property of *where the byte lives* — the same reasoning as D9's "catalog and user data never share a storage path", applied to grants. **Cost is genuinely small**: `AdUnlockRecord.expiresAtMillis` and `hasExpired(now)` already exist, the Firestore mapper already round-trips `expiresAtMillis`, and the new table is one extra `CREATE TABLE` inside the `MIGRATION_8_9` we are already shipping — so **zero additional migrations**. **`resolveAccess`'s new branch differs from `ONE_TIME_TRIAL` in exactly one line**: an expired record returns `LockedAdUnlockable(TIMED_REPEATABLE)` (re-offer the ad) instead of `LockedNeedsPro` (spent forever). **(d) is what the user rejected**; the honest downside of the conservative mapping was that a 24h-window collection would unlock once in a lifetime, contradicting the content author's declared intent for 75 of 226 collections. |
| **D17. The 3 legacy groups are DELETED outright — no alias, no migration, no fallback** | `bienestar`, `autocuidado`, `fuerza_de_voluntad` removed from `defaultAffirmationGroups()`, which becomes exactly `catalogUniverseGroups()` (14). Their 6 string resources are deleted from `values/` and `values-en/`. `resolveSelectedGroupIds`'s existing "drop unknown ids" behavior is kept, and **D18 adds the thematic-emptiness fallback that makes the recovery tier-independent**. | (a) Keep them contentless (the previous design's position); (b) hidden-but-honored — out of `selectableAffirmationGroups()` yet still accepted by `resolveSelectedGroupIds`; (c) alias each to the nearest universe. | **User decision, explicit and recorded**: the legacy groups "were just made to have something to build the app with", and no real user data exists. That removes the *only* argument the previous design had for keeping them — the risk of silently dropping a live persisted selection. With no live selections to protect, (b) and (c) are pure carrying cost, and (a) leaves three permanently empty rows next to 14 stocked ones, which is worse UX than the problem this change set out to fix. **The safety net is waived deliberately, not overlooked:** a device that somehow holds `{"bienestar"}` gets those ids filtered out by `resolveSelectedGroupIds` and then re-seeded from `defaultThematicIds`, so the user lands on a valid default selection rather than an empty feed. **Correction carried into D18:** the previous revision credited that recovery to `EntitlementResolution.deselectLockedGroups`, but that call is guarded by `if (entitlement.tier == AccessTier.FREE)` (`AffirmityAppState.kt:809`), so a **Pro** user with a legacy-only selection would have landed on a thematically empty feed. D18 moves the invariant into the pure resolver, which closes that hole for both tiers. That is an acceptable outcome, not a crash. **`personalizadas` is NOT affected** — `PERSONALIZADAS_GROUP` is defined separately, is `alwaysSelected`/`isThematic = false`, and is prepended by `selectableAffirmationGroups()`; nothing in this decision touches it. **The real cost is test fixtures, not production code** (see Testing Strategy): four suites currently source their Free/Pro/PER_USE group fixtures from `defaultAffirmationGroups()` by literal id and will fail to resolve. |
| **D18. Fresh installs default to ALL 14 universes selected — and the minimum-selection invariant moves INTO `resolveSelectedGroupIds`** | `defaultThematicGroupIds` (`AffirmityAppState.kt:1289-1291`) becomes `defaultAffirmationGroups().filter { it.isThematic && it.access.requiredTier == AccessTier.FREE }` — the tier condition is retained purely as a **guard**, not as the selector, and `isThematic` is added so the product default is a declaration rather than a coincidence. Post-D17 this evaluates to **all 14**. Separately, `resolveSelectedGroupIds` gains the thematic-emptiness fallback it currently lacks. | (a) Opt-in empty state — fresh install starts with `personalizadas` only; (b) a curated subset (3-5 "starter" universes); (c) leave the existing tier filter untouched and rely on it evaluating to 14 by accident. | **User decision, explicit: all 14 selected on a fresh install.** (a) and (b) are rejected on product grounds — the change ships 2712 affirmations and an empty or 4-universe first feed would hide the entire point of it behind a selector the user has no reason to open. **The important finding is that (c) is nearly a no-op and that is exactly what makes it dangerous.** The existing filter `access.requiredTier == FREE` already yields all 14 once every universe group is declared `Free` (see the CatalogTaxonomy note), so "default to 14" ships whether or not anyone decides it. Encoding the decision as `isThematic` makes it a stated default; keeping the tier condition preserves the real invariant — **the fresh-install default must never contain a group `deselectLockedGroups` would immediately strip**, which would produce a visible flicker from 15 selected to some subset on the first entitlement emission. **This is a NEW-INSTALL-ONLY default and that is structural, not a promise:** `resolveSelectedGroupIds` (`AffirmityAppState.kt:199-206`) consults `defaultThematicIds` **only** via `persisted?.filter{...} ?: defaultThematicIds`, and `GroupSelectionPreferences.observeSelectedGroupIds()` returns `null` **only** when the DataStore key was never written (its KDoc says so explicitly, and `AffirmationGroupPreferences.kt:31-32` is a bare `it[SELECTED_GROUP_IDS]` read). Any user who has ever committed a selection has a non-null set and never reaches the `?:`. **The second half is a real bug fix, not polish.** Today an existing device holding `{"personalizadas","bienestar"}` filters down to `{"personalizadas"}`, and the recovery the previous revision relied on — `deselectLockedGroups`' `cleaned + defaultThematicIds` fallback — lives inside `if (entitlement.tier == AccessTier.FREE)` (`AffirmityAppState.kt:809`). **A PRO user therefore never runs it and lands on a thematically empty selection, i.e. a feed of custom affirmations only.** Moving the same invariant into the pure resolver (`if (resolved.none { it != PERSONALIZADAS_GROUP_ID }) defaultThematicIds else resolved`, then `+ PERSONALIZADAS_GROUP_ID`) makes recovery tier-independent, makes the fresh-install path and the stale-selection path the *same* code path, and is provable in the existing `ResolveSelectedGroupIdsTest` with no Android. It cannot touch a healthy selection: any surviving thematic id short-circuits the fallback. A persisted thematic-empty set is unreachable through the UI (`isDraftSelectionValid` requires ≥1 non-`personalizadas` id), so the fallback does not override a deliberate user choice. |
| **D19. Partial-lock badge = a NEW `GroupBadge.PARTIALLY_LOCKED`, fed by a GENERATED static set narrowed at runtime. `deriveBadge` is NOT modified.** | New constant `GroupBadge.PARTIALLY_LOCKED` (`ui/groups/AffirmationGroup.kt:19`). `deriveBadge` (`ui/groups/GroupAccessPolicy.kt:60-67`) stays **byte-identical**; a catalog-aware wrapper `deriveCatalogBadge(group, decision, isPartiallyLocked)` in `ui/groups/CatalogAccessPolicy.kt` falls back to the new badge only when `deriveBadge` returned `null`. Membership comes from `partiallyLockedGroupIds(tier, grants, nowMillis)`, seeded by the generated constant `CATALOG_GATED_GROUP_IDS`. | (a) Widen `deriveBadge`'s signature with a third parameter; (b) compute the flag live per row inside `AffirmationGroupSelectableRow`; (c) a static `hasProCollections: Boolean` on `AffirmationGroup`; (d) reuse `GroupBadge.PREMIUM` for the partial case; (e) defer the indicator (previous revision's position). | **(e) is what the user rejected**, and correctly: declaring all 14 universes `Free` at the group level makes `deriveBadge` return `null` for every one of them, so ~66% Pro-gated content would render with no visual signal whatsoever. **(d) is the tempting wrong answer.** A `PARTIALLY_LOCKED` row is still `isToggleable` and still `!isLocked` — the user *can* select it and *will* get content from it. Painting it with the same Premium badge a fully-locked row wears says "you cannot have this", which is false, and it makes the two states indistinguishable at exactly the moment the distinction is the whole point. It needs its own visual. **(a) is rejected on blast radius**: `deriveBadge` is the shipped group-badge rule with 8 existing assertions in `GroupAccessPolicyTest`, one call site (`AffirmationGroupSelectorSheet.kt:213`), and a documented mirror in `MeditationAccessPolicy.kt:43` — widening it churns all of that for a catalog-only concern and invites the meditation mirror to drift. A wrapper keeps the shipped rule regression-testable byte-for-byte and puts the new logic in the file D6 already creates. **(c) is a type-level lie**: partial lock is *user-dependent* (a Pro user has none, a Free user with an ad grant may have fewer), so a static boolean on `AffirmationGroup` would be wrong for every Pro user, and it would push a catalog concept onto the data class `PERSONALIZADAS_GROUP` also uses. **(b) is the performance trap the decision exists to avoid** — resolving up to 226 collections inside a row composable, for 15 rows, on every recomposition of a scrolling `LazyColumn`. **The cost staging is the substance of this decision** (see `partiallyLockedGroupIds` below): PRO short-circuits on one enum comparison; a Free user with **zero** collection-scoped grants — the overwhelmingly common case — returns the generated constant with **zero** access resolution; only a Free user actually holding a collection grant pays a resolve, and then only over the ≤150 gated collections, never the 226, and only for groups already in the static set. The whole thing is then `remember`ed on `(tier, adUnlockState)` at the sheet call site, so it recomputes on an entitlement change or a new grant — not per recomposition and never per row. **Scope is the selector only**: no new screen, no change to `isLocked`/`isToggleable`/row alpha/feed filtering. |

## Interfaces / Contracts

### Firestore paths — `data/remote/FirestorePaths.kt` (modified)

```kotlin
// --- Shared, non-per-user catalog (D1). The FIRST top-level, non-`users/` paths in this file. ---

/** Flat by design (design D1): depth costs a collectionGroup query and buys zero read savings. */
fun catalogAffirmationsCollection(): String = "catalogAffirmations"
fun catalogAffirmationDoc(id: String): String = "${catalogAffirmationsCollection()}/$id"

fun catalogUniversesCollection(): String = "catalogUniverses"
fun catalogUniverseDoc(universeId: String): String = "${catalogUniversesCollection()}/$universeId"

fun catalogThemesCollection(): String = "catalogThemes"
fun catalogThemeDoc(themeId: String): String = "${catalogThemesCollection()}/$themeId"

fun catalogCollectionsCollection(): String = "catalogCollections"
fun catalogCollectionDoc(collectionId: String): String = "${catalogCollectionsCollection()}/$collectionId"

/** Written LAST by the seeder (design D12) -- its presence/value is the "seeded through" signal. */
fun catalogVersionDoc(): String = "catalogMeta/version"

// --- Per-user override surface (unchanged convention: users/{uid}/...) ---
fun catalogOverridesCollection(uid: String): String = "users/$uid/catalogOverrides"
fun catalogOverrideDoc(uid: String, catalogAffirmationId: String): String =
    "${catalogOverridesCollection(uid)}/$catalogAffirmationId"

/** TIMED_REPEATABLE grants (design D16). A SIBLING of `adUnlocks`, never a reuse of it: this one
 *  permits overwrite and `adUnlocks` must never. */
fun timedUnlocksCollection(uid: String): String = "users/$uid/timedUnlocks"
fun timedUnlockDoc(uid: String, key: ContentKey): String =
    "${timedUnlocksCollection(uid)}/${key.storageKey}"
```

`catalogOverrideDoc`'s id is a `cat_*` id containing dots — legal (D3), and it is the *only*
Firestore doc id in this file derived from external content, so its legality is unit-tested.

### Access — `access/ContentKey.kt` (modified)

```kotlin
enum class ContentType(val wireName: String) {
    AFFIRMATION_GROUP("affirmationGroup"),
    MEDITATION("meditation"),
    CUSTOM_AFFIRMATION_SLOT("customAffirmationSlot"),

    /** Collection-level gating for the curated catalog (design D5). The source declares
     *  `access{tier, rewardedUnlockHours}` on COLLECTIONS, never on themes.
     *  `wireName` has no `_` -- the invariant above is load-bearing here because catalog
     *  collection ids are dotted AND underscored (`self_worth.feeling_enough.intrinsic_worth`). */
    AFFIRMATION_COLLECTION("affirmationCollection");
    ...
}
```

**Dedicated round-trip test requirement (`ContentKeyTest`, RED first):**

- `AFFIRMATION_COLLECTION.wireName` contains no `_` — asserted for **every** entry via
  `ContentType.entries.forEach`, so the invariant is enforced for future constants too, not just this one.
- `ContentKey.parse(ContentKey(AFFIRMATION_COLLECTION, id).storageKey) == ContentKey(AFFIRMATION_COLLECTION, id)`
  for the longest real dotted-and-underscored collection id in the corpus.
- Round-trip holds for **all 226** real collection ids, driven off the committed taxonomy — a data-driven
  loop, not one hand-picked sample.
- `fromWireName("affirmationGroup")` still returns `AFFIRMATION_GROUP` — no prefix shadowing.
- `storageKey` satisfies `firestore.rules:71`'s `contentType + '_' + contentId` identity.

### Access — `access/AccessCombination.kt` (new)

```kotlin
/**
 * Most-restrictive composition of two independent gates (design D6). Pure, total, commutative,
 * associative, with [AccessDecision.Unlocked] as identity -- all four asserted.
 *
 * NOT a change to `resolveAccess`: this composes its OUTPUT, so every existing single-level caller
 * is untouched and the group gate remains an invariant a collection can never override.
 */
fun mostRestrictive(a: AccessDecision, b: AccessDecision): AccessDecision
```

Full 4×4 truth table is the unit test. Load-bearing rows:

| a | b | result | why |
|---|---|---|---|
| `LockedNeedsPro` | `Unlocked` | `LockedNeedsPro` | absorbing: no ad path may be manufactured from an unlocked sibling |
| `LockedNeedsPro` | `LockedAdUnlockable(PER_USE)` | `LockedNeedsPro` | a spent/no-path gate must never re-offer an ad |
| `LockedAdUnlockable(PER_USE)` | `LockedAdUnlockable(ONE_TIME_TRIAL)` | `LockedAdUnlockable(ONE_TIME_TRIAL)` | the non-repeatable policy is the stricter one |
| `LockedAdUnlockable(P)` | `UnlockedByAd(Q)` | `LockedAdUnlockable(P)` | one live grant does not clear the other gate |
| `UnlockedByAd(P)` | `Unlocked` | `UnlockedByAd(P)` | provenance survives — analytics and the downgrade carve-out both read it |

### Access — `ui/groups/CatalogAccessPolicy.kt` (new)

```kotlin
/**
 * Two-level facade (design D6). `alwaysSelected` short-circuits FIRST, preserving
 * GroupAccessPolicy's "PERSONALIZADAS_GROUP is never locked" regression guard.
 * A [collection] of `null` (unknown/archived) contributes `Unlocked`, so the group gate alone
 * decides -- an unknown collection can never be MORE permissive than its group.
 */
fun catalogAccessDecision(
    group: AffirmationGroup,
    collection: CatalogCollection?,
    tier: AccessTier,
    grants: AdUnlockState,
    nowMillis: Long,
): AccessDecision
```

### Partial-lock badge — `ui/groups/AffirmationGroup.kt` + `CatalogAccessPolicy.kt` (D19)

The gap this closes, stated exactly: every universe group is declared `ContentAccess.Free`, so
`groupAccessDecision` always returns `Unlocked`, so `deriveBadge`'s second branch
(`decision.isUnlocked -> null`) fires for all 14 — **no badge at all on rows whose content is ~66%
Pro-gated**. The row is genuinely selectable and genuinely partially locked at the same time, which
is a state the current two-value `GroupBadge` cannot express.

```kotlin
// ui/groups/AffirmationGroup.kt (modified) -- ONE new constant.
enum class GroupBadge {
    PREMIUM,
    AD_UNLOCK,

    /** The group itself is UNLOCKED and selectable, but at least one collection under it is not
     *  (design D19). Distinct from [PREMIUM] on purpose: a PREMIUM row is `isLocked` and
     *  non-toggleable, a PARTIALLY_LOCKED row is neither. Reusing PREMIUM here would tell the user
     *  "you cannot have this" about content they can select right now. */
    PARTIALLY_LOCKED,
}
```

```kotlin
// ui/groups/GroupAccessPolicy.kt -- UNCHANGED. `deriveBadge` keeps its exact 3-branch body and its
// exact signature. Every existing assertion in GroupAccessPolicyTest must still pass byte-for-byte,
// and `MeditationAccessPolicy`'s documented mirror of it needs no edit.

// ui/groups/CatalogAccessPolicy.kt (new, same file as `catalogAccessDecision`)

/**
 * Catalog-aware badge (design D19). Strictly a FALLBACK layered over [deriveBadge]:
 *  - `alwaysSelected` (personalizadas) -> its `badgeOverride`, unchanged, and NEVER partial.
 *  - a locked group -> PREMIUM / AD_UNLOCK, unchanged. A fully-locked row must not be downgraded
 *    to "partially" locked.
 *  - unlocked group + >=1 locked collection -> PARTIALLY_LOCKED (the only new outcome).
 *  - unlocked group, nothing locked underneath -> null, unchanged.
 */
fun deriveCatalogBadge(
    group: AffirmationGroup,
    decision: AccessDecision,
    isPartiallyLocked: Boolean,
): GroupBadge? =
    deriveBadge(group, decision)
        ?: GroupBadge.PARTIALLY_LOCKED.takeIf { isPartiallyLocked && !group.alwaysSelected }

/**
 * Which universes currently read as partially locked, for THIS user (design D19).
 *
 * Partial lock is user-dependent, so this cannot be a static flag on [AffirmationGroup]. The cost
 * is staged so the two common cases never resolve a single collection:
 *
 *  1. `tier == PRO`            -> `emptySet()`. One enum comparison. Nothing under any universe is
 *                                 locked for a Pro user, by definition.
 *  2. FREE with NO collection-scoped grant -> [CATALOG_GATED_GROUP_IDS] verbatim. This is a
 *                                 GENERATED compile-time constant (see CatalogTaxonomy), so the
 *                                 answer costs a set copy and ZERO access resolution. This is the
 *                                 overwhelmingly common Free path.
 *  3. FREE holding >=1 `AFFIRMATION_COLLECTION` grant -> resolve, but only over the collections
 *                                 that are actually gated (<=150, never the 226) and only for
 *                                 groups already in the static set. Short-circuits per group on the
 *                                 first still-locked collection.
 *
 * Deliberately returns a SET rather than a per-group predicate: the caller memoizes one value for
 * the whole sheet instead of doing work inside a `LazyColumn` item.
 */
fun partiallyLockedGroupIds(
    tier: AccessTier,
    grants: AdUnlockState,
    nowMillis: Long,
): Set<String>
```

```kotlin
// ui/groups/CatalogTaxonomy.kt (generated) -- one more emitted constant.

/** Universe ids with >=1 collection whose `access` is not `ContentAccess.Free` (design D19).
 *  Computed by `generate-catalog.mjs` at generation time, so the runtime never scans 226
 *  collections to answer "could this group be partially locked at all?". */
val CATALOG_GATED_GROUP_IDS: Set<String>
```

Call-site memoization — `MainActivity.kt` (the sheet already receives `accessDecisionFor` as a
lambda at `:713`, so this follows the established shape):

```kotlin
// Recomputed ONLY when the entitlement tier or the grant state changes -- not per recomposition,
// and never per row. This is the whole point of returning a Set (D19).
val partiallyLockedIds = remember(appState.entitlementTier.value, appState.adUnlockState) {
    partiallyLockedGroupIds(
        appState.entitlementTier.value,
        appState.adUnlockState,
        System.currentTimeMillis(),
    )
}
AffirmationGroupSelectorSheet(
    /* ...existing args unchanged... */
    partiallyLockedIds = partiallyLockedIds,   // new param, defaults to emptySet()
)
```

```kotlin
// ui/groups/AffirmationGroupSelectorSheet.kt (modified)
// - new sheet param `partiallyLockedIds: Set<String> = emptySet()` (defaulted, so previews and
//   existing tests compile untouched), threaded to the row as a Boolean.
// - line 213 only:  val badge = deriveCatalogBadge(group, decision, group.id in partiallyLockedIds)
// - `toggleable`, `locked`, `adUnlockable`, and the `.alpha(0.6f)` dim are ALL UNTOUCHED -- a
//   partially locked row behaves exactly like an unlocked one. Badge-only, by scope.

@Composable
fun AffirmationGroupAccessBadge(badge: GroupBadge) = when (badge) {
    /* PREMIUM / AD_UNLOCK branches unchanged */
    GroupBadge.PARTIALLY_LOCKED -> AccessBadge(
        icon = Icons.Filled.LockOpen,                       // "some of it is open", not a closed lock
        label = stringResource(R.string.affirmation_group_badge_partial),
        containerColor = PremiumBadgeColor.copy(alpha = 0.08f),   // adjacent to Premium...
        contentColor = PremiumBadgeColor,
        borderColor = PremiumBadgeColor.copy(alpha = 0.3f),       // ...but visibly lighter than it
    )
}
```

New strings (2 per locale): `affirmation_group_badge_partial` — `values/` (es) **"Incluye Pro"**,
`values-en/` **"Includes Pro"**. The existing `AccessBadge` already renders a visible text label and
leaves the icon's `contentDescription` null, so the label carries the semantics — no extra
accessibility work.

**Two honest caveats, both carried to `sdd-tasks` rather than buried:**

1. **The badge may be uniform on day one.** With 150 Pro collections spread over 226, it is likely
   that all 14 universes land in `CATALOG_GATED_GROUP_IDS`, in which case every thematic row wears
   the same badge and conveys little *between* rows. It still does two real jobs: it distinguishes
   the 14 universes from `personalizadas` (which keeps its own PREMIUM override), and it becomes
   genuinely discriminating the moment a Free user ad-unlocks a collection or a future content drop
   ships an all-free universe. The generator emits the **measured** set, never a hard-coded 14, and
   the acceptance task must actually look at the emitted value before shipping. If it is uniform and
   the team judges that noise, the cheap follow-up is a count ("12 de 18 gratis") — explicitly out of
   scope here.
2. **The badge can lag an expiring `TIMED_REPEATABLE` window by one recomposition**, because
   `remember` is keyed on grants rather than on wall-clock time. This is cosmetic only and cannot
   leak content: feed inclusion goes through `filteredAffirmations`, which recomputes
   `catalogAccessDecision` with a live `System.currentTimeMillis()` on every read (D7). Accepted, and
   it is the same staleness `accessDecisionFor` already carries today.

### Group selection defaults — `data/AffirmityAppState.kt` (modified, D18)

```kotlin
// AffirmityAppState.kt:1289-1291 (inside rememberAffirmityAppState)
// `isThematic` is the SELECTOR -- "every thematic group is on by default" is the product decision.
// The tier condition is retained purely as a GUARD: it keeps the invariant that the fresh-install
// default can never contain a group `deselectLockedGroups` would immediately strip, which would
// show as a visible flicker on the first entitlement emission. Post-D17/D19 this is all 14.
val defaultThematicGroupIds = defaultAffirmationGroups()
    .filter { it.isThematic && it.access.requiredTier == AccessTier.FREE }
    .map { it.id }.toSet()

// AffirmityAppState.kt:199-206 -- the ONE behavioural line added.
fun resolveSelectedGroupIds(
    persisted: Set<String>?,
    knownIds: Set<String>,
    defaultThematicIds: Set<String>,
): Set<String> {
    val filtered = persisted?.filter { it in knownIds }?.toSet() ?: defaultThematicIds
    // Minimum-selection invariant, applied HERE so it is tier-independent (design D18). Previously
    // this recovery lived only in `deselectLockedGroups`, which `AffirmityAppState.kt:809` guards
    // with `if (entitlement.tier == AccessTier.FREE)` -- so a PRO user whose persisted ids were all
    // dropped as unknown landed on a personalizadas-only, thematically empty feed.
    // Cannot touch a healthy selection: any surviving thematic id short-circuits it.
    val resolved = if (filtered.none { it != PERSONALIZADAS_GROUP_ID }) defaultThematicIds else filtered
    return resolved + PERSONALIZADAS_GROUP_ID
}
```

**Existing users are untouched, structurally.** `defaultThematicIds` is reachable only through the
`?:` on a `null` persisted value, and `GroupSelectionPreferences.observeSelectedGroupIds()` returns
`null` **only** when the DataStore key was never written — its own KDoc states this, and
`AffirmationGroupPreferences.kt:31-32` is a bare key read with no default. Anyone who has ever
committed a selection has a non-null set. The new fallback is *not* a retroactive re-default either:
it fires only when a persisted selection has been reduced to nothing thematic, which is precisely
the broken state it exists to repair. `AffirmationGroupPreferences` itself is **not modified**.

### Ad unlocks — timed repeatable windows (D16)

```kotlin
// access/ContentAccess.kt (modified)
enum class AdUnlockPolicy {
    NONE, PER_USE, ONE_TIME_TRIAL,

    /** Durable but EXPIRING and RE-EARNABLE: after [ContentAccess.unlockWindowHours] elapse the
     *  content re-locks and the ad CTA is offered again. Deliberately payload-free -- an enum
     *  constant is a singleton, so a duration on the constant would freeze one window into the
     *  type (design D16). Persisted to `timed_ad_unlock` / `users/{uid}/timedUnlocks`, NEVER to
     *  the create-only `ad_unlock` / `users/{uid}/adUnlocks` store. */
    TIMED_REPEATABLE,
}

data class ContentAccess(
    val requiredTier: AccessTier,
    val adUnlock: AdUnlockPolicy = AdUnlockPolicy.NONE,
    /** Non-null IFF [adUnlock] is TIMED_REPEATABLE -- enforced in `init`, so an unwindowed timed
     *  policy is unconstructible rather than a runtime surprise at grant time. */
    val unlockWindowHours: Int? = null,
) {
    init { require((unlockWindowHours != null) == (adUnlock == AdUnlockPolicy.TIMED_REPEATABLE)) }
    companion object {
        /* Free / Pro / ProOrAdPerUse / ProOrAdTrial unchanged -- all omit the new param. */
        fun ProOrAdTimed(hours: Int) = ContentAccess(AccessTier.PRO, AdUnlockPolicy.TIMED_REPEATABLE, hours)
    }
}
```

**Every existing `ContentAccess` construction is source-compatible** (new param is trailing with a
default), so meditations (`MeditationCatalog.kt:131`), the custom-affirmation slot, and
`PERSONALIZADAS_GROUP` are untouched — this is purely additive (the user's explicit check).

```kotlin
// access/AccessResolution.kt (modified) -- ONE new branch. The ONE_TIME_TRIAL branch is unchanged.
AdUnlockPolicy.TIMED_REPEATABLE -> {
    val record = grants.timedUnlocks[key]          // separate map, separate store (D16)
    when {
        record == null -> AccessDecision.LockedAdUnlockable(AdUnlockPolicy.TIMED_REPEATABLE)
        !record.hasExpired(nowMillis) -> AccessDecision.UnlockedByAd(AdUnlockPolicy.TIMED_REPEATABLE)
        // THE ONE LINE that differs from ONE_TIME_TRIAL: expired means re-earnable, not spent.
        else -> AccessDecision.LockedAdUnlockable(AdUnlockPolicy.TIMED_REPEATABLE)
    }
}

// access/AdUnlockGrant.kt (modified) -- AdUnlockRecord is UNCHANGED (expiresAtMillis + hasExpired
// already exist and were built for exactly this).
data class AdUnlockState(
    val sessionUnlocks: Set<ContentKey> = emptySet(),
    val durableUnlocks: Map<ContentKey, AdUnlockRecord> = emptyMap(),
    /** TIMED_REPEATABLE grants. Separate from [durableUnlocks] because its store permits
     *  overwrite and [durableUnlocks]' store must never (design D16). */
    val timedUnlocks: Map<ContentKey, AdUnlockRecord> = emptyMap(),
)

// data/repository/Repositories.kt (modified) -- AdUnlockRepository gains two members.
interface AdUnlockRepository {
    /* observeDurableUnlocks / getDurableUnlocks / grantDurableUnlock UNCHANGED (still idempotent) */
    fun observeTimedUnlocks(): Flow<List<AdUnlockRecord>>
    /** UPSERT, unlike [grantDurableUnlock]: re-earning after expiry replaces grantedAt/expiresAt. */
    suspend fun grantTimedUnlock(record: AdUnlockRecord)
}

// data/local/TimedAdUnlockEntity.kt (new) + TimedAdUnlockDao.kt (new)
@Entity(tableName = "timed_ad_unlock")            // same columns as AdUnlockEntity
@Insert(onConflict = OnConflictStrategy.REPLACE)  // REPLACE, not IGNORE -- the whole point

// data/AffirmityAppState.kt (modified) -- grant routing
fun requestAdUnlock(key: ContentKey, policy: AdUnlockPolicy, unlockWindowHours: Int? = null)
// on Earned:
AdUnlockPolicy.TIMED_REPEATABLE -> {
    val hours = unlockWindowHours ?: return@launch   // logged; never grants an unbounded window
    val now = System.currentTimeMillis()
    ready().adUnlocks.grantTimedUnlock(
        AdUnlockRecord(key, now, expiresAtMillis = now + hours * 3_600_000L),
    )
}
```

The trailing-default third parameter keeps every existing `requestAdUnlock` call site compiling
unchanged; the CTA for a catalog collection passes `collection.access.unlockWindowHours`.

```kotlin
// access/RewardedAdGateway.kt + RewardedAdUnlockSource.kt (modified)
data class AdUnitIds(val perUse: String, val oneTimeTrial: String, val timedRepeatable: String)
AdUnlockPolicy.TIMED_REPEATABLE -> adUnitIds.timedRepeatable.ifBlank { null }
```

`adUnitIdFor`'s `when` is exhaustive, so **the compiler forces every policy switch in the codebase
to acknowledge the new constant** — that is the intended safety net, not an inconvenience.
**Release-build gotcha:** the existing two units come from `requiredAdSecret(...)`, which *fails the
release build* when the secret is absent. Do **not** add a third `requiredAdSecret` — use
`adSecret("admob.rewardedUnit.timedRepeatable", ...)` falling back to the `oneTimeTrial` unit id, so
a release build is not blocked on provisioning a new AdMob unit. Debug reuses the Google test unit.

```
// firestore.rules (modified) -- NEW block. users/{uid}/adUnlocks is left BYTE-IDENTICAL.
// Update is allowed here and ONLY here: re-earning a window after expiry is an overwrite by
// definition (design D16). Delete stays denied so a client cannot erase an active window and
// restart it early -- the anti-back-dating property that matters for a timed grant.
match /users/{uid}/timedUnlocks/{contentKey} {
  allow read: if request.auth != null && request.auth.uid == uid;
  allow create, update: if request.auth != null && request.auth.uid == uid
    && request.resource.data.keys().hasOnly(
         ['contentType', 'contentId', 'grantedAtMillis', 'expiresAtMillis'])
    && request.resource.data.keys().hasAll(
         ['contentType', 'contentId', 'grantedAtMillis', 'expiresAtMillis'])
    && contentKey == request.resource.data.contentType + '_' + request.resource.data.contentId
    && request.resource.data.grantedAtMillis is int
    && request.resource.data.expiresAtMillis is int          // NON-null here, unlike adUnlocks
    && request.resource.data.expiresAtMillis > request.resource.data.grantedAtMillis;
  allow delete: if false;
}
```

### Taxonomy — `ui/groups/CatalogTaxonomy.kt` (new, generated)

Compiled Kotlin, generated by the build-time transform (D11) and committed. 14 universes → 14
`AffirmationGroup`s; 74 themes and 226 collections as plain data. Compiled rather than parsed at
runtime because collection access must be resolvable **before** any I/O — `filteredAffirmations` (D7)
reads it on every recomposition.

```kotlin
data class CatalogTheme(val id: String, val universeId: String, val titleRes: Int, val order: Int)

data class CatalogCollection(
    val id: String,
    val universeId: String,
    val themeId: String,
    /** Null when the source declares none -- pure group inheritance (design D6). */
    val access: ContentAccess?,
    val order: Int,
)

fun catalogUniverseGroups(): List<AffirmationGroup>   // 14, order-sorted
fun catalogCollections(): List<CatalogCollection>     // 226
fun catalogCollectionsById(): Map<String, CatalogCollection>
```

Source `access` → `ContentAccess` mapping (D6 / D16):

| source | `ContentAccess` |
|---|---|
| `tier: "free"` (any hours) | `ContentAccess.Free` |
| `tier: "pro"`, `rewardedUnlockHours: null` (75 collections) | `ContentAccess.Pro` |
| `tier: "pro"`, `rewardedUnlockHours: <n>` (75 collections, all `n = 24`) | `ContentAccess.ProOrAdTimed(n)` — the generator emits `n` verbatim, never a hard-coded 24 (D16) |

The generator **fails** on the source's own invariant violation (`tier: "free"` with non-null hours)
and on a non-positive `rewardedUnlockHours`, which `ContentAccess.init` would otherwise accept.

**All 14 universe `AffirmationGroup`s are emitted as `ContentAccess.Free`.** The source declares
access only on collections (D5), so a group-level tier would be fabricated; `Free` at the group
level makes `mostRestrictive` reduce to the collection's own decision, which is the intent. Two
consequences follow from this plus D17 and must be carried to `sdd-tasks`:

1. `proOnlyGroupIds` (`AffirmityAppState.kt:1295`) becomes **empty**, so
   `EntitlementResolution.stripProOnlyGroups` is a no-op after this change. Pro gating is not lost —
   it moves from "strip the selection on downgrade" to "filter per collection at read time" (D7),
   which is strictly more precise. State it, test it, do not delete the function.
2. `deriveBadge` hides a badge whenever the decision is unlocked for a non-`alwaysSelected` group,
   so a universe row would render **no Premium/ad badge** even though ~66% of its collections are
   Pro. This was a presentation gap, not a security one — locked content still never enters the feed.
   **It is no longer deferred: `sdd-tasks` ships the fix as part of this change** — a new
   `GroupBadge.PARTIALLY_LOCKED` fed by the generated `CATALOG_GATED_GROUP_IDS`, with `deriveBadge`
   itself left byte-identical. See **D19** and the Partial-lock badge section. The generator emits
   one extra constant for it; the data model is otherwise unaffected.

### Persistence — Room entities

```kotlin
// data/local/CatalogAffirmationEntity.kt (new)
/**
 * One shared, read-only catalog affirmation. Never user-owned, never edited, never deleted:
 * there is no write path to this table outside [CatalogSeeder]'s full-replace transaction.
 * Deliberately has NO background columns (design D4) and NO subtitle (design D8) -- the source
 * authored one string per affirmation and no background at all.
 */
@Entity(
    tableName = "catalog_affirmations",
    indices = [Index("groupId"), Index("collectionId")],
)
data class CatalogAffirmationEntity(
    /** `cat_` + the source dotted id (design D3). Disjoint from `UUID.randomUUID()` by construction. */
    @PrimaryKey val id: String,
    val text: String,
    /** The universe id -- this is the `AffirmationGroup.id` the feed filters on. */
    val groupId: String,
    val themeId: String,
    /** The access unit (design D5). Joined against `catalogCollectionsById()` in memory. */
    val collectionId: String,
    val sortOrder: Int,
)

// data/local/CatalogOverrideEntity.kt (new)
/**
 * Per-user placeholder overrides for a SHARED row (design: proposal's catalog-token-overrides).
 * A separate table exists because `AffirmationEntity.overrides` sits on the same mutable row as
 * title/subtitle -- a read-only shared row has no per-user slot.
 *
 * NOTE: measured to be structurally empty in v1.0.0 -- zero catalog texts contain `[`/`]` (D11),
 * so no catalog affirmation has a token to override. The full surface (this table + the Firestore
 * mirror + rules) ships anyway, by explicit user decision: it is forward-compatible storage for
 * token-bearing content, and adding it later would cost a second migration and a second rules
 * review. Open Question 1 is CLOSED.
 */
@Entity(tableName = "catalog_affirmation_overrides")
data class CatalogOverrideEntity(
    @PrimaryKey val catalogAffirmationId: String,
    @ColumnInfo(defaultValue = "{}") val overrides: Map<String, String> = emptyMap(),
)
```

Both `Index` declarations are load-bearing: `observeByGroupIds` is the feed's hot query, and
`groupId` has only 14 distinct values across 2712 rows.

### Persistence — DAOs

```kotlin
// data/local/CatalogAffirmationDao.kt (new) -- READ + seed only. No insert/update/delete of a single row.
@Dao
interface CatalogAffirmationDao {
    @Query("SELECT * FROM catalog_affirmations ORDER BY groupId ASC, sortOrder ASC")
    fun observeAll(): Flow<List<CatalogAffirmationEntity>>

    /** Feed query. Empty [groupIds] returns empty -- never "all", which would leak locked groups. */
    @Query("SELECT * FROM catalog_affirmations WHERE groupId IN (:groupIds) ORDER BY groupId ASC, sortOrder ASC")
    fun observeByGroupIds(groupIds: Set<String>): Flow<List<CatalogAffirmationEntity>>

    /** Favorites cross-space resolution (design D10) -- ids may reference either space. */
    @Query("SELECT * FROM catalog_affirmations WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<CatalogAffirmationEntity>

    @Query("SELECT COUNT(*) FROM catalog_affirmations")
    suspend fun count(): Int

    /** Seed path only (design D13). @Transaction makes replace-then-insert atomic. */
    @Transaction
    suspend fun replaceAll(rows: List<CatalogAffirmationEntity>) { deleteAll(); insertAll(rows) }

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(rows: List<CatalogAffirmationEntity>)
    @Query("DELETE FROM catalog_affirmations") suspend fun deleteAll()
}

// data/local/CatalogOverrideDao.kt (new)
@Dao
interface CatalogOverrideDao {
    @Query("SELECT * FROM catalog_affirmation_overrides")
    fun observeAll(): Flow<List<CatalogOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CatalogOverrideEntity)

    /** Whole-map replacement mirrors `AffirmationRepository.setOverrides`; an empty map DELETES
     *  the row rather than storing `{}`, so "no overrides" has exactly one representation. */
    @Query("DELETE FROM catalog_affirmation_overrides WHERE catalogAffirmationId = :id")
    suspend fun deleteById(id: String)
}
```

### Persistence — migration

```kotlin
/** Additive: creates both catalog tables empty plus two indices. No existing table, column, or row
 * is read or altered, so every pre-change read path is bit-identical. `catalog_affirmations` is
 * populated by CatalogSeeder from the bundled asset on the next launch (design D2/D13), NOT here --
 * a migration must not parse a 550 KB asset on the main-thread-adjacent open path. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `catalog_affirmations` (
                `id` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `groupId` TEXT NOT NULL,
                `themeId` TEXT NOT NULL,
                `collectionId` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_affirmations_groupId` ON `catalog_affirmations` (`groupId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_affirmations_collectionId` ON `catalog_affirmations` (`collectionId`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `catalog_affirmation_overrides` (
                `catalogAffirmationId` TEXT NOT NULL,
                `overrides` TEXT NOT NULL DEFAULT '{}',
                PRIMARY KEY(`catalogAffirmationId`)
            )
            """.trimIndent(),
        )
        // Timed ad unlocks (design D16). Same columns as `ad_unlock`, deliberately a SEPARATE
        // table so `ad_unlock`'s create-only DAO contract needs no change. `expiresAtMillis` is
        // NOT NULL here -- an unbounded timed window is meaningless.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `timed_ad_unlock` (
                `contentKey` TEXT NOT NULL,
                `contentType` TEXT NOT NULL,
                `contentId` TEXT NOT NULL,
                `grantedAtMillis` INTEGER NOT NULL,
                `expiresAtMillis` INTEGER NOT NULL,
                PRIMARY KEY(`contentKey`)
            )
            """.trimIndent(),
        )
    }
}
```

`AffirmityDatabase`: `version = 9`, **three** entities appended (`CatalogAffirmationEntity`,
`CatalogOverrideEntity`, `TimedAdUnlockEntity`), three DAO accessors,
`MIGRATION_8_9` appended to the `addMigrations(...)` chain, and
`app/schemas/com.pirxhio.affirmity.data.local.AffirmityDatabase/9.json` generated and committed
(the existing `OverridesConverters` already covers `CatalogOverrideEntity.overrides`).

**Index-name exactness is a real trap**: Room's exported schema compares index names, so
`index_catalog_affirmations_groupId` must match Room's generated name character for character or
the migration test fails with a confusing identity-mismatch error.

### Repositories — `data/repository/Repositories.kt` (modified)

```kotlin
/**
 * Read-only contract for the shared catalog cache. Deliberately OUTSIDE [DataSession] (design D9):
 * the catalog is not per-user, so it has no sign-in/sign-out swap semantics to participate in and
 * `DataSession.Remote`'s "backed exclusively by Firestore" contract would be a lie. There is no
 * write method: seeding goes through [CatalogSeeder] against the DAO directly.
 */
interface CatalogAffirmationRepository {
    fun observeByGroupIds(groupIds: Set<String>): Flow<List<CatalogAffirmationEntity>>
    suspend fun getByIds(ids: List<String>): List<CatalogAffirmationEntity>
}

/**
 * Per-user overrides on shared rows. **Full sync surface ships in this change** (user decision,
 * Open Question 1 CLOSED): a Room-backed implementation AND a Firestore-backed one at
 * `users/{uid}/catalogOverrides/{catalogAffirmationId}`, selected by the active [DataSession] the
 * same way `AffirmationRepository` is -- unlike the catalog itself (D9), overrides ARE per-user, so
 * they DO have sign-in/sign-out swap semantics and belong on the session path.
 */
interface CatalogOverrideRepository {
    fun observeAll(): Flow<Map<String, Map<String, String>>>
    /** Whole-map replacement, matching `AffirmationRepository.setOverrides` (design.md D8). */
    suspend fun setOverrides(catalogAffirmationId: String, overrides: Map<String, String>)
}

object NoOpCatalogAffirmationRepository : CatalogAffirmationRepository { /* empty flow, empty list */ }
object NoOpCatalogOverrideRepository : CatalogOverrideRepository { /* emptyMap, no-op */ }
```

`NoOp` defaults follow the `NoOpFavoriteAffirmationRepository` convention, so every existing
`AffirmityAppState` unit test compiles untouched.

### Seeding — `data/catalog/` (new package)

```kotlin
/** Pure. The bracket gate's RULE (design D11), free of Android, I/O, and JSON. */
object CatalogTextSanitizer {
    /** Character offsets of every literal `[` or `]`. Empty == clean. Never rewrites (D11). */
    fun findIllegalBrackets(text: String): List<Int>
}

/** Pure asset -> entity transform. Validates as it maps; throws on the first violation. */
object CatalogAssetParser {
    fun parse(json: String): ParsedCatalog   // { version: String, affirmations: List<CatalogAffirmationEntity> }
}

/**
 * Bundled-asset-first seeding (design D2/D13). Runs off the main thread at app start.
 * Marker AFTER the transaction: a crash between them costs one redundant re-seed, never a
 * half-populated catalog.
 */
class CatalogSeeder(
    private val assets: AssetManager,
    private val dao: CatalogAffirmationDao,
    private val prefs: CatalogPreferences,
) {
    /** No-op when `prefs.seededCatalogVersion == bundled version`. Idempotent by full replace. */
    suspend fun seedIfNeeded()
}
```

`CatalogPreferences` (DataStore, `data/local/CatalogPreferences.kt`) exposes
`observeSeededCatalogVersion(): Flow<String?>` / `saveSeededCatalogVersion(v: String)`.

### App state — `data/AffirmityAppState.kt` (modified)

```kotlin
data class Affirmation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String,
    val background: AffirmationBackground,
    val groupId: String = PERSONALIZADAS_GROUP_ID,
    val overrides: Map<String, String> = emptyMap(),
    /** Presentation-level provenance (design D14). The UI reads THIS; write routing reads the id
     *  prefix, which is the storage-level ground truth and cannot drift from the row's real home. */
    val source: AffirmationSource = AffirmationSource.OWNED,
    /** Null for OWNED rows. The access unit for CATALOG rows (design D5/D6). */
    val collectionId: String? = null,
)

enum class AffirmationSource { OWNED, CATALOG }

class AffirmityAppState(
    /* ...existing parameters unchanged... */
    private val catalog: CatalogAffirmationRepository = NoOpCatalogAffirmationRepository,
    // NOTE: catalogOverrides is NOT a constructor param -- it is reached via `ready().catalogOverrides`
    // because it is a per-user DataSession member (D9, revised).
) {
    private val catalogAffirmations = mutableStateListOf<Affirmation>()

    /** Both ID spaces, for favorites resolution (design D10). Concatenation, never a SQL union --
     *  when the session is Remote, `affirmations` is not in Room at all. */
    private val allAffirmations: List<Affirmation> get() = affirmations + catalogAffirmations

    /**
     * Feed list. Owned rows keep the existing group filter verbatim; catalog rows are additionally
     * filtered by per-collection effective access (design D6/D7) -- a locked collection's content
     * never enters the pager, it is upsold in the selector sheet instead.
     */
    val filteredAffirmations: List<Affirmation>
        get() {
            val ids = selectedGroupIds.value ?: return affirmations
            val byId = catalogCollectionsById()
            val now = System.currentTimeMillis()
            val groups = catalogUniverseGroups().associateBy { it.id }
            return affirmations.filter { it.groupId in ids } +
                catalogAffirmations.filter { a ->
                    a.groupId in ids && groups[a.groupId]?.let { g ->
                        catalogAccessDecision(g, byId[a.collectionId], entitlementTier.value, grantState(), now).isUnlocked
                    } == true
                }
        }

    /** Unchanged in shape; now resolves across BOTH id spaces (design D10). Access-unfiltered on
     *  purpose: a favorite made while Pro survives a downgrade (Open Question 4). */
    val favoriteAffirmations: List<Affirmation>
        get() = allAffirmations.associateBy { it.id }
            .let { byId -> favoriteOrderedIds.value.mapNotNull(byId::get) }

    init {
        /* ...existing collectors unchanged... */
        scope.launch {
            // Two subscriptions with DELIBERATELY different lifetimes (design D9, revised):
            // the catalog rows survive an auth swap (identical signed-in and signed-out); the
            // overrides half is session.flatMapLatest, matching the existing per-user collectors,
            // so signing out drops the previous user's overrides atomically.
            combine(
                snapshotFlow { selectedGroupIds.value.orEmpty() }
                    .flatMapLatest { catalog.observeByGroupIds(it) },
                session.flatMapLatest { it.catalogOverrides.observeAll() },
            ) { rows, overrides -> rows to overrides }
                .catch { e -> Log.e(TAG, "catalog flow failed", e) }
                .collect { (rows, overrides) ->
                    catalogAffirmations.clear()
                    catalogAffirmations.addAll(rows.map { it.toAffirmation(overrides[it.id].orEmpty()) })
                }
        }
    }

    fun setTokenOverride(affirmationId: String, tokenKey: String, rawValue: String) {
        scope.launch {
            // Prefix routing (design D14): the id decides WHICH store, not a presentation flag.
            val current = allAffirmations.firstOrNull { it.id == affirmationId } ?: return@launch
            val next = current.overrides.toMutableMap().apply {
                when (val n = AffirmationTemplateParser.normalizeOverrideValue(rawValue)) {
                    null -> remove(tokenKey); else -> put(tokenKey, n)
                }
            }
            val pruned = AffirmationTemplateParser.pruneOverrides(current.title, current.subtitle, next)
            if (affirmationId.startsWith(CATALOG_ID_PREFIX)) {
                ready().catalogOverrides.setOverrides(affirmationId, pruned)
            } else {
                ready().affirmations.setOverrides(affirmationId, pruned)
            }
        }
    }

    fun removeAffirmation(id: String) {
        // Load-bearing guard (design D14): without it, a catalog id reaches
        // `ready().affirmations.deleteById`, which is a silent no-op on Room but a REAL per-user
        // Firestore write when the session is Remote. Asserted, not assumed.
        if (id.startsWith(CATALOG_ID_PREFIX)) return
        /* ...existing body unchanged... */
    }
}
```

`toggleFavorite`, `removeFavorite`, and the entire `FavoriteAffirmationRepository` chain are
**unmodified** — they only ever handled opaque id strings (D10).

### Firestore rules — `firestore.rules` (modified)

```
// Shared, read-only curated catalog. World-readable so an unauthenticated first launch can refresh
// the version marker; client writes are unconditionally denied. Written exclusively by
// `functions/tools/seedCatalog.ts` via the Admin SDK, which bypasses rules (design D12).
match /catalogAffirmations/{id}   { allow read: if true; allow write: if false; }
match /catalogUniverses/{id}      { allow read: if true; allow write: if false; }
match /catalogThemes/{id}         { allow read: if true; allow write: if false; }
match /catalogCollections/{id}    { allow read: if true; allow write: if false; }
match /catalogMeta/{doc}          { allow read: if true; allow write: if false; }

// Per-user placeholder overrides on shared catalog rows. Owner-only, same posture as
// users/{uid}/affirmations. Deliberately NOT covered by a recursive wildcard, per this file's
// own leading comment.
match /users/{uid}/catalogOverrides/{catalogAffirmationId} {
  allow read, write: if request.auth != null && request.auth.uid == uid;
}
```

`allow read: if true` is a deliberate, recorded choice: the catalog contains **no user data**, it
already ships in the APK (D2), and requiring auth would break the version check for signed-out users
— who are the majority at first launch. The blast radius of a scraper is "someone copies content
that is already in every downloaded APK". Covered by the existing `npm run test:rules` harness.

## Data Flow

### Cold start — bundled-first seeding (D2/D13)

```
APK install
  │
  ├─▶ Room opens at v9 (MIGRATION_8_9 ran: both catalog tables exist, EMPTY)
  │
  ▼
CatalogSeeder.seedIfNeeded()            [off main thread, at app start]
  │  prefs.seededCatalogVersion == null  ≠  bundled "1.0.0"
  ├─▶ assets.open("catalog.v1.json") ─▶ CatalogAssetParser.parse
  │        └─ throws on: duplicate id · unknown collectionId · illegal bracket (D11)
  ├─▶ dao.replaceAll(2712 rows)          @Transaction ── atomic
  └─▶ prefs.saveSeededCatalogVersion("1.0.0")   ◀── MARKER LAST (D13)
                                                     crash before this ⇒ redundant re-seed, never
                                                     a half-populated catalog

           ═══ steady state: ZERO network, ZERO Firestore reads ═══

Refresh check (throttled)
  └─▶ read catalogMeta/version                                    ← 1 doc read, not 2712
        ├─ remote == local ⇒ done
        └─ remote  >  local ⇒ whereGreaterThan("catalogVersion", local) delta fetch
                              ─▶ upsert ─▶ save new marker LAST
```

### Read model — merging two ID spaces

```
  affirmations (owned, session-backed: Room OR Firestore)      catalog_affirmations (Room, shared)
        │  id = UUID  · title+subtitle · overrides on-row            │  id = cat_*  · text only
        │                                                            │
        │                                        catalog_affirmation_overrides (Room, per-user)
        │                                                            │  overrides off-row
        │                                                            ▼
        │                                             toAffirmation(source = CATALOG)
        ▼                                                            ▼
        └──────────────────────┬─────────────────────────────────────┘
                               ▼
                        allAffirmations                    (D10 — concatenation, never SQL UNION)
              ┌────────────────┴─────────────────┐
              ▼                                  ▼
      filteredAffirmations                favoriteAffirmations
        owned: group filter                 favoriteOrderedIds.mapNotNull { byId[it] }
        catalog: group filter AND               ▲
          mostRestrictive(group, collection)    │  ids from favorite_affirmations (UNCHANGED table,
            .isUnlocked            (D6/D7)      │  UNCHANGED DAO, UNCHANGED repository)
              ▼                                 │  an id in NEITHER space drops out here — favorites'
      AffirmationsScreen  ◀── unchanged sig ────┘  design D7 survives across two ID spaces for free
```

### Effective access resolution (D6)

```
                       AffirmationGroup (universe)          CatalogCollection
                                │                                  │
              resolveAccess(ContentKey(                 resolveAccess(ContentKey(
                AFFIRMATION_GROUP, group.id))             AFFIRMATION_COLLECTION, collection.id))
                                │                                  │
                                └──────────┬───────────────────────┘
                                           ▼
                                mostRestrictive(a, b)      ← LockedNeedsPro is absorbing
                                           ▼
                                   AccessDecision
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    ▼                      ▼                      ▼
             feed inclusion         selector row state      ad-unlock CTA
              (D7 filter)          (lock / badge / CTA)   (ContentKey persists to
                                                           users/{uid}/adUnlocks)
```

`group.alwaysSelected` short-circuits the whole diagram to `Unlocked`, preserving
`GroupAccessPolicy`'s existing regression guard.

## File Changes

| File | Action | Description |
|---|---|---|
| `tools/catalog/generate-catalog.mjs` | Create | Source JSON → `catalog.v1.json` asset + `CatalogTaxonomy.kt`. **Fails** on any literal `[`/`]` in a text field, duplicate id, unknown reference, or `free`+non-null-hours (D11). Also computes and emits `CATALOG_GATED_GROUP_IDS` (D19). Node, no build wiring — run manually on a content drop. |
| `app/src/main/assets/catalog.v1.json` | Create (generated, committed) | 2712 sanitized affirmations + version. ~550 KB raw, ~150 KB in APK (D2). |
| `app/src/main/java/.../ui/groups/CatalogTaxonomy.kt` | Create (generated, committed) | 14 groups, 74 themes, 226 collections with `ContentAccess` (D6). Plus `CATALOG_GATED_GROUP_IDS` — the generated set of universes holding ≥1 non-Free collection (D19). |
| `data/catalog/CatalogTextSanitizer.kt` | Create | `findIllegalBrackets`. Pure, stdlib only (D11). |
| `data/catalog/CatalogAssetParser.kt` | Create | Asset → entities. Validating, throws on first violation. |
| `data/catalog/CatalogSeeder.kt` | Create | `seedIfNeeded()`; transaction then marker (D13). |
| `data/local/CatalogAffirmationEntity.kt` | Create | `id`/`text`/`groupId`/`themeId`/`collectionId`/`sortOrder` + 2 indices. No background (D4), no subtitle (D8). |
| `data/local/CatalogOverrideEntity.kt` | Create | `catalogAffirmationId` PK + `overrides`. |
| `data/local/CatalogAffirmationDao.kt` | Create | Read + `replaceAll` only; no single-row mutation. |
| `data/local/CatalogOverrideDao.kt` | Create | `observeAll`, `upsert`, `deleteById`. |
| `data/local/CatalogPreferences.kt` | Create | DataStore seed marker (D13). |
| `data/local/AffirmityDatabase.kt` | Modify | `version = 9`, 2 entities, 2 DAO accessors, `MIGRATION_8_9`. |
| `app/schemas/…/9.json` | Create (generated) | Room exported schema v9. |
| `data/repository/Repositories.kt` | Modify | `CatalogAffirmationRepository` + `NoOp`; `CatalogOverrideRepository` + `NoOp`; `AdUnlockRepository` gains `observeTimedUnlocks`/`grantTimedUnlock` (D16). |
| `data/repository/DataSession.kt` | Modify | **Now modified, contrary to the previous revision**: `catalogOverrides` joins the sealed interface's per-user members (D9, revised). |
| `data/repository/RoomCatalogAffirmationRepository.kt` | Create | 1:1 DAO delegation. |
| `data/repository/RoomCatalogOverrideRepository.kt` | Create | 1:1 DAO delegation; empty map deletes the row. |
| `data/remote/FirestoreCatalogOverrideRepository.kt` | Create | `users/{uid}/catalogOverrides` mirror; snapshot listener → `Map<String, Map<String,String>>`; empty map deletes the doc. Mirrors `FirestoreAffirmationRepository`. |
| `data/repository/RoomAdUnlockRepository.kt` | Modify | Implements the two timed members over `TimedAdUnlockDao` (D16). |
| `data/remote/FirestoreAdUnlockRepository.kt` | Modify | Implements the two timed members over `users/{uid}/timedUnlocks`; reuses the existing `expiresAtMillis` mapper (D16). |
| `data/local/TimedAdUnlockEntity.kt` | Create | Same columns as `AdUnlockEntity`; `expiresAtMillis` NOT NULL (D16). |
| `data/local/TimedAdUnlockDao.kt` | Create | `@Insert(onConflict = REPLACE)` + `observeAll` — REPLACE is the re-earn semantic (D16). |
| `data/remote/FirestorePaths.kt` | Modify | 5 catalog builders + 2 override builders (D1) + 2 timed-unlock builders (D16). |
| `access/ContentKey.kt` | Modify | `AFFIRMATION_COLLECTION("affirmationCollection")` (D5). |
| `access/ContentAccess.kt` | Modify | `AdUnlockPolicy.TIMED_REPEATABLE`; `ContentAccess.unlockWindowHours` + `init` invariant + `ProOrAdTimed(hours)` (D16). |
| `access/AccessResolution.kt` | Modify | **Now modified** (previous revision said otherwise): one new `when` branch; `ONE_TIME_TRIAL`'s branch untouched (D16). |
| `access/AdUnlockGrant.kt` | Modify | `AdUnlockState.timedUnlocks`. `AdUnlockRecord` unchanged. |
| `access/RewardedAdGateway.kt` | Modify | `AdUnitIds.timedRepeatable`. |
| `access/RewardedAdUnlockSource.kt` | Modify | `adUnitIdFor` third branch (compiler-forced by the exhaustive `when`). |
| `app/build.gradle.kts` | Modify | `ADMOB_REWARDED_UNIT_TIMED_REPEATABLE` for debug + release. **Optional** secret with fallback to the oneTimeTrial unit — NOT `requiredAdSecret` (D16). |
| `access/AccessCombination.kt` | Create | `mostRestrictive` (D6), including the 3-policy strictness order. |
| `ui/groups/CatalogAccessPolicy.kt` | Create | Two-level facade (D6) **plus** `deriveCatalogBadge` and `partiallyLockedGroupIds` (D19). |
| `ui/groups/GroupAccessPolicy.kt` | **Not modified** | `deriveBadge`/`isLocked`/`isToggleable` stay byte-identical (D19) — the partial-lock rule is a wrapper, never an edit to the shipped rule. Listed explicitly so nobody "helpfully" widens it during apply. |
| `MainActivity.kt` | Modify | `partiallyLockedIds` memoized on `(entitlementTier, adUnlockState)` and passed to the selector sheet (D19). No other change. |
| `ui/groups/AffirmationGroup.kt` | Modify | **Delete `bienestar`/`autocuidado`/`fuerza_de_voluntad`** (D17). `defaultAffirmationGroups()` = `catalogUniverseGroups()` (14); `selectableAffirmationGroups()` = personalizadas + 14 = **15**. `PERSONALIZADAS_GROUP` untouched. **Plus `GroupBadge.PARTIALLY_LOCKED`** (D19). |
| `ui/groups/AffirmationGroupSelectorSheet.kt` | Modify | Sticky section headers + collapsed-by-default sections (UI concern; see below). **Plus (D19)**: new `partiallyLockedIds: Set<String> = emptySet()` param, `deriveCatalogBadge` at the `:213` call site, and the `PARTIALLY_LOCKED` branch in `AffirmationGroupAccessBadge`. Lock/toggle/dim logic untouched. |
| `ui/affirmations/CatalogBackgrounds.kt` | Create | Derived per-universe palette (D4). |
| `data/AffirmityAppState.kt` | Modify | **D18**: `defaultThematicGroupIds` filters on `isThematic` (tier kept as guard); `resolveSelectedGroupIds` gains the thematic-emptiness fallback. Plus: `source`/`collectionId` on `Affirmation`; catalog collector; `filteredAffirmations` access filter; `favoriteAffirmations` merge; `setTokenOverride` routing; `removeAffirmation` guard (D7/D10/D14). Plus: `timedUnlocks` collector, `requestAdUnlock`'s third param + `TIMED_REPEATABLE` grant branch, `AdUnitIds` third field (D16). |
| `firestore.rules` | Modify | 5 read-only catalog blocks + owner-only `catalogOverrides` + the new `timedUnlocks` block. **`users/{uid}/adUnlocks` is left byte-identical** (D16). |
| `functions/tools/seedCatalog.ts` | Create | Admin-SDK one-time publisher, marker-last (D12). |
| `functions/test/seedCatalog.test.ts` | Create | vitest + emulator: chunk boundaries, marker last, idempotent re-run. |
| `res/values/strings.xml`, `res/values-en/strings.xml` | Modify | **Add** `affirmation_group_badge_partial` per locale — es "Incluye Pro", en "Includes Pro" (D19). **Add** 14 group titles + 14 descriptions per locale (from `universes[].title`/`coreNeed`; `values/` is Spanish, `values-en/` English). **Delete** the 6 legacy strings per locale: `affirmation_group_bienestar_*`, `_autocuidado_*`, `_fuerza_*` (D17). |
| `app/src/test/…` | Create | See Testing Strategy. |
| `app/src/androidTest/…` | Create/Modify | DAO round-trips + `migrate8To9`. |

**Not created, contrary to the proposal:** `data/remote/CatalogSeedPlan.kt` (D12).

### Delivery impact of the closed decisions — read this before `sdd-tasks`

The proposal's 4-slice PR plan **does not survive these decisions unchanged.** All five closures add
work; none remove any. Stated plainly so the plan is re-cut deliberately rather than discovered
mid-apply:

| Closed decision | Net effect on the plan |
|---|---|
| **1. Full override sync surface** | Restores ~1 slice the previous revision proposed to defer, and it is *larger* than "the deferred part": it also converts `CatalogOverrideRepository` into a `DataSession` member (D9 revised), which touches the sealed interface, both session constructions, and every `AffirmityAppState` test that builds a session. |
| **2. `TIMED_REPEATABLE`** | The biggest addition, and it is **not** confined to the catalog: it modifies 8 existing files across `access/`, `data/local/`, `data/repository/`, `data/remote/`, `firestore.rules`, and `app/build.gradle.kts`, adds 2 new files, and adds a table to `MIGRATION_8_9`. It is also the **only** part of this change that touches shipped monetization code, so it carries the highest regression risk per line. |
| **3. Legacy group removal** | Small in production code (~20 deleted lines + 12 string resources) but non-trivial in tests: **4 suites** must have their group fixtures rebuilt, and two downstream behaviors shift (`proOnlyGroupIds` empties; the first-launch default selection changes shape). |
| **4. Default = all 14 selected (D18)** | The smallest of the five: **~4 production lines** in `AffirmityAppState.kt` (one filter clause, one fallback expression). Cost is almost entirely in tests — `ResolveSelectedGroupIdsTest` gains the thematic-emptiness cases, and it fixes a **latent Pro-user bug** (empty thematic selection after id removal) that no current test covers. Rides along with the D17 slice; does **not** justify its own PR. |
| **5. Partial-lock badge (D19)** | Self-contained and presentation-only: 1 enum constant, 2 new functions in a file D6 already creates, 1 generator constant, 1 string per locale, ~5 lines in the sheet, ~4 in `MainActivity`. Zero data-model, zero migration, zero rules impact, and `GroupAccessPolicy.kt` is deliberately not touched. **The natural home is the selector-UI slice**, alongside the 15-row sticky-header work — the two edit the same file and are reviewable together. |

Recommendation to `sdd-tasks`: **5 slices, not 4**, and cut `TIMED_REPEATABLE` out as its own
**first** slice, ahead of any catalog work. It is independently valuable, independently testable
against the existing `AccessResolutionTest`/rules suites, independently revertible, and it is the
only slice whose blast radius includes already-shipped paid features — reviewing it inside a
2712-affirmation catalog diff would be the worst possible way to look at it. The catalog taxonomy
slice then simply *consumes* `ProOrAdTimed(24)`. D11's measurement still makes the sanitization slice
smaller than the proposal budgeted (Open Question 3), but that saving no longer offsets the total.
**400-line budget risk is High**; chained PRs are the expected outcome.

### Selector at 15 groups — UI concern for `sdd-tasks`

`selectableAffirmationGroups()` goes from 4 to **15** (personalizadas + 14 universes). The 3 legacy
thematic groups are **deleted**, not retained (D17) — the user confirmed no real selections exist to
protect, which was the only reason the previous revision kept them. The sheet is already a
`LazyColumn` inside `fillMaxHeight(0.85f)`, so it scrolls today and nothing **breaks** at 15. What
degrades is findability: the Aplicar button is below the list, and the Favorites/Add-custom entry
cards get pushed to the bottom of a 15-row scroll.

Recommendation (structure only, no mockup): keep one flat `LazyColumn`; add `stickyHeader`
sections — *Mis afirmaciones* (personalizadas + the two entry cards, pinned first), then
*Temáticas*; and move `FavoritesEntryCard`/`AddCustomAffirmationsCard` **above** the group list.
No data-model change either way — this is presentation-only and can be sliced independently.
Search is explicitly **not** recommended at 15 items.

## Testing Strategy

Strict TDD is active. `app/src/test` has **no Robolectric** (`junit`, `json`, `mockito`,
`kotlinx-coroutines-test` only), so **every DAO round-trip is `androidTest`, not a JVM unit test** —
the same correction the favorites design had to make.

| Layer | What to test | Approach |
|---|---|---|
| Unit | **`ContentKeyTest` (D5, dedicated round-trip requirement).** No `wireName` in `ContentType.entries` contains `_`. `parse(storageKey)` round-trips for **all 226** real collection ids, driven off `catalogCollections()`. `fromWireName("affirmationGroup")` unaffected. `storageKey` satisfies `firestore.rules:71`'s identity. | JUnit 4, data-driven over the committed taxonomy — so a future id with a new punctuation class fails here, not in production. |
| Unit | **`mostRestrictive` (D6)**: the full 4×4 table; `LockedNeedsPro` absorbing; `ONE_TIME_TRIAL` beating `PER_USE`; `UnlockedByAd` provenance surviving; commutativity, associativity, `Unlocked` identity. | Pure JUnit. This is the security-relevant rule of the change — RED first. |
| Unit | **`catalogAccessDecision` (D6)**: `alwaysSelected` short-circuits before anything else; free collection in a Pro group ⇒ **locked** (the D6(a) hole, as an explicit regression guard); Pro collection in a Free group ⇒ locked; `collection == null` ⇒ group decision unchanged. | Pure JUnit with fixed `nowMillis` and a hand-built `AdUnlockState`. |
| Unit | **`CatalogTextSanitizer` (D11)**: `[`, `]`, `[]`, nested, unicode-adjacent → correct offsets; clean text → empty. **Plus a smoke assertion over the committed asset**: zero illegal brackets across all 2712 texts. | Asset path injected via a Gradle `systemProperty`, not a relative `File(...)` — unit-test cwd is not contractual. |
| Unit | **ID scheme (D3)**: every generated id matches `^cat_[a-z0-9_]+(\.[a-z0-9_]+)+$`; all 2712 unique; **no id is a valid `UUID`** (`runCatching { UUID.fromString(it) }.isFailure` over the whole set); no catalog `groupId` equals `PERSONALIZADAS_GROUP_ID` or any legacy group id. | Data-driven over the committed asset. Turns "no collision" from an argument into an assertion. |
| Unit | **`CatalogAssetParser`**: duplicate id, unknown `collectionId`, illegal bracket, and `free`+non-null-hours each throw with a message naming the offending id. Round-trip on a 3-row fixture. | Hand-written JSON fixtures (repo convention over heavy mocking). |
| Unit | **`CatalogSeeder` (D13)**: seeds when the marker is absent/stale; **no-ops** when current; a throwing `saveSeededCatalogVersion` still leaves the rows committed and re-seeds cleanly on the next call; the marker is written **after** the DAO call (asserted by call order on a recording fake). | `runTest` + recording fake DAO/prefs. |
| Unit | **`filteredAffirmations` (D7)**: owned rows keep the existing filter verbatim (regression); a locked-collection catalog row is absent; the same row appears for a Pro tier; deselecting a group removes its catalog rows. | Construct `AffirmityAppState` directly with fake repositories, per `AffirmityAppStateSwapTest`. |
| Unit | **`favoriteAffirmations` cross-space (D10)**: a personal id and a catalog id favorited together resolve in recency order; an id in neither space drops out; **a favorited catalog row whose collection is locked STILL appears** (Open Question 4's chosen behavior, pinned by test so a reversal is a deliberate edit). | Fake `favorites` + fake catalog repository. |
| Unit | **Write routing (D14)**: `setTokenOverride("cat_…")` hits `catalogOverrides` and **never** `ready().affirmations`; `setTokenOverride(uuid)` unchanged; **`removeAffirmation("cat_…")` performs zero repository calls** (the Remote-write-tombstone guard). | Recording fakes with a shared call log; asserts absence, not just presence. |
| Unit | **`resolveAccess` TIMED_REPEATABLE (D16)**: no record ⇒ `LockedAdUnlockable(TIMED_REPEATABLE)`; live record ⇒ `UnlockedByAd`; **expired record ⇒ `LockedAdUnlockable` again** (the one line that differs from `ONE_TIME_TRIAL`); boundary `now == expiresAtMillis` is expired (`hasExpired` uses `>=`). **Plus a regression guard: the entire existing `ONE_TIME_TRIAL` table still passes byte-for-byte**, including "spent ⇒ `LockedNeedsPro`". | Extend `AccessResolutionTest`. RED first — this is the security-relevant half of D16. |
| Unit | **`ContentAccess` invariant (D16)**: `TIMED_REPEATABLE` without hours throws; any other policy *with* hours throws; `ProOrAdTimed(24)` constructs; `Free`/`Pro`/`ProOrAdPerUse`/`ProOrAdTrial` still construct unchanged. | Pure JUnit. |
| Unit | **Timed grant routing (D16)**: `requestAdUnlock(key, TIMED_REPEATABLE, 24)` on `Earned` calls `grantTimedUnlock` with `expiresAtMillis == grantedAt + 86_400_000` and **never** `grantDurableUnlock`; a null window grants **nothing**; `ONE_TIME_TRIAL` and `PER_USE` routing is unchanged (regression). `adUnitIdFor(TIMED_REPEATABLE)` returns the timed unit, and blank ⇒ `null`. | Recording fakes with a shared call log; assert absence, not just presence. |
| Unit | **Legacy-group removal fallout (D17)**: `selectableAffirmationGroups()` has 15 entries, contains `personalizadas`, and contains **none** of the 3 legacy ids; `resolveSelectedGroupIds(persisted = setOf("bienestar"), …)` drops it and `EntitlementResolution` restores a valid default selection (the waived-fallback behavior, pinned so it is a decision and not an accident). **Fixture repair is part of this**: `GroupAccessPolicyTest`, `AdUnlockEndToEndTest`, `AffirmityAppStateAdFunnelAnalyticsTest`, and `AccessDecisionPurityAnalyticsTest` currently do `defaultAffirmationGroups().first { it.id == "bienestar" \|\| … }` and will throw `NoSuchElementException`. Repoint them to **locally constructed `AffirmationGroup` fixtures** with the Free/Pro/PER_USE shapes they need — production data no longer supplies a Pro or PER_USE group, and tests should not have depended on it. Tests that use the legacy ids as *opaque strings* (`ContentKeyTest`, `AccessResolutionTest`, `FirestorePathsTest`, `FirestoreMappersTest`, `AdUnlockDaoTest`, `AdUnlockMigrationTest`, `ResolveSelectedGroupIdsTest`) are **unaffected** and must not be churned. | JUnit; the fixture repair is mechanical but must be enumerated as tasks, not discovered during apply. |
| Unit | **Default selection + emptiness fallback (D18)**, extending `ResolveSelectedGroupIdsTest` (already Android-free): `persisted = null` ⇒ all 14 universes **plus** `personalizadas`; `persisted = {"personalizadas","bienestar"}` with `knownIds` = the 15 new ids ⇒ falls back to the 14 (the tier-independent repair — **the case that would have left a Pro user with an empty thematic feed**); `persisted = {"personalizadas","self_worth"}` ⇒ returned **verbatim**, fallback does NOT fire (the "existing users are untouched" guard); `persisted = emptySet()` ⇒ fallback fires (distinct from `null`, same outcome, asserted so the distinction is not lost). Plus a wiring assertion that `defaultAffirmationGroups().filter { it.isThematic && requiredTier == FREE }` has size 14 and equals the full universe id set. | Pure JUnit. Both new branches must be RED first — the emptiness fallback is a real bug fix, not a refactor. |
| Unit | **`deriveCatalogBadge` (D19)**, new `CatalogAccessPolicyTest`: unlocked group + `isPartiallyLocked = true` ⇒ `PARTIALLY_LOCKED`; unlocked + `false` ⇒ `null`; **locked group + `true` ⇒ `PREMIUM`/`AD_UNLOCK`, never `PARTIALLY_LOCKED`** (a fully-locked row must not be softened); `PERSONALIZADAS_GROUP` + `true` ⇒ its `PREMIUM` override (`alwaysSelected` is excluded twice — by `deriveBadge`'s first branch and by the `takeIf`). **Regression, mandatory: the entire existing `GroupAccessPolicyTest` badge table still passes byte-for-byte**, since `deriveBadge` is not modified. | Pure JUnit. |
| Unit | **`partiallyLockedGroupIds` (D19)** — the performance contract, asserted rather than asserted-in-prose: `tier = PRO` ⇒ `emptySet()` **and zero collection resolutions** (counted via an instrumented fake clock/grant probe, or by asserting the result is `emptySet()` for a taxonomy fixture whose every collection is Pro); `FREE` + empty `AdUnlockState` ⇒ exactly `CATALOG_GATED_GROUP_IDS`; `FREE` + a live grant covering the only gated collection of one universe ⇒ that universe **drops out**, all others remain; an **expired** timed grant ⇒ the universe comes **back**. Plus a generated-data consistency assertion: `CATALOG_GATED_GROUP_IDS` equals the set derived from `catalogCollections()` at runtime — pins generator and app in agreement so a stale committed constant fails here, not in the UI. | Pure JUnit, data-driven off the committed taxonomy. |
| Unit | **`CatalogBackgrounds` (D4)**: deterministic for a given id across calls; ids within a universe span the palette; every universe id resolves. | Pure JUnit. |
| Node (`cd functions && npm test`) | **`seedCatalog.ts` (D12)**: 2712+314 writes chunk at ≤450 ops; the version marker is the **last op of the last chunk**; a second run is a no-op-equivalent (`merge: true`); a mid-run abort leaves no marker. | vitest + the existing Firestore emulator harness. |
| Node (`npm run test:rules`) | **Rules**: unauthenticated read of `catalogAffirmations` **succeeds**; any client write **fails**; `users/{a}/catalogOverrides` is unreadable and unwritable by `{b}`; owner read/write succeeds. **New for D16**: owner may `create` **and `update`** `users/{uid}/timedUnlocks/{key}`; `delete` fails; a doc whose id disagrees with `contentType_contentId` fails; a null/absent `expiresAtMillis` fails; `expiresAtMillis <= grantedAtMillis` fails; `{b}` cannot touch `{a}`'s. **Regression, mandatory**: the full existing `users/{uid}/adUnlocks` suite still passes, in particular **`update` and `delete` still fail** — the non-repeatability guarantee D16 refuses to weaken. | The existing `@firebase/rules-unit-testing` suite. |
| E2E (`connectedDebugAndroidTest`) | **DAO round-trips**: `observeByGroupIds` ordering and empty-set behavior; `getByIds` across mixed spaces; `replaceAll` atomicity; override upsert/delete. **`migrate8To9`**: a v8 DB with affirmations + favorites migrates to v9 with **all three** new tables present, empty, **both indices created**, and every pre-existing column (incl. `overrides`) untouched. **`TimedAdUnlockDao`**: a second insert for the same `contentKey` **REPLACES** (the re-earn), which is the exact inverse of `AdUnlockDao.insertIfAbsent` — assert both in the same suite so the contrast is impossible to miss, and assert a pre-existing `ad_unlock` row is untouched by the migration. | `MigrationTestHelper` + in-memory Room, per `AffirmityDatabaseMigrationTest`. |
| **Manual / on-device** | (1) **D8's empty subtitle**: a catalog card renders with no layout gap or stray spacing — the first content in the app with a blank subtitle. (2) Cold-start seed of 2712 rows does not visibly block first paint. (3) The 15-row selector is navigable and Aplicar is reachable. (3b) **D19**: a partially-locked row shows the new badge, is still **checkable**, and is **not** dimmed — the visual must not read as "locked"; and the emitted `CATALOG_GATED_GROUP_IDS` is eyeballed for the uniformity caveat before merge. (3c) **D18**: a fresh install (app data cleared) opens with all 14 universes checked. (4) A Free user sees only free-collection content in a mixed group, and the locked ones are upsold in the sheet, not in the pager. (5) **D16 end-to-end**: watch an ad on a `TIMED_REPEATABLE` collection, confirm it unlocks; move the device clock past the window; confirm it re-locks **and re-offers the ad** (not `LockedNeedsPro`); watch again and confirm the second grant persists. | Physical device. `sdd-tasks` must carry (1) and (2) as **blocking acceptance items before merge** — neither is provable by a green unit suite. |

## Threat Matrix

- **New public read surface.** `catalogAffirmations` is the first `allow read: if true` document in
  `firestore.rules`. Accepted deliberately: it contains zero user data and ships verbatim in every
  APK (D2), so the exfiltration ceiling is "content the attacker already has". The **write** side is
  `if false` at every one of the five paths, with no exception for the owner, and the seeder writes
  through the Admin SDK which bypasses rules entirely — so there is no client code path, debug or
  release, that can mutate shared content (a direct consequence of rejecting the in-app admin
  seeder in D12).
- **Injection.** Every value written to SQLite goes through bound Room `@Query`/`@Insert` parameters;
  nothing is concatenated. Catalog text is rendered as Compose `Text`, never as markup. The one
  externally-derived string that becomes an *identifier* is the `cat_*` id used as a Firestore doc id
  in `catalogOverrideDoc` — its legality (no `/`, not `.`/`..`, not `__.*__`) is asserted for all
  2712 ids at build time and again in a unit test.
- **Blast radius / quota.** Steady-state Firestore cost is **one document read per refresh check**,
  independent of user count and corpus size (D2). The seeder runs once, manually, by one developer.
  `catalog_affirmations` is a fixed 2712 rows (~600 KB on device); `catalog_affirmation_overrides`
  is bounded by the tokens the user actually edits — **zero in v1.0.0**, by measurement (D11).
- **Privacy.** `users/{uid}/catalogOverrides` values are free text a user typed into an affirmation
  and are therefore PII-representable. They are owner-only by rules, never logged, and **never sent
  to analytics** — the same posture `setTokenOverride` already holds (placeholders design D14). No
  analytics event is added anywhere in this change.
- **Self-granting.** `ContentType.AFFIRMATION_COLLECTION` extends the existing client-writable
  `users/{uid}/adUnlocks` surface to 226 new keys. This inherits — and does not widen — the recorded
  weakening from the ad-unlock design: a modified client can self-grant an ad unlock for
  low-value content. Rules still enforce owner-only, closed field set, create-only, and the
  `contentType + '_' + contentId` doc-id identity, which the new wireName satisfies (D5).
- **New client-updatable surface (`users/{uid}/timedUnlocks`).** This is the only path in
  `firestore.rules` where a client may `update` an existing grant doc, and it exists because
  re-earning an expired window *is* an overwrite (D16). The weakening is bounded three ways: it is a
  **separate collection**, so `users/{uid}/adUnlocks` keeps `allow update, delete: if false` verbatim
  and a spent `ONE_TIME_TRIAL` remains unforgeable; `delete` is denied here too, so a client cannot
  erase an *active* window to restart it early; and the same closed field set, owner-only check, and
  doc-id/field identity apply. The residual exposure — a modified client extending its own 24h
  window on ad-supported content — is the *already-accepted* self-granting weakening from the
  ad-unlock design, not a new class of risk.

## Migration / Rollout

**Room 8 → 9.** Purely additive: **three** `CREATE TABLE IF NOT EXISTS` (`catalog_affirmations`,
`catalog_affirmation_overrides`, `timed_ad_unlock`) plus two `CREATE INDEX IF NOT EXISTS`. Same shape as `MIGRATION_7_8`/`MIGRATION_5_6`. No existing table, column, or row is read,
altered, or backfilled, so every pre-change read path is bit-identical. `app/schemas/9.json` must be
generated and committed and `AffirmityDatabaseMigrationTest` extended before merge. **The migration
does not seed** — populating 2712 rows happens in `CatalogSeeder` off the open path (D13).

**Downgrade.** No `MIGRATION_9_8` ships (D15). `fallbackToDestructiveMigrationOnDowngrade` stays
disabled — enabling it would silently wipe affirmations, completions, moods, healer rows and
favorites, which is categorically worse than the problem. Pre-release rollback is free (revert the
branch). Post-release rollback is the **partial** rollback at schema v9: drop the 14 universe groups
from `selectableAffirmationGroups()` and the catalog branch of `filteredAffirmations`. The catalog
becomes unreachable with zero data loss; re-enabling is the inverse edit. **D17 sharpens the cost of
that rollback**: with the legacy groups deleted, dropping the 14 universes leaves `personalizadas`
as the only selectable group, so the partial rollback degrades to "custom affirmations only" rather
than "back to the 3 old themes". Acceptable — it is a recovery path, not a supported state — but it
means the forward fix is now the strongly preferred response, and it should be recorded as such. A true code revert to a
v8-compiled build on a migrated device is **not supported**; the recovery path is a forward fix.

**Existing users' group selection — no migration, by explicit decision (D17).** The 3 legacy groups
are deleted outright. `resolveSelectedGroupIds` drops ids that are no longer in `knownGroupIds` and
force-includes `personalizadas`; `EntitlementResolution` then re-satisfies the minimum-selection
invariant from `defaultThematicIds`, which is now the 14 universes. So a hypothetical device holding
`{"personalizadas", "bienestar"}` lands on `{"personalizadas"} + defaultThematicIds` — a valid,
non-empty selection, never a blank feed. **No compensating remap, alias, or `bienestar → <universe>`
migration is written**, because the user confirmed no real user data exists: the previous revision's
"deleting them would wipe a live selection" objection has no live selection to protect. This is a
consciously waived safety net, and it is pinned by the D17 unit test so a future reader sees a
decision rather than an omission. `personalizadas` is unaffected throughout.

**First-launch default selection: ALL 14 universes, decided (D18, Open Question 6 CLOSED).**
`defaultThematicGroupIds` was "the FREE legacy groups" (i.e. `{bienestar}`); it becomes all 14
universes. The user chose the all-selected default over an opt-in empty state or a curated subset:
the first feed deliberately mixes every theme, and the selector is where a user narrows it. The
filter is re-expressed as `isThematic && requiredTier == FREE` so this reads as a decision rather
than as an artifact of every universe happening to be group-level `Free`.

**This is a new-install default only, and nothing here is retroactive.** `defaultThematicIds` is
consumed exclusively by `resolveSelectedGroupIds`'s `?:` branch, which is reachable only when
`observeSelectedGroupIds()` emits `null` — i.e. the DataStore key was never written. Existing users
keep their committed selection verbatim. The one new behaviour that *can* affect an existing device
is the thematic-emptiness fallback added in D18, and it fires only when a persisted selection has
already been reduced to nothing thematic (the legacy-id case above) — repairing a broken state, not
overriding a chosen one. It also fixes the tier asymmetry documented in D17: that recovery
previously lived only on the `AccessTier.FREE` branch, so a Pro user on the same device would have
been left with a custom-affirmations-only feed.

**Sign-in / sign-out.** The catalog is auth-independent and its collector deliberately does not
participate in the session swap (D9), so signing in or out neither re-fetches nor clears it. Favorites
on catalog ids survive a swap intact, because catalog ids are device-independent constants — this is
strictly **better** than the personal-affirmation case, where the favorites design documented that
cross-device ids orphan.

**Content updates.** A future `catalog.v2.json` is: regenerate (gate re-runs) → bump the bundled
version → new APK reseeds via `replaceAll` → optionally run `seedCatalog.ts` to publish the same
content to Firestore for users on the older APK. Because ids are the source ids verbatim (D3),
favorites and overrides survive a content update for every affirmation that still exists; the source's
"archive not delete" principle means they normally all do.

## Open Questions

- [x] **1. CLOSED — ship the FULL `catalogOverrides` sync surface now.** *(user decision, overrides the
      previous recommendation)* The recommendation was to ship only the Room table and defer the
      Firestore mirror + rules, since **zero of the 2712 texts contain a `[` token** (measured, D11) so
      the table is structurally empty in v1.0.0. **Rejected by the user.** This change ships the local
      table, the `users/{uid}/catalogOverrides/{catalogAffirmationId}` mirror, its owner-only rules
      block, and the merge logic, exactly as the proposal specified — which is also what
      `specs/catalog-token-overrides/spec.md` already requires, so the design now matches the spec
      rather than the spec being narrowed to the design. Concrete consequences, all folded in above:
      `CatalogOverrideRepository` becomes a `DataSession` member (D9, revised) with Room and Firestore
      implementations; `FirestoreCatalogOverrideRepository.kt` is a new file; the overrides collector
      is `session.flatMapLatest`; `setTokenOverride` routes through `ready().catalogOverrides`; the
      rules block ships and is covered by `npm run test:rules`. Accepted cost: a slice of code with no
      user-observable behavior until token-bearing content lands.
- [x] **2. CLOSED — build `AdUnlockPolicy.TIMED_REPEATABLE` in THIS change.** *(user decision, overrides
      the previous recommendation)* The recommendation was the conservative `ONE_TIME_TRIAL` mapping,
      deferring real repeating-window support. **Rejected by the user.** Designed in full as **D16**:
      a payload-free enum constant, the window on `ContentAccess.unlockWindowHours`, and — the part
      that makes this bigger than one enum case — a **separate durable store**
      (`timed_ad_unlock` table + `users/{uid}/timedUnlocks`), because both existing grant stores are
      deliberately create-only (`@Insert(onConflict = IGNORE)` and `allow update, delete: if false`)
      and re-earning a window is by definition an overwrite. `resolveAccess` gains one branch that
      differs from `ONE_TIME_TRIAL` in exactly one line. The 75 `rewardedUnlockHours: 24` collections
      map to `ContentAccess.ProOrAdTimed(24)`, generated from the source value verbatim. **Existing
      `ONE_TIME_TRIAL` usages (meditations, custom-affirmation slots, `PERSONALIZADAS_GROUP`) are
      unaffected** — the new `ContentAccess` param is trailing-with-default, the `ONE_TIME_TRIAL`
      resolution branch is untouched, `adUnlocks` rules stay byte-identical, and both facts are pinned
      by regression tests. Remaining judgement call for `sdd-tasks`, not a blocker: whether to
      provision a third AdMob unit or fall back to the `oneTimeTrial` unit (D16 recommends the
      fallback so release builds are not blocked on a new secret).
- [ ] **3. The sanitization risk is measurably lower than the proposal assumed — re-slice?**
      *(planning, low risk)* The proposal ranks bracket collision as its top **High** risk with
      "strip/rewrite every hit" mitigation. Measurement says the corpus is already clean, so D11 ships
      a verification-only gate and no rewrite logic. `sdd-tasks` should treat "sanitization + seed
      artifact + ID scheme" as materially smaller than budgeted. Flagged rather than silently
      re-planned.
- [ ] **4. Should a favorited catalog affirmation stay visible after a Pro downgrade?** *(product)*
      D10 says **yes** — `favoriteAffirmations` is access-unfiltered, matching the existing
      "unfiltered by group on purpose" rule and the grandfathering posture `PERSONALIZADAS_GROUP`
      already uses for custom affirmations. But it does mean a lapsed user retains read access to Pro
      content they favorited. Pinned by a unit test so a reversal is a deliberate edit, not a
      regression. Confirm.
- [x] **5. CLOSED — delete all 3 legacy groups entirely, with NO migration or fallback.** *(user
      decision)* The user's own framing: the previous groups "weren't important at all, they were just
      made to have something to build the app with — use everything about the new groups and
      affirmations", and no real user data exists. So `bienestar`, `autocuidado`, and
      `fuerza_de_voluntad` are removed from `AffirmationGroup.kt` (hence from
      `defaultAffirmationGroups()` and `selectableAffirmationGroups()`, which drops to 15), their 6
      string resources are deleted from both locales, and **no alias, remap, or hidden-but-honored
      path is written**. `GroupAccessPolicy.kt` does **not** reference the ids — it is fully
      declaration-driven and needs no edit. There is no seed data or DB row keyed to a group id
      (`AffirmationEntity.groupId` is free-form and only user-created rows exist). Designed as **D17**;
      `resolveSelectedGroupIds`'s drop-unknown-ids behavior is accepted **as-is with the safety net
      explicitly waived**, and the resulting recovery path is pinned by a unit test. **`personalizadas`
      is NOT one of the three and stays untouched** — it is defined separately, is `alwaysSelected`, and
      is prepended by `selectableAffirmationGroups()`. The real cost is **test-fixture repair in four
      suites** that source Free/Pro/PER_USE group fixtures from production data by literal id; see
      Testing Strategy.
- [x] **6. CLOSED — a fresh install starts with ALL 14 universe groups selected.** *(user decision)*
      The alternatives were an opt-in empty state (`personalizadas` only) and a curated starter
      subset; both rejected. `personalizadas` remains always-selected on top, so a new install lands
      on 15 checked rows. Designed as **D18**. **Two findings the closure surfaced, both folded in:**
      (i) the existing `defaultThematicGroupIds` filter (`AffirmityAppState.kt:1289-1291`) *already*
      produces all 14 once every universe is declared group-level `Free`, so the chosen default would
      have shipped by accident — it is now re-expressed as `isThematic && requiredTier == FREE` so it
      is a stated product decision, with the tier clause kept only as the "never default to a group
      `deselectLockedGroups` would strip" guard; (ii) **a latent bug**: the minimum-selection recovery
      lived only inside `if (entitlement.tier == AccessTier.FREE)` (`AffirmityAppState.kt:809`), so a
      **Pro** user whose persisted ids were all dropped as unknown landed on a thematically empty
      feed. D18 moves the invariant into the pure `resolveSelectedGroupIds`, making it
      tier-independent and unit-testable without Android. **Existing users are unaffected by the new
      default** — `defaultThematicIds` is reachable only via the `?:` on a `null` persisted value, and
      `observeSelectedGroupIds()` returns `null` only when the DataStore key was never written.
- [x] **7. CLOSED — ship a real partial-lock badge NOW; do not defer it.** *(user decision, overrides
      the previous revision)* The previous revision recorded the gap and punted: all 14 universes are
      group-level `Free`, so `proOnlyGroupIds` empties and `deriveBadge` returns `null` for every
      universe row, leaving ~66% Pro-gated content with no visual signal. **Rejected by the user.**
      Designed as **D19**: a new `GroupBadge.PARTIALLY_LOCKED`, a `deriveCatalogBadge` wrapper that
      leaves the shipped `deriveBadge` **byte-identical**, and `partiallyLockedGroupIds(tier, grants,
      now)` whose cost is staged — `PRO` returns empty on one comparison, a Free user with no
      collection grants gets the **generated** `CATALOG_GATED_GROUP_IDS` with zero access resolution,
      and only a Free user actually holding a collection grant pays a resolve over the ≤150 gated
      collections. Memoized once per sheet on `(tier, adUnlockState)`, never per row. **Scoped to the
      selector**: no new screen, and `isLocked`/`isToggleable`/the row dim are untouched, because a
      partially-locked group is genuinely selectable. Two accepted caveats, both carried as acceptance
      items rather than hidden: the badge may be **uniform across all 14** on day one (the generator
      emits the measured set — look at it before merge), and it can lag an expiring
      `TIMED_REPEATABLE` window by one recomposition (cosmetic only; feed filtering resolves live).
