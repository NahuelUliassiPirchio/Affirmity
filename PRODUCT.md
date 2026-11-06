# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Primary: adults building a short daily mental-wellness habit (a few minutes a day) who need help staying consistent. This is the product owner's own use case and the default lens for decisions.

Also served, never ignored: people using the app to cope with stress, anxiety or low mood, sometimes on hard days; and people who treat affirmations as a spiritual or self-growth practice (self-love, manifestation). Design for the habit builder without making the other two feel excluded or judged.

Default language is Spanish (neutral, no voseo); English is supported.

## Product Purpose

Affirmity helps a person keep a daily practice of reading affirmations and meditating, with a mood check-in, and makes the practice stick through streaks and gentle accountability. Success means the user comes back daily and the streak reflects a real habit, not anxiety about a number.

## Positioning

A combined daily practice (affirmations plus meditation) with one general streak that is forgiving by design: the streak advances with either activity, and "healers" earned by full days (both activities) can repair a missed day. Consistency is rewarded without punishing one bad day.

## Operating Context

- Daily ritual on a phone, often at the user's chosen moments: on waking, during the day, before something important, in difficult moments, at the end of the day, before sleep.
- Home-screen widgets: a weekly tracker (7-day grid, streak, today's status) and an affirmation carousel.
- Push notifications driven by the server (Firebase Cloud Functions + FCM), planned per user's local day and time zone, with quiet hours and a one-per-family daily cap.
- Works signed out (local Room data) or signed in (Firestore sync).

## Capabilities and Constraints

- Daily goal (hardcoded): affirmations done = 5 affirmations viewed; meditation done = any completed session. A full day means both are done.
- General streak: at least one activity per day, or a healed day. Healers: two consecutive full days earn one, capped at 2; activated the day after a missed day.
- Feature areas: affirmations feed and collections, custom affirmations, guided meditation catalog plus free timer, mood check-in, progress and history, onboarding quiz and guide, settings, paywall.
- Monetization: some themes and sessions are Pro; some sessions unlock by watching an ad.
- The onboarding quiz captures goals, a preferred tone, and usage moments.
- Open decision: milestone celebrations and badges, and an "at risk" end-of-day state, are planned but not yet defined.
- The README is outdated (it describes a local-only app with no server and a 2-affirmation goal). The code is the source of truth.

## Brand Commitments

- Name: Affirmity.
- Voice follows the user's chosen tone from the onboarding quiz ("¿Qué tono te gustaría encontrar?"):
  - Soft ("Suave"): "Puedo darme tiempo para encontrar mi camino."
  - Direct ("Directo"): "Sé lo que tengo que hacer. Empiezo."
  - Powerful ("Poderoso"): "Yo puedo. Avanzo aunque cueste."
  - Manifestation ("Manifestación"): "Me abro a nuevas oportunidades."
  
  User-facing motivational copy (nudges, celebrations, at-risk messages) should respect that preference. Today the preference is only stored on the device (`UserGoalsPreferences`) and is not yet used by the notification copy.
- Spanish copy is neutral Spanish ("Empieza", never voseo "Empezá").

## Evidence on Hand

- Real content: an affirmation catalog, a guided meditation catalog with generated session covers, and onboarding and notification copy (`app/src/main/res/values*/strings.xml`, `functions/tools/notification-copy.v1.json`).
- Pre-launch review notes: `PRE_LAUNCH_AUDIT.md`.
- No testimonials, user metrics, ratings or press exist. Do not fabricate them.

## Product Principles

1. Consistency over perfection: reward showing up, and make recovery from a missed day possible and dignified.
2. Motivate, never shame: urgency can be clear, but it never uses guilt or fear against someone who may be on a hard day.
3. Honest status: every surface tells the truth about the streak (at risk vs. goal incomplete vs. done), and the widget, the app and notifications agree on what "today" is.
4. The user's tone is respected: motivation speaks in the voice the user chose.
5. Small daily effort: the practice takes minutes, and the UI should never make it feel heavier.

## Accessibility & Inclusion

Follow WCAG contrast (a contrast helper already exists in `ui/theme/ContrastRatio.kt`). Assume some users open the app in distress; avoid alarming or punishing visual and verbal language.
