# Pre-Launch Audit — "Recta final affirmity"

Audit of the 8 items from Nahuel's pre-launch checklist against the actual codebase, as of `feat/ios-billing-server-verification`. Each item lists status, evidence, gap, and a rough effort estimate.

---

## 1. Pantalla holding affirmation — compartir, like, hide

**Status:** 🟡 Partial

**Evidence:**
- Like/favorite: fully implemented. `ui/affirmations/AffirmationsScreen.kt:81-330` — double-tap or icon-tap toggles favorite, with an animated heart burst (`favoriteScale`).
- Share: **not found anywhere in the codebase.** No `Intent.ACTION_SEND`, no `Intent.createChooser` call in the whole `app/` module.
- Hide: **no such concept exists.** The only related feature is `MyAffirmationsScreen.onDeleteAffirmation`, which deletes a user-*created* custom affirmation — it does not let the user hide a *catalog* affirmation from their rotation.

**Gap:** Add a share action (build a text `Intent.ACTION_SEND` from the current card's text) and a hide/skip-from-rotation action (needs a new persisted "hidden ids" set, likely in `TrackerPreferences` or a Room table, consulted wherever the affirmation feed picks the next card).

**Effort:** Small (share) + Medium (hide — needs new persistence + feed filtering).

---

## 2. Meditaciones personales (toggle)

**Status:** ❌ Missing

**Evidence:** `ui/meditation/customization/` only exposes per-session *parameter* customization (duration, rounds, breathing pattern) via `CustomizationField` — confirmed in `MeditationCatalog.kt` (32+ `CustomizationField.Toggle`/`IntSlider` usages, all session knobs, not content). There is no concept of a user-authored/personal meditation distinct from the 38 fixed catalog entries, and no toggle switching between "catalog" and "personal" anywhere in `ui/meditation`.
- Compare with affirmations, which DO have this: `MyAffirmationsScreen.kt` lets a user add/import their own affirmations alongside catalog ones. Meditation has no equivalent.

**Gap:** This is a net-new feature, not a UI toggle bug. Needs: a data model for a user-defined meditation (probably a subset of the existing `MeditationDefinition` authoring primitives), a creation UI, storage, and a toggle/section in the Discover screen.

**Effort:** Large — this is closer to a new feature than a fix.

---

## 3. Combos (ej. ocasiones especiales)

**Status:** ❌ Missing

**Evidence:** No matches for `combo`, `occasion`, or `bundle` in `MeditationCatalog.kt`, `MeditationTreeDerivation.kt`, or `AffirmationGroup.kt`. No grouping concept beyond the existing category taxonomy (`MeditationCatalogEntry.categoryRes`) and affirmation groups (`AffirmationGroup`), which are flat, single-purpose collections — not "occasion combos" mixing meditation + affirmations + duration presets.

**Gap:** No groundwork exists at all. Would need a new domain concept (`MeditationCombo` or similar) referencing multiple catalog entries/groups, plus UI to browse/launch a combo.

**Effort:** Large — genuinely new feature, not a fix.

---

## 4. Meditaciones que no son claras (falta descripción)

**Status:** 🟡 Partial

**Evidence:**
- Data model already has it: `MeditationCatalogEntry.descriptionRes: Int` (`MeditationCatalogEntry.kt:22`), and **all 38 catalog entries populate it** (`MeditationCatalog.kt`, one `descriptionRes = R.string....` per entry).
- But it's only rendered in ONE place: `MeditationCustomizationScreen.kt:92`.
- The Discover list card (`MeditationDiscoverCard` in `MeditationScreen.kt:416-429`) shows only title + `"{duration} min • {category}"` — no description.
- Entries whose `customizationFields` is empty skip the customization screen entirely (`MainActivity.kt:1001-1002`, `decideMeditationLaunchStep`) and go straight into the guided session — so for those entries, the already-written description is **never shown to the user at all**.

**Gap:** Not a content problem (descriptions exist and are authored) — it's a surfacing problem. Two options: (a) show description text in the Discover card/a tap-to-expand row, or (b) always route through a summary step (reusing/repurposing the customization screen) before starting, even for zero-field entries.

**Effort:** Small — this is a display wiring fix, not new content or new domain logic.

---

## 5. Meditaciones recientes

**Status:** ❌ Missing

**Evidence:** No recency tracking exists for meditation entries anywhere in `ui/meditation` or `data/`. Compare with `Repositories.kt:38`, which documents favorite affirmation ids as "ordered most recently favorited first" — that pattern exists for affirmations but has no meditation analogue. `recordMeditationCompleted` (used for streaks) does not persist which *entry* was played, only that *a* meditation was completed that day.

**Gap:** Needs a new persisted list of `(entryId, timestamp)` (DataStore or Room), updated on session start or completion, plus a "Recent" section/sort in `MeditationScreen.kt`'s Discover list.

**Effort:** Medium.

---

## 6. No completar meditación con puro skip

**Status:** ❌ Missing (confirmed abuse path)

**Evidence:**
- `MeditationEngine.kt:72-75` — `MeditationEvent.Next`/`Skip` calls `exitCurrentPhaseAndAdvance()`, the **exact same function** a natural phase-timer completion calls (`MeditationEngine.kt:101-107`). There is no distinction in the engine between "phase timed out naturally" and "user mashed skip."
- Reaching the end via all-skips still fires `AdvanceResult.SessionCompleted` → `EndSession(SessionEndReason.Completed)` (`MeditationEngine.kt:150-160`) — identical terminal state to an honest session.
- `MainActivity.kt:436` — `handleGuidedMeditationSessionEnded`: `if (reason == SessionEndReason.Completed) recordMeditationCompleted()`. **No elapsed-time or skip-count guard whatsoever.** `elapsedSeconds` is captured and passed through, but only used for the analytics event (`AnalyticsEvent.MeditationCompleted(..., elapsedSeconds)`) — never checked against a minimum before crediting the streak.

**Gap:** A user can skip through an entire multi-minute guided meditation in under a second and get full streak credit. Fix needs either: a minimum-elapsed-time threshold before `recordMeditationCompleted()` fires, or tracking skip count/ratio and gating completion credit on it.

**Effort:** Small–Medium (the check itself is small; deciding the right threshold/UX for "we didn't count that" needs a product decision).

---

## 7. Sonidos durante la meditación

**Status:** ✅ Done (uncommitted) for the cue chime — 🟡 Partial overall

**Evidence:** Implemented earlier in this session (uncommitted, current branch):
- `TrackerPreferences.kt` — `observeMeditationCueSoundEnabled()` / `saveMeditationCueSoundEnabled()`, DataStore-backed, defaults to `true`.
- `GuidedMeditationAudioExecutor.kt` — `playSound()` now checks `isCueSoundEnabled()` before playing the one-shot `SOUND`-channel chime (`meditation_gong.mp3`).
- `GuidedMeditationScreen.kt` — `VolumeUp`/`VolumeOff` `IconButton`, top-right corner, live-toggles via `rememberUpdatedState` without rebuilding the session.
- `MainActivity.kt` — wires persistence + passes state down.

**Gap:** This only covers the phase-transition chime. There is **no volume control at all** for the ambient bed (`StartAmbient`) or the spoken voice guidance (`PlayVoice`) — confirmed via `rg` for "Slider"/"volume" in `ui/meditation`: the only sliders found are `CustomizationField.IntSlider` for duration/rounds, not audio levels. If ambient/voice ever need independent volume control, that's still fully unbuilt.

**Effort:** Cue toggle: done. Ambient/voice volume control (if wanted): Medium.

---

## 8. Costos del premium

**Status:** ⚙️ Config-only (not a code task)

**Evidence:**
- `BillingService.kt:55` — `proSubscriptionProductId` is passed in as `"pro"` (a placeholder, `MainActivity.kt:1624` `PRO_SUBSCRIPTION_PRODUCT_ID`), with base plan ids `"monthly"`/`"annual"` expected (`BillingService.kt:120-123`).
- `refreshProductDetails()` (`BillingService.kt:105-124`) queries Play Billing Library live and only populates `_monthlyOffer`/`_annualOffer` if the corresponding base plan actually exists on Play's side (`pricedOfferOrNull`, `BillingService.kt:126-130`) — no hardcoded price anywhere in the code.
- `PaywallSheet.kt:34-89` — takes `monthlyPriceLabel`/`annualPriceLabel` as nullable strings; both CTA buttons stay `enabled = false` and show no price copy until Billing resolves them. Confirmed: **no code change needed once Play Console is configured** — the code is already correctly wired to render whatever Play Console returns.

**Gap:** Purely external: create the "pro" subscription product in Play Console, add "monthly" and "annual" base plans, set prices per region, and get the app into at least Internal Testing so `BillingClient` can query real product details.

**Effort:** Config-only — no engineering effort, but blocks real end-to-end testing of the paywall until done.

---

## Prioritized Punch List

**Blocks first release (real bugs/gaps a user will hit):**
1. **#6 — Skip abuse.** Silent streak/analytics integrity gap. Cheapest fix on this list and the one most likely to visibly embarrass the app if noticed.
2. **#8 — Premium pricing.** Not a code gap, but the paywall is literally non-functional (no prices, disabled buttons) until Play Console is set up — needs doing before *any* premium-path QA is meaningful.
3. **#1 — Share/hide.** Share is a small, expected feature for an affirmations app; its total absence will read as unfinished. Hide is lower priority than share.
4. **#4 — Missing descriptions on Discover.** Cheap fix, real content already exists — high value-to-effort ratio, and directly addresses "meditations that are unclear."

**Nice-to-have polish (can ship without, iterate after):**
5. **#7 — Ambient/voice volume.** The cue-mute toggle already shipped; extending to full volume control is a "nice, not blocking" enhancement.
6. **#5 — Recent meditations.** Pure discoverability polish, no correctness or trust issue.
7. **#2 — Personal meditations.** Large net-new feature — treat as a post-launch roadmap item, not a pre-launch blocker.
8. **#3 — Combos.** Same as #2: a genuinely new feature area, not a fix. Push to post-launch.
