# Proposal: Firebase Authentication (Firebase migration stage 1 of 3)

## Intent

Affirmity has **zero user identity** today: Room/DataStore are unscoped and single-device. Stage 2 (Firestore) cannot start without a stable per-user key. This stage introduces the first identity concept: sign in with Google, obtain a Firebase Auth UID, expose it to the composition root, and let the user validate it against their own Firebase console before stage 2.

## Scope

### In Scope
- Firebase platform wiring: `google-services` plugin, Firebase BOM, `firebase-auth`, `androidx.credentials` + `googleid` in `gradle/libs.versions.toml` (catalog only, no inline coordinates). All ≥ minSdk 24 compatible (Auth 23.x needs 23, Credential Manager needs 21).
- `auth/` package (sibling of `notifications/`, `widget/`): `AuthRepository` exposing `authState` (SignedOut / SignedIn(uid, displayName, email)), `signInWithGoogle()`, `signOut()`.
- Google Sign-In via **Credential Manager + Google ID** (`GoogleSignInClient` is deprecated in 2026).
- Hand-wire `authRepository` into `AffirmityAppState` + `rememberAffirmityAppState()` as an **additive** constructor param; no existing screen-facing property or method changes.
- Minimal UI: a sign-in/sign-out account section inside existing `ui/settings/SettingsScreen.kt` (Spanish copy, matching current screens). No first-run gate — the app stays fully usable signed out.
- Unit tests (TDD per config) for auth-state mapping/reducer logic with a faked auth source.

### Out of Scope
- **Stage 2:** Firestore, per-user collections, Room/DataStore migration, day-boundary decision, image→Storage.
- **Stage 3:** FCM, Cloud Functions/Scheduler, replacing WorkManager chains, "streak about to end".
- **Apple Sign-In: deferred.** Android has no first-party Apple button; it needs `OAuthProvider` + a web flow plus an Apple Developer Services ID/redirect the user does not need until an iOS/web client exists. Revisit when iOS lands.
- Account deletion, account linking, email/password, anonymous-account upgrade, DI framework (Hilt) — hand-wiring stays; one repository does not justify it.
- Gating any existing feature behind sign-in.

## Capabilities

### New Capabilities
- `user-auth`: sign-in/sign-out lifecycle, observable auth state, stable UID exposure to app state.

### Modified Capabilities
- None (no existing specs in `openspec/specs/`).

## Approach

Additive, screen-gated slice. `AuthRepository` wraps `FirebaseAuth.addAuthStateListener` as a Flow; `AffirmityAppState` mirrors it to Compose state and nothing else consumes the UID yet. Credential UI lives only in the Settings account section, so a misconfigured console breaks one section, not app startup. Firebase init stays default (`google-services` auto-init) — no `AffirmityApplication` change.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `gradle/libs.versions.toml`, `app/build.gradle.kts`, root `build.gradle.kts` | Modified | BOM, plugin, auth/credential deps |
| `app/google-services.json` | New | Console-generated config |
| `app/src/main/java/.../auth/` | New | `AuthRepository`, auth state model |
| `data/AffirmityAppState.kt` | Modified | Additive `authRepository` param + `authState` |
| `ui/settings/SettingsScreen.kt` | Modified | Account section |
| `app/src/test/.../auth/` | New | Auth-state unit tests |

## Console-side prerequisites (user, in parallel)

1. Firebase project created; Android app registered with applicationId `com.pirxhio.affirmity`.
2. **Debug + release SHA-1 and SHA-256** fingerprints registered (Google Sign-In fails without these).
3. Google provider enabled in Authentication → Sign-in method; project support email set.
4. OAuth consent screen configured; **Web client ID** noted — the Android app must pass the *web* client ID to Credential Manager, not the Android one.
5. `google-services.json` downloaded to `app/`.
6. (Deferred) Apple: Services ID + redirect URL — not needed this stage.

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| `google-services.json` commit policy undecided (no secrets convention in repo) | High | Decide before apply; default = commit (Google-documented as non-secret, protected by SHA fingerprints + Firestore rules in stage 2). Alternative: gitignore + a checked-in template, which breaks CI/clean clones. |
| Console not ready → build or sign-in fails | High | Plugin fails the build without `google-services.json`; keep the change on a branch until the file exists. Sign-in errors surface as inline Settings text, never a crash. |
| Web-vs-Android client ID confusion | Med | Read the web client ID from `google-services.json`/a resource, document in design. |
| Credential Manager UX on devices without Play Services / no Google account | Med | Explicit "no credentials available" message; feature is optional, nothing else degrades. |
| Deps bloat APK / minSdk regression | Low | Catalog-pinned, BOM-managed, all ≥ minSdk 24 verified in `assembleDebug`. |
| Scope creep into Firestore | Med | UID is stored nowhere and read by nothing this stage. |

## Rollback Plan

1. Revert the feature branch — Room/DataStore/WorkManager are untouched, so no data migration to undo and no user-visible regression.
2. Partial rollback: remove the Settings account section only; `AuthRepository` is inert with no callers.
3. Console rollback: disable the Google provider in Firebase Auth; the app still runs signed out.
4. If `google-services.json`/plugin blocks the build, dropping the plugin line + the file restores a buildable app.

## Dependencies

- User-completed console prerequisites above (blocking for manual verification, not for coding).
- Firebase BOM, `firebase-auth`, `androidx.credentials`, `androidx.credentials:credentials-play-services-auth`, `googleid`.
- Stage 2 (`firebase-firestore` change) depends on this stage's UID contract.

## Success Criteria

- [ ] `gradlew.bat assembleDebug` and `gradlew.bat testDebugUnitTest` pass.
- [ ] Signing in from Settings shows the Google account and a non-null UID; the same UID appears in Firebase console → Authentication → Users.
- [ ] UID is stable across app restarts; sign-out returns to the signed-out state.
- [ ] Signed-out app behavior is byte-for-byte unchanged (affirmations, streaks, meditation, notifications, widget).
- [ ] No Firestore, FCM, or WorkManager code touched.
- [ ] `google-services.json` handling decision recorded in the design.
