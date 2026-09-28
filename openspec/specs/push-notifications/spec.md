# Push Notifications Specification

## Purpose

Server-computed notification scheduling and FCM delivery for signed-in users.
The server (nightly planner + Cloud Tasks) decides when to notify; the client
only receives an FCM message and posts it via `Notifier`. Covers reminder and
reflection-prompt scheduling (reproducing today's 3-random-slot-per-window
math), the new "streak about to end" channel, and the signed-out exclusion.

## Requirements

### Requirement: Nightly Planner Computes Per-User Trigger Instants

A Cloud Scheduler job SHALL invoke a planner Cloud Function once per day. For
each active (signed-in) user, the planner MUST read that user's Firestore
notification settings and persisted IANA timezone, and MUST compute trigger
instants for the user's local day using the same random 3-slot-per-window
algorithm as the retired client-side `NotificationSchedule.subWindow`/
`nextTriggerAtMillis`. The planner MUST enqueue one Cloud Task per computed
slot. A failure planning one user MUST NOT abort planning for other users.

#### Scenario: Reminder window yields 3 random slots

- GIVEN a signed-in user with reminders enabled and a configured window
- WHEN the nightly planner runs for that user's local day
- THEN it MUST enqueue exactly 3 Cloud Tasks, each falling inside the
  configured window, at instants matching the ported slot-randomization
  algorithm

#### Scenario: One user's planning failure does not block others

- GIVEN the planner is processing multiple active users in one nightly pass
- WHEN planning fails for one user (e.g. malformed settings)
- THEN the planner MUST continue planning and enqueuing tasks for all
  remaining users
- AND the failure MUST be isolated to that one user's plan for that day

#### Scenario: Editing settings changes the next day's schedule

- GIVEN a signed-in user edits their reminder window or enables/disables a
  channel in the app
- WHEN the nightly planner next runs
- THEN the enqueued Cloud Tasks MUST reflect the updated settings, not the
  previous day's settings

### Requirement: Cloud Task Fire Sends One FCM Message

When a Cloud Task fires, the system SHALL send exactly one FCM message to
the user's registered device token(s) for that notification's channel.

#### Scenario: Fired task sends FCM message

- GIVEN a Cloud Task enqueued by the planner for a given slot
- WHEN the task's scheduled instant is reached
- THEN the system MUST send an FCM message identifying the notification
  channel to the user's current FCM token(s)

#### Scenario: Stale token is removed on send failure

- GIVEN an FCM send returns `UNREGISTERED` or `INVALID_ARGUMENT` for a token
- WHEN the send completes
- THEN the system MUST delete that token from the user's token store
- AND MUST NOT retry sending to that same token

### Requirement: Client Receives and Posts via Notifier

The Android client SHALL register a `FirebaseMessagingService` subclass. On
`onMessageReceived`, the client MUST forward the message directly to the
existing `Notifier.notify()` without performing its own trigger-time
computation.

#### Scenario: Received message is posted

- GIVEN the `FirebaseMessagingService` receives an FCM message for a known
  notification channel
- WHEN `onMessageReceived` is invoked
- THEN the client MUST call `Notifier.notify()` with that channel's content
- AND MUST NOT compute or reschedule any trigger time on-device

#### Scenario: Token refresh is synced

- GIVEN the FCM SDK invokes `onNewToken` with a new token
- WHEN the callback runs
- THEN the client MUST write the new token to `users/{uid}`'s token store
- AND a stale token previously stored for this device MAY be superseded

### Requirement: Streak-About-to-End Channel

The system SHALL evaluate, once per active user during the nightly planner
pass, whether to enqueue a "streak about to end" notification for 20:00
user-local time. The notification is about the GENERAL streak: consecutive
days with at least one activity (affirmations OR meditation). The trigger
condition MUST be: the general streak through yesterday is >= 1, AND no
activity (neither affirmation nor meditation) is marked for the user's
current local day (per their persisted timezone). The condition MUST be
re-evaluated at send time.

#### Scenario: Fires when the general streak is live and today has no activity

- GIVEN a user whose general streak through yesterday is 3 and who has
  marked neither meditation nor affirmations today at 20:00 local time
- WHEN the planner evaluates the streak channel for that user
- THEN it MUST enqueue a "streak about to end" notification for 20:00
  user-local time

#### Scenario: General streak is held by mixed activities

- GIVEN a user who meditated two days ago and did affirmations yesterday
- WHEN the general streak is computed
- THEN it MUST count both days as one streak of 2

#### Scenario: Does not fire once any activity is done today

- GIVEN a user with a general streak of 3 who has completed at least one of
  affirmation or meditation for their current local day before 20:00
- WHEN the planner evaluates the streak channel for that user
- THEN it MUST NOT enqueue a "streak about to end" notification for that day

#### Scenario: Does not fire with no active streak

- GIVEN a user whose general streak through yesterday is 0
- WHEN the planner evaluates the streak channel for that user
- THEN it MUST NOT enqueue a "streak about to end" notification

### Requirement: Activity-Specific Streak Notification

When the streak channel fires, the system SHALL send the general-streak copy
by default. It SHALL instead send an activity-specific notification only when
exactly one activity (meditation or affirmations) has its own live streak of
at least 3 consecutive days through yesterday. The activity-specific copy MUST
name the activity and use that activity's streak count (for example "You've
been meditating for 5 days, let's not stop now"). The FCM data payload MUST
carry an `activity` field (`meditation` | `affirmations`) only for
activity-specific alerts, and copy MUST come from catalog variants tagged
`streak_activity` plus `activity_meditation` or `activity_affirmations` (es and
en). The client MUST treat `activity` as optional so older payloads still work.

#### Scenario: Single activity with a live streak of 3+ days

- GIVEN a user with a meditation streak of 5 days through yesterday and no
  affirmations streak of 3+ days
- WHEN the streak notification is sent
- THEN the payload MUST include `activity=meditation` and `streakCount=5`
- AND the copy MUST be a meditation-tagged variant

#### Scenario: Both activities have live streaks of 3+ days

- GIVEN a user whose meditation and affirmations streaks are both >= 3
- WHEN the streak notification is sent
- THEN it MUST use the general copy and MUST NOT include `activity`

#### Scenario: Activity streak below the threshold

- GIVEN a user whose only activity streak is 2 days
- WHEN the streak notification is sent
- THEN it MUST use the general copy and MUST NOT include `activity`

#### Rollout and rollback

- Seed the copy catalog (`functions/tools/seedCopyCatalog.ts`) BEFORE deploying the
  function. If the `streak_activity_*` variants are missing or disabled at send time, the
  function falls back to the general band copy, omits `activity` from the payload and logs a
  `notification_copy_variant_missing` warning when no variant matches at all.
- Rollback: set `enabled=false` on the four `streak_activity_meditation_a/b` and
  `streak_activity_affirmations_a/b` catalog documents; sends then use the general copy with no
  redeploy.
- Streak counts in streak notifications are now the GENERAL streak (any activity per day) and may
  differ from the per-activity streaks shown in the app. Activity-specific alerts use that
  activity's own count. A stale planned `activity` value is never trusted: payload, context tags
  and copy are all re-derived at send time.
- `notification_opened` carries an optional `activity` analytics param (`meditation` |
  `affirmations`) for activity-specific alerts only.

### Requirement: Per-Type Notification Presentation

The Android client SHALL render STREAK, REFLECTION and MOOD notifications with
distinct custom presentations (custom `RemoteViews` layouts inside
`DecoratedCustomViewStyle`, with collapsed and expanded views and light/dark
colors); every other channel MUST keep the plain BigText presentation. The
presentation choice MUST be produced by a framework-free spec mapper so it is
JVM-unit-testable. Tapping any of these notifications MUST keep the existing
start-destination and mood-picker deep-link behavior.

#### Scenario: Streak notification

- GIVEN a streak notification with `streakCount=7`
- WHEN it is posted
- THEN it MUST show a flame and the count prominently
- AND, when `activity` is present, the expanded view MUST show the activity name

#### Scenario: Reflection notification

- GIVEN a reflection notification whose body is a question
- WHEN it is posted
- THEN the question MUST be rendered large in a calm, quote-like layout

#### Scenario: Mood notification

- GIVEN a mood notification
- WHEN it is posted expanded
- THEN it MUST show a friendly text-only prompt (title and body) on a tinted card with a heart icon
- AND tapping it MUST open the mood picker on the mood destination

### Requirement: Signed-Out Users Receive Zero Notifications

The system SHALL exclude signed-out users entirely from the planner. No
local (on-device) scheduling MUST remain as a fallback for signed-out users.

#### Scenario: Signed-out user gets no notifications

- GIVEN a user who has never signed in, or has signed out
- WHEN the nightly planner runs
- THEN it MUST NOT plan, enqueue, or send any notification for that device
- AND no on-device WorkManager scheduling MUST be active for that device

### Requirement: Signed-In Scheduling Reads Firestore Settings

`AffirmityAppState` and any code path that determines a signed-in user's
notification schedule MUST read `FirestoreNotificationSettingsRepository`,
not the local DataStore `NotificationPreferences` copy. This corrects the
pre-change bug where signed-in scheduling silently used stale local settings.

#### Scenario: Signed-in scheduling uses Firestore settings

- GIVEN a signed-in user whose local DataStore settings differ from their
  Firestore settings document
- WHEN the app determines what to schedule/display for that user
- THEN it MUST use the value from `FirestoreNotificationSettingsRepository`
- AND MUST NOT read the local DataStore copy for a signed-in user

#### Scenario: Regression guard — editing in-app settings takes effect

- GIVEN a signed-in user changes their reminder window in the app
- WHEN the change is persisted
- THEN it MUST be persisted to the Firestore settings document
- AND the next planner run MUST observe the updated value (see also
  data-sync's settings requirement)
