# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Spanish-first audience (English secondary) building a daily self-care habit of affirmations and meditation. They open the app briefly, often once a day, and return through notifications and home-screen widgets.

## Product Purpose

Affirmity helps people sustain a gentle daily practice: reading affirmations, meditating, checking in on mood, and seeing their progress. Success is a user who keeps coming back, and who feels supported rather than judged when they slip.

## Positioning

A streak system built around forgiveness: any single activity counts for the day, and earned streak healers let a missed day be mended instead of erasing progress.

## Operating Context

- Daily rhythm: notifications (streak, healer families) and Glance home-screen widgets (weekly tracker, carousel, badge) bring users back.
- Signed-out users persist locally (Room); signed-in users sync through Firestore.
- Freemium with a paywall for premium content.

## Capabilities and Constraints

- Streak: a day is done when either the affirmation or the meditation is completed.
- Streak healer: completing both habits on 2 consecutive days earns one healer, capped at 2 held. A healer can mend only yesterday (`today-1`), and only while today is still running. Inventory is derived from history, not stored.
- Healer application lives in `AffirmityAppState.activateStreakHealer()`; state comes from `StreakHealerStats.evaluate()` (`HealerActivation.Available / UsedToday / Unavailable`).
- Locales: `values/` is Spanish (default), `values-en/` is English.

## Brand Commitments

- Voice: warm, supportive, never guilt-tripping.
- Spanish copy uses neutral tú ("Empieza"), never voseo ("Empezá").
- Existing assets: `ic_streak_flame`, `ic_streak_flame_outline`, `ic_heart_mended` (heart with bandage, multicolor).

## Product Principles

1. Forgiveness over punishment: a slip is recoverable, and the UI should say so plainly.
2. One small action a day is enough.
3. Respect the user's time: short, glanceable moments.
4. Never manufacture urgency or shame to drive engagement.
