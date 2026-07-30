# Tasks: Firebase Authentication (Stage 1 of 3)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~550–650 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR1 → PR2 → PR3 |
| Delivery strategy | auto-chain |
| Chain strategy | feature-branch-chain |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|-----------------|-------------------|
| 1 | Gradle/version-catalog + `google-services` wiring | PR1 (base: `feature/firebase-auth`) | `gradlew.bat assembleDebug` (needs `google-services.json`) | N/A — config only | Revert catalog/plugin lines, delete json |
| 2 | `auth/` package + unit tests | PR2 (base: PR1 branch) | `gradlew.bat testDebugUnitTest --tests "com.pirxhio.affirmity.auth.*"` | N/A — repo has no callers yet | Delete `auth/` + its test dir |
| 3 | AppState wiring + Settings account card | PR3 (base: PR2 branch) | `gradlew.bat testDebugUnitTest` | Manual: sign in from Settings, verify uid in Firebase console | Remove card, revert `AffirmityAppState`/`SettingsScreen`/`MainActivity` additive params |

Only the tracker branch merges to `main`.

## Phase 1: Build & Dependency Foundation (PR1)

- [x] 1.1 Add Firebase BOM, `firebase-auth`, `androidx.credentials`, `androidx.credentials:credentials-play-services-auth`, `googleid`, `google-services` plugin to `gradle/libs.versions.toml`.
- [x] 1.2 Apply `google-services` plugin in root `build.gradle.kts` and `app/build.gradle.kts`; add deps via `libs.*`.
- [x] 1.3 Place user-provided `app/google-services.json` (console prerequisite, in parallel) — RESOLVED: corrected JSON now registers `package_name: "com.pirxhio.affirmity"` (matches `applicationId`) with an `oauth_client` entry of `client_type: 3` (web client, project `affirmity-7ace6`). `processDebugGoogleServices` and `testDebugUnitTest` both pass.
- [x] 1.4 Verify `gradlew.bat assembleDebug` once the JSON exists before merging PR1 into the tracker branch. — `gradlew.bat assembleDebug` BUILD SUCCESSFUL.

## Phase 2: Auth Domain Contracts (PR2)

- [x] 2.1 Create `auth/AuthState.kt`: `AuthProviderId`, `AuthState` (`SignedOut`/`SignedIn`), `ProviderCredential`, `AuthError`.
- [x] 2.2 Create `auth/AuthProvider.kt`, `auth/AuthRepository.kt` interfaces: `signIn(provider: AuthProviderId, activityContext)`, `signOut()`, `authState: StateFlow<AuthState>`.

## Phase 3: Firebase Repository — TDD (PR2)

- [x] 3.1 RED `app/src/test/.../auth/FirebaseAuthRepositoryTest.kt`: fake user source, non-null user → `SignedIn(uid,name,email)`, null → `SignedOut`.
- [x] 3.2 GREEN `auth/FirebaseAuthRepository.kt`: mirror `FirebaseAuth.addAuthStateListener` into `StateFlow<AuthState>`.
- [x] 3.3 RED: `signIn` on an unregistered `AuthProviderId` fails without touching Firebase.
- [x] 3.4 GREEN: provider lookup + `ProviderCredential.IdToken` → `GoogleAuthProvider.getCredential` → `signInWithCredential`.
- [x] 3.5 REFACTOR: extract credential-to-Firebase mapping into a small pure function.

## Phase 4: Google Credential Provider — TDD (PR2)

- [x] 4.1 RED: error classification — cancel → `SignedOut`/no error; `NoCredentialException` → `NoCredentialAvailable`; Play Services issue → `ProviderUnavailable`; missing `default_web_client_id` → `ConfigurationMissing`.
- [x] 4.2 GREEN `auth/GoogleIdAuthProvider.kt`: `CredentialManager` + `GetGoogleIdOption`, `webClientId()` name-based resource lookup.
- [x] 4.3 Hand-wire `GoogleIdAuthProvider` + `FirebaseAuthRepository` in `rememberAffirmityAppState()`.

## Phase 5: AffirmityAppState Wiring (PR3)

- [x] 5.1 Additive `authRepository: AuthRepository` param on `data/AffirmityAppState.kt`; expose `authState`/`authError` Compose state and `signIn`/`signOut`.
- [x] 5.2 Confirm no pre-existing `AffirmityAppState` signature changed. — new constructor param `authRepository` added at the end of the existing parameter list; all prior params, properties, and methods are byte-for-byte unchanged. Full `testDebugUnitTest` suite (existing + new auth tests) still passes.

## Phase 6: Settings UI (PR3)

- [x] 6.1 Create `ui/settings/AccountSettingsCard.kt`: stateless, style-matched to `ChannelSettingsCard` — signed-out row, signed-in row + sign-out, inline error text.
- [x] 6.2 Add `item { AccountSettingsCard(...) }` + params to `ui/settings/SettingsScreen.kt`.
- [x] 6.3 Pass auth state/callbacks from `MainActivity.kt`, `activityContext` from call-site `LocalContext.current`.
- [x] 6.4 Add Spanish account/error strings to `res/values/strings.xml`.

## Phase 7: Verification

- [x] 7.1 `gradlew.bat testDebugUnitTest` — Phase 3/4 suites pass. — full suite green (14 auth tests: 6 `FirebaseAuthRepositoryTest` + 8 `GoogleIdAuthProviderTest`; see TDD Cycle Evidence; no pre-existing test broke).
- [x] 7.2 `gradlew.bat assembleDebug` — requires `app/google-services.json`. — BUILD SUCCESSFUL.
- [ ] 7.3 Manual: sign in from Settings, uid matches Firebase console Users; sign-out → `SignedOut`; restart keeps `SignedIn`. — requires a device/emulator + the user's own Firebase console; not executable by the apply agent.
- [ ] 7.4 Optional `connectedDebugAndroidTest`: signed-out Settings shows sign-in row; other screens unaffected. — optional, requires a connected device/emulator; not executable by the apply agent.

## Phase 8: Cleanup

- [x] 8.1 Resolve design Open Questions: spec's `signInWithGoogle()` wording superseded by `signIn(AuthProviderId.GOOGLE, ...)`; confirm console produced a web (type-3) OAuth client. — Confirmed consistent: `specs/user-auth/spec.md` (Requirement "Google Sign-In via Credential Manager") already uses `authRepository.signIn(provider: AuthProviderId, activityContext)` wording (fixed in a prior session); `design.md` Architecture Decisions table documents the same signature as the adopted choice, not a contradiction. `app/google-services.json`'s `oauth_client` entry has `client_type: 3` (web client), confirmed by direct inspection — `default_web_client_id` resolves correctly, so `GoogleIdAuthProvider.webClientId()` will not fall back to `ConfigurationMissing` at runtime.
- [x] 8.2 (found during manual verification) `GoogleIdAuthProvider.requestCredential` never called `GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(...)`, so it used the library default (`true`) — Credential Manager only offers Google accounts already authorized for this app, so every first-time sign-in failed with `NoCredentialAvailable` even on a device with Google accounts configured. Fixed: `requestCredential` now attempts `filterByAuthorizedAccounts = true` first (silent path for returning users), and on `NoCredentialAvailable` retries once with `filterByAuthorizedAccounts = false` (full account picker, first-time sign-up path). `testDebugUnitTest` still 14/14 green (pure functions `classifyCredentialError`/`webClientId` unchanged); the retry orchestration itself is not independently unit-tested, consistent with the existing design decision to keep Android/Firebase-dependent interaction code outside the unit-test seam.
