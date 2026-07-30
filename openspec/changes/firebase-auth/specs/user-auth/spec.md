# User Auth Specification

## Purpose

Introduce the first identity concept in Affirmity: an optional sign-in that
obtains a stable per-user identifier from a configured identity provider,
exposes it through `AffirmityAppState`, and never gates existing
signed-out functionality. This stage ships Google as the only concrete
provider; the contract is written provider-agnostic so Apple Sign-In can be
added later without changing these requirements.

## Requirements

### Requirement: Provider-Agnostic Auth Contract

The system SHALL expose an `AuthRepository` abstraction that describes
sign-in, sign-out, and observable auth state without naming a specific
identity provider in its public contract. The system SHALL support signing
in via a configured identity provider; Google SHALL be the only provider
wired to a concrete implementation in this stage.

#### Scenario: Auth state model is provider-neutral

- GIVEN the `AuthRepository` interface and its `authState` model
- WHEN inspecting their public API (types, method names, parameters)
- THEN no Google-specific identifiers, types, or terminology MUST appear
- AND the contract MUST be satisfiable by any future provider (e.g. Apple)
  without modification

### Requirement: Google Sign-In via Credential Manager

The system SHALL implement Google sign-in through the provider-agnostic
`authRepository.signIn(provider: AuthProviderId, activityContext)` entry
point, backed by a `GoogleIdAuthProvider` using Android Credential Manager
with Google ID, and MUST NOT use the deprecated `GoogleSignInClient` API.

#### Scenario: Successful Google sign-in

- GIVEN the user is signed out and has a Google account with Play Services
  available
- WHEN the user completes the Credential Manager Google sign-in flow from
  Settings
- THEN `authState` MUST transition to `SignedIn(uid, displayName, email)`
  with a non-null `uid`
- AND the same `uid` MUST match the user record created in Firebase
  Authentication

#### Scenario: User cancels the sign-in flow

- GIVEN the user is signed out and opens the Credential Manager sign-in
  prompt
- WHEN the user dismisses or cancels the prompt without selecting an
  account
- THEN `authState` MUST remain `SignedOut`
- AND the Settings screen MUST show no error and stay usable

#### Scenario: No Google account or Play Services unavailable

- GIVEN the device has no Google account configured, or Play Services is
  unavailable/outdated
- WHEN the user attempts Google sign-in from Settings
- THEN sign-in MUST fail without crashing the app
- AND `authState` MUST remain `SignedOut`
- AND the Settings screen MUST display an inline message indicating
  credentials are unavailable

### Requirement: Stable UID Exposure Through AffirmityAppState

The system SHALL expose the current `authState` (and the `uid` when signed
in) as observable Compose state on `AffirmityAppState`, additive to its
existing constructor and properties, and MUST NOT change any existing
screen-facing property or method signature.

#### Scenario: UID is stable across restarts

- GIVEN a user previously signed in successfully
- WHEN the app is force-closed and relaunched
- THEN `AffirmityAppState.authState` MUST report `SignedIn` with the same
  `uid` as before, without requiring the user to sign in again

#### Scenario: Existing app state contract is unchanged

- GIVEN the pre-change `AffirmityAppState` public API (properties and
  methods unrelated to auth)
- WHEN `authRepository`/`authState` are added
- THEN all pre-existing properties and methods MUST keep their original
  signatures and behavior

### Requirement: Sign-Out

The system SHALL provide `authRepository.signOut()`, reachable from the
Settings account section, that clears the local session and returns
`authState` to `SignedOut`.

#### Scenario: User signs out

- GIVEN the user is signed in
- WHEN the user selects sign-out in the Settings account section
- THEN `authState` MUST transition to `SignedOut`
- AND a subsequent app restart MUST NOT restore the previous `SignedIn`
  state without a new explicit sign-in

### Requirement: Settings-Gated Optional Entry Point

The system SHALL surface sign-in and sign-out exclusively inside the
existing `ui/settings/SettingsScreen.kt` account section. The system MUST
NOT gate any existing screen, feature, or data (affirmations, streaks,
meditation, notifications, widget) behind authentication.

#### Scenario: App is fully usable signed out

- GIVEN a user who has never signed in
- WHEN the user uses affirmations, streaks, meditation, notifications, and
  the widget
- THEN every feature MUST behave identically to the pre-change app, with no
  auth-related prompts, blocks, or redirects outside Settings

#### Scenario: Sign-in entry point exists only in Settings

- GIVEN the app's navigation surface (Home/other destinations plus
  Settings)
- WHEN searching for sign-in/sign-out UI
- THEN it MUST be present only in the Settings account section and MUST
  NOT appear on any other screen
