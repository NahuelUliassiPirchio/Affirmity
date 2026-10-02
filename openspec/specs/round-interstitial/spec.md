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

On round completion a free-tier user MUST be shown one interstitial, provided the last one was shown at least 5 minutes ago (ROUND_INTERSTITIAL_COOLDOWN_MS). Pro users MUST never see it. The cooldown MUST start only when an interstitial was actually shown.

#### Scenario: Free user

- GIVEN a free user, no interstitial in the last 5 minutes, and a loaded ad
- WHEN a round completes
- THEN one interstitial is shown and the timestamp is stored

#### Scenario: Pro user

- GIVEN a Pro user
- WHEN a round completes
- THEN no interstitial is requested or shown

#### Scenario: Cooldown

- GIVEN an interstitial was shown 4 minutes 59 seconds ago
- WHEN another round completes
- THEN none is shown; at 5 minutes 0 seconds one is shown

### Requirement: Silent degradation

The interstitial MUST NOT block, delay or visibly alter the feed. Consent MUST be checked passively (`canRequestAds()`); a consent form MUST NOT be shown. If consent is missing, the ad is not loaded, the app is backgrounded, or loading or showing fails, nothing visible happens, no cooldown starts, and loads are throttled (no tight retry loop).

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
- The cooldown is also kept in memory, so a failing persistence write cannot cause back-to-back ads; if the persisted value cannot be read, the ad is skipped.

### Requirement: Ad expiry and Activity safety

A preloaded ad older than 50 minutes MUST be discarded and reloaded (throttled) instead of shown. The ad is shown only on the current RESUMED Activity, resolved weakly at show time; a backgrounded app shows nothing. Load and show failures are logged and reported with a bounded reason, never surfaced in the UI.

### Requirement: Operational notes

- The kill switch value is parsed case-insensitively; any value other than `true`/`false` FAILS the build with an explicit error instead of silently leaving the feature on.
- A `show()` that never reports back is abandoned after 30 s (ROUND_INTERSTITIAL_SHOW_TIMEOUT_MS) so later rounds are not queued behind it. If the caller is cancelled after the ad was shown, the cooldown still starts (the shown hook writes the process-level in-memory last-shown time, which also survives Activity recreation).
- After a dismiss, the 60 s load throttle usually means the next ad is not ready for a round that completes very soon after; that round is silently skipped (`not_loaded`). The throttle is intentionally unchanged.
