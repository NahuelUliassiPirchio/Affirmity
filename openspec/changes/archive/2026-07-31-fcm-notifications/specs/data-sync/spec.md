# Delta for Data Sync

## MODIFIED Requirements

### Requirement: Per-User Collection Schema

The system SHALL persist signed-in user data under `users/{uid}` with
`affirmations/{id}` (mirrors `AffirmationEntity` 1:1), `dailyCompletions/{epochDay}`
(mirrors `DailyCompletionEntity` 1:1, doc id = stringified `epochDay`),
`settings/preferences` (`meditationDurationSeconds`, both notification
channels, and a persisted IANA timezone id field), `meta/migrated` (migration
marker), and `fcmTokens/{tokenId}` (registered FCM device tokens for the
user). The system MUST NOT store a cached streak field anywhere in this
schema.
(Previously: schema did not include a timezone field or an FCM token store;
only `affirmations`, `dailyCompletions`, `settings/preferences`, `meta/migrated`.)

#### Scenario: Streak stays re-derived, never cached

- GIVEN a signed-in user's `dailyCompletions` documents in Firestore
- WHEN `DailyCompletionStats` computes the current streak
- THEN it MUST walk raw per-day rows exactly as it does for Room today
- AND no document in the schema MUST contain a precomputed streak value

#### Scenario: Timezone field is part of settings

- GIVEN a signed-in user's `settings/preferences` document
- WHEN the document is read
- THEN it MUST include an IANA timezone id field alongside existing
  preference fields

#### Scenario: FCM tokens are stored per-user, not per-field

- GIVEN a signed-in user registers a device for push notifications
- WHEN the token is persisted
- THEN it MUST be written under `users/{uid}/fcmTokens/{tokenId}`, not
  embedded as a single field on another document
- AND multiple devices for the same user MAY each have their own token
  document

## ADDED Requirements

### Requirement: Timezone Auto-Detection and Sync

The client SHALL detect the device's IANA timezone id (`TimeZone.getDefault().id`)
at sign-in and at first launch, and SHALL write it to
`users/{uid}/settings/preferences` if it differs from the currently persisted
value. No manual timezone picker UI is provided.

#### Scenario: Timezone is captured at sign-in

- GIVEN a user signs in for the first time on a device
- WHEN sign-in completes
- THEN the device's IANA timezone id MUST be written to the user's
  `settings/preferences` document

#### Scenario: Timezone re-syncs after travel

- GIVEN a signed-in user's persisted timezone differs from the device's
  current `TimeZone.getDefault().id` (e.g. after travel)
- WHEN the app launches
- THEN the client MUST re-sync the persisted timezone id to the device's
  current value

### Requirement: FCM Token Store Under Per-User Schema

The system SHALL store each registered FCM device token under
`users/{uid}/fcmTokens/{tokenId}`, written on successful token
registration and deleted when the token becomes stale (send failure with
`UNREGISTERED`/`INVALID_ARGUMENT`) or the associated `onNewToken` callback
supersedes it.

#### Scenario: Token is written on sign-in

- GIVEN a user signs in on a device with notification permission granted
- WHEN the FCM SDK provides a token
- THEN the system MUST write that token under
  `users/{uid}/fcmTokens/{tokenId}`

#### Scenario: Stale token is deleted, not just ignored

- GIVEN an FCM send to a stored token fails with `UNREGISTERED` or
  `INVALID_ARGUMENT`
- WHEN the failure is handled
- THEN the corresponding `fcmTokens/{tokenId}` document MUST be deleted
