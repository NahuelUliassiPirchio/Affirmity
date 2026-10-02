# Round Interstitial Specification

## Purpose

Defines when the app shows an interstitial ad after the user completes a round of their affirmations feed.

## Requirements

### Requirement: Round completion

A round MUST complete when the user has settled on every distinct affirmation of the current feed, the initial page included, and the feed holds at least 10 affirmations (MIN_ROUND_SIZE). Revisiting an affirmation (backward swipe, wrap-around) MUST NOT count twice. Each round MUST complete exactly once. Progress MUST reset when the feed's id list changes (hide, sources, randomize or seed, size change) and MUST restart after a completed round.

#### Scenario: Full feed seen

- GIVEN a feed of 10 affirmations and the user is on the first one
- WHEN the user settles on the other 9 at least once each
- THEN the round completes once

#### Scenario: Small feed

- GIVEN a feed of 9 affirmations
- WHEN the user cycles through it any number of times
- THEN no round completes

#### Scenario: Feed changes mid-round

- GIVEN the user has seen 9 of 10 affirmations
- WHEN the user hides one or toggles randomize
- THEN progress is discarded and the round must be walked again

### Requirement: Interstitial gating

On every round completion a free-tier user MUST be shown one interstitial, with no minimum gap between two of them: finishing a round, watching the ad and finishing the next round shows another ad. Pro users MUST never see it.

#### Scenario: Free user

- GIVEN a free user and a loaded ad
- WHEN a round completes
- THEN one interstitial is shown

#### Scenario: Pro user

- GIVEN a Pro user
- WHEN a round completes
- THEN no interstitial is requested or shown

#### Scenario: Back-to-back rounds

- GIVEN a free user who just completed a round and saw the interstitial
- WHEN the user completes the next round, however soon
- THEN another interstitial is shown (if one is loaded)

### Requirement: Silent degradation

The interstitial MUST NOT block, delay or visibly alter the feed. Consent MUST be checked passively (`canRequestAds()`); a consent form MUST NOT be shown. If consent is missing, the ad is not loaded, the app is backgrounded, or loading or showing fails, nothing visible happens and loads are throttled (no tight retry loop).

#### Scenario: No consent

- GIVEN `canRequestAds()` is false
- WHEN a round completes
- THEN nothing is shown and no consent form appears

#### Scenario: Ad not loaded

- GIVEN no ad finished loading
- WHEN a round completes
- THEN the feed is unaffected and a throttled preload is requested for the next round

### Requirement: Entitlement-resolved gate

Nothing ad-related MAY happen until the entitlement has resolved for the current session (`entitlementResolved`), because the tier defaults to FREE. Until then there MUST be no preload, no show, and a completed round is DROPPED (not deferred). The gate resets on every session swap.

#### Scenario: Cold start for a Pro user

- GIVEN the entitlement has not resolved yet
- WHEN the feed is shown or a round completes
- THEN no ad is requested or shown, and once it resolves to Pro none ever is

### Requirement: Kill switch

A build-time flag (`ROUND_INTERSTITIAL_ENABLED`, default true; override with `-Padmob.roundInterstitialEnabled=false`, `local.properties` or the env var) MUST disable the feature entirely: no preload, no show, no analytics.

### Requirement: Intentional progress semantics

- The last-seen card seeds the next round, so a round takes N-1 swipes. This is intentional.
- Round progress is intentionally in-memory: it resets on rotation, process death and leaving the screen.
- Any change to the feed's id list or order resets progress and never completes a round. An identical list (e.g. refresh) keeps progress.
- Nothing is reported while a scroll is in progress.
- No ad-frequency state is persisted or kept in memory: there is no cooldown, so the only gate besides tier and consent is that an ad is loaded.

### Requirement: Ad expiry and Activity safety

A preloaded ad older than 50 minutes MUST be discarded and reloaded (throttled) instead of shown. The ad is shown only on the current RESUMED Activity, resolved weakly at show time; a backgrounded app shows nothing. Load and show failures are logged and reported with a bounded reason, never surfaced in the UI.

### Requirement: Operational notes

- The kill switch value is parsed case-insensitively; any value other than `true`/`false` FAILS the build with an explicit error instead of silently leaving the feature on.
- A `show()` that never reports back is abandoned after 30 s (ROUND_INTERSTITIAL_SHOW_TIMEOUT_MS) so later rounds are not queued behind it.
- The 60 s load throttle (INTERSTITIAL_LOAD_THROTTLE_MS) applies to retries after a no-fill or a show failure. A reload triggered by a dismiss MUST bypass it: a dismissal proves the unit just filled, so the next round's ad is requested immediately and cannot become a retry loop. If AdMob still has no fill, that round is silently skipped (`not_loaded`).
