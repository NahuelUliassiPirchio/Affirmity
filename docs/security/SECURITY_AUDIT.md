# Affirmity Security Audit

Date: 2026-08-29
Scope: Full repository at the time of audit (Android client `com.pirxhio.affirmity`, Firebase project `affirmity-7ace6`, Cloud Functions in `functions/`, security rules at `firestore.rules`). Static, source-only review. No dynamic testing, no APK build/decompile, no emulator execution, no production Firebase access was performed.
Assumption enforced throughout: **the Android client is completely attacker-controlled.** Any authorization enforced only by Kotlin/UI/local state is treated as bypassable; only server-side enforcement (Firestore Security Rules, Cloud Functions with independent verification) counts as a real control.

---

## 1. Executive summary

Affirmity is a personal-wellness Android app (affirmations, mood tracking, meditation, streaks) with a Google-Sign-In-only Firebase backend. The security posture is **notably better than average for an app of this size and maturity** — the code shows evidence of deliberate, documented security design (see the extensive rationale comments in `firestore.rules` and Cloud Functions), and the most sensitive resource in the system (subscription entitlement / "Pro" status) is genuinely server-authoritative: it is verified against the Google Play Developer API and is unconditionally write-denied to every client, including the owning user.

No Critical findings were confirmed. No leaked secrets were found anywhere in the current tree or in the full git history (all branches/refs). No exported Android attack surface exists beyond the mandatory launcher Activity, which accepts no external data. No WebView, no custom TLS trust bypass, no cleartext traffic override, no SQL injection surface (Room, parameterized), no hardcoded production secrets.

The confirmed issues are all Medium/Low: release builds ship with R8/ProGuard **fully disabled** (trivial reverse engineering of the shipped APK), `allowBackup=true` with unedited stock backup rules (exports the full local SQLite DB, including free-text mood notes, via ADB backup / cloud backup), a Firebase UID logged at `Log.d` in five call sites (not stripped from release logs because minification is off), and a real but well-scoped, explicitly-documented weakening in `firestore.rules` where ad-reward "unlock" grants are client-observed and therefore self-grantable by a modified client — a deliberate, low-value-content trade-off, not an oversight. The rules test suite is real and thoughtfully designed but only 3 of 22 written test cases were confirmed to actually execute in the environment this audit could inspect (JDK/CLI availability blocked the rest); their correctness rests on manual rules review, not on verified CI evidence, and that should be closed before relying on them as a regression gate.

**Bottom line:** the authorization model for real assets (entitlements, personal data ownership) is sound and server-enforced. The main gaps are APK-hardening hygiene (R8, backup rules, log hygiene) and closing a test-execution gap, not authorization architecture flaws.

---

## 2. Architecture / security model

```
Android client (untrusted)
  └─ Jetpack Compose UI → AffirmityAppState (composition root)
       ├─ auth/  FirebaseAuthRepository + GoogleIdAuthProvider (Credential Manager)
       │     └─ FirebaseAuth (Google Identity Platform) — issues ID token, session, uid
       ├─ data/local/  Room (SQLite, unencrypted) + DataStore (Preferences) — offline/local cache
       ├─ data/remote/  Firestore*Repository classes — one per collection family, uid-scoped
       │     └─ Cloud Firestore SDK → firestore.rules (server-enforced authorization)
       ├─ billing/  BillingService (Play Billing Library) — purchase UI + acknowledgement
       │     └─ Cloud Functions (syncEntitlement) → Play Developer API (source of truth)
       ├─ ads/  AdMob (rewarded ads) → client-observed completion → adUnlocks/timedUnlocks writes
       └─ notifications/  FCM token registration + local scheduling (WorkManager)

Firebase project (affirmity-7ace6)
  ├─ Firestore: /users/{uid}/... (owner-scoped), /catalog*/... (world-readable, admin-write-only)
  ├─ Cloud Functions (functions/src/*.ts): playRtdn (Pub/Sub RTDN), syncEntitlement (HTTPS callable),
  │    sendNotification (Cloud Tasks target), planNotifications (scheduled)
  └─ firestore.rules: the single enforcement boundary between an attacker-controlled client and data
```

Trust anchor for the one asset that matters commercially (subscription entitlement) is the **Play Developer API**, reached only from Cloud Functions using the Admin SDK, which bypasses Firestore rules by design — the rules then close the loop by making the `entitlements` collection unconditionally `write: false` for every client.

---

## 3. Attack surface

| Surface | Exposure | Notes |
|---|---|---|
| Android exported components | `MainActivity` only (mandatory, LAUNCHER) | No deep links, no App Links, no custom scheme, no exported service/receiver/provider |
| Intents / IPC | None accepting external data | No `getParcelableExtra`/`getSerializableExtra`; `PendingIntent`s all `FLAG_IMMUTABLE` |
| WebView | Absent | Zero WebView usage in the codebase |
| Network | Firebase SDKs, Coil (image loading), AdMob, Play Billing | No custom TLS trust manager; relies on Android platform default (cleartext blocked by default at targetSdk 36) |
| Local storage | Room SQLite (unencrypted) + DataStore Preferences | No credentials/tokens stored; mood notes (free text) are the most sensitive local content |
| Firestore | Every `/users/{uid}/...` collection + world-readable `/catalog*/...` | Full data path traced in §7 |
| Cloud Functions | `playRtdn` (Pub/Sub push), `syncEntitlement` (HTTPS, requires Firebase ID token), `sendNotification` (Cloud Tasks target, OIDC-gated), `planNotifications` (scheduled, no external trigger) | All four verified to gate on something other than client-supplied trust |
| APK reverse engineering | Release build unobfuscated (R8 off) | Increases ease of static analysis of client logic, not a data-authorization bypass by itself since server-side rules remain the real control |
| Backup channel | `allowBackup=true`, unedited rules | ADB backup / Auto Backup can export the full local DB |

---

## 4. Trust boundaries

1. **Android client ⇄ Firebase Auth** — client presents a Google ID token; Firebase issues a session and a `uid`. Trust boundary crossed correctly: `uid` is only ever read from `FirebaseAuth.currentUser`/the auth-state listener, never cached/overridable locally (confirmed by tracing every repository construction site in `AffirmityAppState.kt`).
2. **Android client ⇄ Firestore** — the real authorization boundary for all user data. Enforced by `firestore.rules`, evaluated server-side on every request; the client's Kotlin code has no bearing on what is actually allowed. Reviewed exhaustively in §8.
3. **Android client ⇄ Cloud Functions** — `syncEntitlement` requires a verified Firebase ID token; `sendNotification` requires OIDC identity from a specific invoker service account (not reachable directly by the Android client at all — it's a Cloud Tasks target); `playRtdn` requires a Pub/Sub push OIDC claim from Google's RTDN infrastructure.
4. **Cloud Functions ⇄ Play Developer API** — the actual source of truth for entitlement status. Functions re-verify purchase tokens against Google's servers rather than trusting client- or even Firestore-resident claims.
5. **Ad SDK (client-observed) ⇄ Firestore `adUnlocks`/`timedUnlocks`** — a *deliberately* weaker boundary: there is no server signal for "did the user watch the rewarded ad," so the client is trusted for this one, explicitly low-value-content decision. This is the one place client observation directly becomes a Firestore write with real (if bounded) effect.

---

## 5. Threat model

**Assets:** subscription entitlement status (revenue-relevant), user's personal data (affirmations, mood notes, streak history — privacy-relevant, not regulated-health-data grade but still personal), Firebase Auth session/identity, ad-unlock grants (low value), curated catalog content (low value, already shipped in the APK).

**Actors:** anonymous/unauthenticated attacker; authenticated attacker (their own real Google account); authenticated attacker with a modified/rooted client (full control over what the "app" sends, including forged Firestore writes and forged Cloud Function calls up to what a valid ID token permits); malicious app on the same device (backup/clipboard/logcat-adjacent risks); attacker with physical/ADB access to an unlocked device.

**Entry points:** Firestore SDK calls (fully attacker-shaped once client is modified), the two client-reachable Cloud Functions, Google Sign-In flow, ADB backup, logcat (if debuggable/log-readable), rewarded-ad completion callback.

**Primary abuse cases considered:** self-granting Pro entitlement (blocked, §8), reading/writing another user's data (blocked, §8), forging catalog content (blocked, §8), replaying/backdating ad-unlock grants (largely blocked by rules shape validation; see F-04), impersonating the notification-invoker service account (blocked by OIDC), spoofing RTDN payloads (blocked by push-claim verification), exfiltrating local data via backup (not blocked — F-02), reverse engineering the release APK to understand/automate abuse of the (already-server-verified) entitlement flow (not blocked — F-01, but the payoff is limited since the actual write is still rules-enforced).

---

## 6. Authentication model

Single provider: **Google Sign-In via Android Credential Manager** (`androidx.credentials` + `googleid`, not the deprecated `GoogleSignInClient`) → `FirebaseAuth.signInWithCredential`. No anonymous auth. No password-based auth. No account-deletion flow was found in the reviewed files (sign-out clears the local session via `FirebaseAuth.signOut()` but does not appear to trigger any server-side account/data purge) — flagged as an UNKNOWN/gap for GDPR-style deletion-request handling, not a security vulnerability per se (see §15).

`GoogleIdAuthProvider.kt` correctly validates the returned credential's type (`GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL`) before parsing it, resolves the OAuth web client ID by resource name with a safe null fallback (no hardcoded client ID), and never logs or persists the raw ID token — it is passed directly into `GoogleAuthProvider.getCredential(token, null)` for the Firebase exchange, which is the correct pattern (server-side verification happens inside Firebase Auth, not client code).

Sign-in is additive/optional — the app is fully usable signed-out (local-only data), and Settings is the only sign-in entry point, never a first-run gate (per `openspec/changes/firebase-auth/design.md`, confirmed consistent with the implementation).

---

## 7. Authorization model — full data-path trace

Every `Firestore*Repository` under `app/src/main/java/com/pirxhio/affirmity/data/remote/` is constructed with a `uid` sourced exclusively from `(authState.value as? AuthState.SignedIn)?.uid` at the composition root (`AffirmityAppState.kt`). There is no code path where a repository is built from a locally cached, user-editable, or otherwise attacker-influenceable uid string — the only way to target a different uid's data is to actually be authenticated as that uid, which requires a valid Firebase session for that account.

| Collection | Client operations | Fields client-controlled | Server-side enforcement |
|---|---|---|---|
| `users/{uid}/affirmations/{id}` | read/write | title, subtitle, content | owner-only, no shape validation (non-privileged) |
| `users/{uid}/dailyCompletions/{epochDay}` | read/write | completion flags | owner-only |
| `users/{uid}/dailyMoods/{epochDay}` | read/write | mood value, free-text note | owner-only |
| `users/{uid}/streakHealerUses/{epochDay}` | read/write | usage flag | owner-only |
| `users/{uid}/settings/{doc}`, `users/{uid}/meta/{doc}` | read/write | preferences, onboarding/migration flags | owner-only |
| `users/{uid}/fcmTokens/{token}` | create/update/delete/read | token (=doc id), platform | owner-only + shape check (`token==docId`, `platform=='android'`) |
| `users/{uid}/notificationPlans/{day}` | read only | — | **write: if false** (server/Admin-SDK-written only); no client write path exists in the codebase either |
| `users/{uid}/entitlements/{doc}` | read only | — | **write: if false unconditionally, including for the owner.** No write method exists anywhere in `FirestoreEntitlementRepository` — confirmed absent, not merely unused |
| `users/{uid}/adUnlocks/{contentType_contentId}` | create + read (update/delete denied) | contentType, contentId, grantedAtMillis, expiresAtMillis | owner-only, closed field set, doc-id must equal `contentType_contentId`, create-only (cannot be erased/back-dated) — deliberately client-trusted for grant *timing/existence*, see F-04 |
| `users/{uid}/timedUnlocks/{contentType_contentId}` | create + update + read (delete denied) | same fields, `expiresAtMillis` mandatory and must exceed `grantedAtMillis` | owner-only, closed field set, doc-id agreement, expiry-ordering check |
| `users/{uid}/catalogOverrides/{catalogAffirmationId}` | read/write | override fields | owner-only |
| `catalogAffirmations/`, `catalogUniverses/`, `catalogThemes/`, `catalogCollections/`, `catalogMeta/` | read only (world-readable) | — | **write: if false** for every client; written exclusively by `functions/tools/seedCatalog.ts` via Admin SDK, which is not reachable from any deployed/client-facing surface |

**Privileged-field grep result:** no client write path anywhere sets or trusts a field named `role`, `admin`, `isAdmin`, `permissions`, `status` (in a privilege sense), or an entitlement/premium flag inside a document that rules would honor. The one flag that looks like it ("premium"/"Pro" state) is resolved exclusively server-side; `BillingService.kt` does hold a local `_optimisticProUntilMillis` UX flag for ~10 minutes post-purchase, but it is explicitly documented and confirmed **not** to gate any Firestore-protected resource or feed any server write — it only smooths the UI while the real server-side sync (`syncEntitlement` Cloud Function → Play Developer API → `entitlements` doc) catches up.

**Entitlement write path (the one place a forged/replayed request could theoretically matter):** `syncEntitlement` requires a valid Firebase ID token, and the uid actually written is the `externalAccountIdentifiers.obfuscatedExternalAccountId` returned by the **Play Developer API** for the supplied `purchaseToken`, not the caller's own uid. This looks unusual on first read (a function writing to a uid different from the caller) but is safe by construction: Google's Play API, not the caller, decides which account a purchase token belongs to, so a forged/foreign token either fails verification or harmlessly re-confirms its true owner's already-correct entitlement. There is no path by which calling this function with an arbitrary token elevates the caller's own entitlement.

---

## 8. Firebase Security Rules assessment

Full text of `firestore.rules` was read and reasoned through adversarially, case by case:

| Adversarial case | Result |
|---|---|
| Unauthenticated → any `/users/{uid}/...` doc | **Denied** — every rule requires `request.auth != null` |
| Unauthenticated → catalog collections | **Allowed (by design)** — world-readable, content already ships in every APK, no user data involved |
| User A → user A's own doc | **Allowed**, as intended |
| User A → user B's doc (any `/users/{uid}/...` collection) | **Denied** — every rule is `request.auth.uid == uid` against the path segment, not a client-supplied field, so it cannot be spoofed by document content |
| Normal user → `entitlements` write (their own doc) | **Denied unconditionally** (`allow write: if false`), including the owner — this is the strongest and most important rule in the file |
| Normal user → `notificationPlans` write | **Denied unconditionally** |
| Normal user → catalog write | **Denied unconditionally** for all 5 catalog collections |
| Normal user changes ownership (`uid` field in a payload) | **N/A/moot** — no collection stores a client-supplied `uid`/`ownerId` field that rules would honor instead of the path segment; ownership is always the path segment, never document content |
| Normal user changes role/admin/isPremium field | **N/A** — no such field exists in any client-writable document type |
| Normal user injects unexpected fields | **Denied** for `adUnlocks`/`timedUnlocks`/`fcmTokens` via `hasOnly(...)` closed-field-set checks; **not enforced** for `affirmations`/`dailyMoods`/`dailyCompletions`/`streakHealerUses`/`settings`/`meta` (open schema) — acceptable since none of these are privileged, but means a modified client can write arbitrary extra fields/junk data into its own documents (self-harm only, not a cross-user or privilege issue) |
| Normal user deletes protected objects | `adUnlocks`: delete denied. `timedUnlocks`: delete denied. `entitlements`/`notificationPlans`/catalog: delete denied via blanket `write: if false`. Regular personal collections: delete allowed (bundled under `write`), which is expected/benign for the user's own data |
| User enumerates collections | Firestore rules do not by themselves prevent a signed-in user from *attempting* a query against another uid's subcollection path, but every read is still evaluated per-document against `request.auth.uid == uid`, so enumeration attempts return `PERMISSION_DENIED`/empty, not data. World-readable catalog collections are of course fully enumerable, by design (public content) |

**Deliberate, documented weakening — `adUnlocks`:** the client is trusted to decide *when* a rewarded-ad unlock exists, because there is no server-side signal for "the user watched the ad." Rules bound the blast radius tightly: owner-only, closed field set, doc-id must algebraically match its own fields, and **create-only** (no update/delete), so a modified client can self-grant unlocks for low-value content but cannot erase or back-date an existing one. This is explicitly called out in the rules file's own comments as a recorded design trade-off (referencing "Spec 1 design, Q7"), not an oversight. Confirmed consistent with the code in `FirestoreAdUnlockRepository.kt`.

**Cross-check against `functions/test/firestore.rules.test.ts`:** 22 test cases exist across 4 suites (entitlements, adUnlocks, timedUnlocks, shared-catalog/catalogOverrides). Only the **entitlements suite (3 tests)** was confirmed executable in the environment available to this audit; the remaining **19 tests** (adUnlocks, timedUnlocks, catalog, catalogOverrides — including most of the adversarial cross-uid and shape-validation cases) are annotated in the file itself as environment-blocked (JDK8 present vs. JDK21 required, Firebase CLI unavailable) and were not confirmed to have actually run. This is a genuine gap: the correctness of those 19 cases currently rests on manual rules review (done in this audit and found sound) rather than on verified, repeatable CI evidence. See F-06 and §16 for remediation.

**Coverage gaps in the test suite itself** (irrespective of the execution-environment issue): no test exists for `notificationPlans` write-denial, and no cross-uid test exists for the six "simple" owner-only collections (`affirmations`, `dailyCompletions`, `dailyMoods`, `streakHealerUses`, `settings`, `meta`, `fcmTokens`). These are lower-risk (simple, uniform `request.auth.uid == uid` rules, easy to eyeball-verify), but currently have zero automated regression protection.

---

## 9. Android platform assessment

- **Manifest:** only `MainActivity` is exported (mandatory LAUNCHER), intent filter is `MAIN`/`LAUNCHER` only — no deep links, no App Links, no custom URI scheme. `WeeklyTrackerWidgetReceiver` and `AffirmityMessagingService` are both `exported="false"`. No `<provider>`, no custom `<permission>`. **No exported-component attack surface beyond the unavoidable launcher.**
- **Backup:** `android:allowBackup="true"` with **stock, unedited** `backup_rules.xml` and `data_extraction_rules.xml` (all `<include>`/`<exclude>` directives commented out, template TODOs still present). Full app data — including the unencrypted Room DB with free-text mood notes — is eligible for Auto Backup / `adb backup`. See F-02.
- **WebView:** absent entirely from the codebase — eliminates an entire vulnerability class.
- **Network/TLS:** no `network_security_config.xml`, no custom `TrustManager`/`HostnameVerifier`, no `usesCleartextTraffic` override. Platform default at `targetSdk 36` blocks cleartext HTTP. No certificate pinning, which is an acceptable default for this app's threat model (no cert-pinning-worthy secrets in transit beyond what Firebase's own SDKs already protect).
- **Local storage:** DataStore Preferences (non-sensitive flags only) + Room SQLite (unencrypted, no SQLCipher). No SharedPreferences usage at all. No tokens/credentials stored locally by app code — Firebase Auth session management is delegated to the Firebase SDK's own storage.
- **Logging:** 26 `Log.*` call sites reviewed; only issue found is the Firebase UID logged at `Log.d` in `AffirmityAppState.kt` (5 call sites) — see F-03. No tokens, emails, or ID-token values are logged anywhere.
- **Screenshot protection:** `FLAG_SECURE` is not used anywhere — mood-note and other personal-data screens are screenshot/screen-recording-capturable by the OS/other apps with recording permission. Low severity for this app's data class.
- **Cryptography/Keystore/Biometrics:** none implemented in-app (zero `Cipher`/`KeyStore`/`BiometricPrompt`/`MessageDigest`/`SecureRandom` usage) — all crypto is delegated to Firebase Auth/Credential Manager. No custom-crypto-misuse risk because none exists.
- **Build config:** release build sets `optimization { enable = false }` with **no `proguard-rules.pro` content beyond the empty AGP template** — R8/ProGuard shrinking, obfuscation, and optimization are fully disabled for release. See F-01. AdMob production secrets are correctly sourced from a Gradle property → `local.properties` (gitignored) → environment-variable fallback chain, with a fail-fast guard preventing an accidental release build shipping Google's public test ad unit IDs. Debug builds always use Google's well-known public test ad unit constants (not secrets).
- **Signing:** no `signingConfigs {}` block is present in `app/build.gradle.kts`; this repo alone cannot confirm how release signing is actually performed (Play App Signing, CI-injected keystore, or local). Flagged as UNKNOWN, not a finding (see §15).
- **Intents/PendingIntents:** no `getParcelableExtra`/`getSerializableExtra` usage anywhere (no untrusted-extra parsing surface). All `PendingIntent`s use `FLAG_IMMUTABLE`. `Intent` construction targets `MainActivity` explicitly or system Settings — no implicit intents accepting external data.
- **Clipboard:** one usage (`MyAffirmationsScreen.kt`) via Compose's `LocalClipboardManager` for copying affirmation text — no sensitive data placed on the clipboard.
- **Dependencies:** modern, current-generation versions across Firebase BOM 34.16.0, Credential Manager 1.5.0, Play Billing 7.1.1, AdMob 25.4.0, Room 2.8.4, Coil 3.3.0. No Gson/Moshi or other deserialization-CVE-prone libraries in the dependency graph. No stale majors observed without a live CVE feed to cross-check (see §11).

---

## 10. OWASP MASVS / MASTG matrix

| Control | Status | Evidence | Notes |
|---|---|---|---|
| MASVS-STORAGE-1 (sensitive data not stored unprotected) | PASS (with caveat) | No credentials/tokens in Room/DataStore | Mood notes stored unencrypted — acceptable for data class, combine with F-02 |
| MASVS-STORAGE-2 (no sensitive data in logs) | FAIL (Low-Medium) | `AffirmityAppState.kt` UID logging | F-03 |
| MASVS-CRYPTO-1/2 (crypto implemented correctly) | NOT APPLICABLE | No app-level crypto implemented | Delegated entirely to Firebase/Credential Manager |
| MASVS-AUTH-1 (auth performed at a remote endpoint) | PASS | Firebase Auth, ID token exchange | |
| MASVS-AUTH-2 (session invalidated on logout) | PASS | `FirebaseAuth.signOut()` | Server-side account-deletion flow not found — see §15 |
| MASVS-NETWORK-1 (TLS everywhere) | PASS | No cleartext override, platform default enforced | |
| MASVS-NETWORK-2 (cert validation not weakened) | PASS | No custom TrustManager/HostnameVerifier found | |
| MASVS-PLATFORM-1 (safe use of IPC) | PASS | No exported components beyond mandatory launcher; immutable PendingIntents | |
| MASVS-PLATFORM-2 (WebView safety) | NOT APPLICABLE | No WebView in the app | |
| MASVS-PLATFORM-3 (safe use of platform APIs / clipboard) | PASS | Only benign clipboard write found | |
| MASVS-CODE-1 (app signing) | UNKNOWN | No `signingConfigs` visible in this repo | Requires local/CI verification, §18 |
| MASVS-CODE-2 (release hardening: R8/ProGuard) | FAIL (Medium) | `optimization { enable = false }`, empty proguard rules | F-01 |
| MASVS-CODE-4 (debuggable flag off in release) | PASS | No manifest override; AGP default (debuggable only for debug variant) | Confirm final compiled manifest for release, §18 |
| MASVS-RESILIENCE (anti-tampering/root detection) | NOT APPLICABLE / not implemented | No root-detection or integrity checks found | Not required given the actual trust model (server always re-verifies), but worth confirming Play Integrity is not silently expected elsewhere |
| MASVS-PRIVACY-1 (data minimization) | PASS | Only necessary personal fields stored; no PII beyond what Google Sign-In provides (name/email) is collected | |
| Backup exposure (MASTG-TEST-0038-class) | FAIL (Medium) | `allowBackup=true`, unedited rules | F-02 |
| FLAG_SECURE / screenshot protection | FAIL (Low) | No usage found | F-05 |
| Firestore security rules — authorization | PASS | Full adversarial trace in §8 | Test-execution gap noted separately, F-06 |
| Secret exposure (source + git history) | PASS | No secrets found anywhere | §12 |

---

## 11. Dependency assessment

**Android (`gradle/libs.versions.toml`):** AGP 9.3.1, Kotlin 2.2.10, Compose BOM 2025.12.00, Firebase BOM 34.16.0 (auth/firestore/messaging/analytics), androidx-credentials 1.5.0 + googleid 1.1.1, billing-ktx 7.1.1, play-services-ads 25.4.0, user-messaging-platform 4.0.0, Room 2.8.4, WorkManager 2.11.2, Coil 3.3.0 (pulls OkHttp transitively, version not independently pinned), org.json 20240303. No Gson/Moshi/raw-deserialization libraries. All versions read as current-generation; **none were cross-checked against a live CVE feed from this environment** — treat as REQUIRES DYNAMIC/ONLINE VERIFICATION rather than an asserted-clean result, per the audit's own evidence standard.

**Cloud Functions (`functions/package.json`, Node 22):** `firebase-admin` ^14.2.0, `firebase-functions` ^7.3.2, `google-auth-library` ^9.14.2 (used for OIDC verification — security-critical, current major line), `googleapis` ^174.0.1 (large surface, standard practice), `@google-cloud/tasks` ^5.5.0. All caret-ranged; actual installed versions can float above what's pinned in `package.json` on a fresh `npm install` — recommend `npm ci` against a committed lockfile for reproducible/audited builds if not already CI practice.

**CI:** no `.github/workflows/` directory exists in this repo — there is no CI pipeline to audit for secret-handling mistakes, and correspondingly no evidence of automated dependency/security scanning gating merges.

---

## 12. Secret exposure assessment

**No secrets found**, in the current working tree or in the complete git history (`git log --all --full-history -p`, all branches/refs). Broad pattern scanning for private keys, service-account JSON, API secrets, OAuth client secrets, AWS-style keys, and `.env`-shaped files returned no hits beyond expected, benign matches (environment-variable *names* like `PLAY_SERVICE_ACCOUNT_KEY_JSON` referenced via `process.env` in `functions/src/index.ts`, never a literal value; a fake fixture email in a test file; ordinary variables named `idToken`/`offerToken`).

`app/google-services.json` contains only the standard Firebase client configuration (project ID, app ID, OAuth client IDs, certificate hashes, and the public, restriction-based Firebase Web API key). Per Google's own documentation and this audit's instructions, this is **not a secret** and is not flagged as one — its protection comes from SHA-1/SHA-256 app-signing-certificate restriction and, more importantly, from `firestore.rules` being the actual authorization boundary.

`local.properties` and any keystore/credentials files are correctly `.gitignore`d and confirmed never committed (`git ls-files` clean). `.firebaserc` is tracked but contains only a project alias, not a credential.

---

## 13. Business-logic assessment

- **Entitlement/Pro status:** cannot be self-granted by any client-side action; the only write path is server-side, Play-API-verified. This is the single most important business-logic control in the app and it is sound.
- **Rewarded-ad unlocks (`adUnlocks`/`timedUnlocks`):** client-observed by design, so a modified client *can* self-grant unlocks for ad-gated content without watching an ad. This is a real, working abuse path against a low-value asset (already-shipped catalog content, not the paid subscription), explicitly accepted as a design trade-off in the code's own comments. Confirmed the blast radius is bounded: cannot touch entitlements, cannot be back-dated/erased once granted (create-only for `adUnlocks`), and `timedUnlocks`' update path still enforces `expiresAtMillis > grantedAtMillis`.
- **Streak/mood/completion data:** purely personal, no monetary or privilege implication if manipulated by the owning user themselves (which the rules allow, by design — it's their own data).
- **Catalog content:** world-readable by design (content already ships inside every downloaded APK, so read-restriction would add no real protection); write-denied to every client, enforced server-side.
- **Notification planning:** server-computed and server-written only; client cannot forge its own notification schedule server-side (though it can of course alter local notification behavior on-device, which has no security implication).

No replay/race-condition issue was found in the reviewed write paths beyond the already-accepted `adUnlocks` design trade-off. No workflow/state-machine bypass was found in the entitlement sync flow — `syncEntitlement`'s uid-resolution-via-Play-API behavior was specifically checked for privilege-escalation potential and found safe by construction (§7).

---

## 14. Findings ordered by severity

### F-01 — Release build ships with R8/ProGuard shrinking and obfuscation fully disabled
- **Severity:** Medium
- **Confidence:** CONFIRMED
- **CWE:** CWE-656 (Reliance on Security Through Obscurity as the *absent* layer — more precisely, this is a hardening gap, not itself an authz bypass)
- **MASVS control:** MASVS-CODE-2
- **File(s):** `app/build.gradle.kts` (release build type, `optimization { enable = false }`); `app/proguard-rules.pro` (default AGP template, no custom rules)
- **Attack prerequisites:** possession of the release APK (freely available once published to Play or side-loaded)
- **Attack path:** decompile the release APK with any standard tool (jadx, apktool); because minification/obfuscation is off, class and method names are intact, making it straightforward to locate and study `BillingService`, `FirestoreEntitlementRepository`, `FirestoreAdUnlockRepository`, and the ad-unlock flow to understand exactly how the client interacts with Firestore — accelerating the crafting of a modified client for the `adUnlocks`/`timedUnlocks` abuse case already described in §13.
- **Evidence:**
  ```kotlin
  release {
      optimization {
          enable = false
      }
      ...
  }
  ```
- **Impact:** does not by itself grant unauthorized data access (the real authorization boundary is `firestore.rules`, server-side, and unaffected by client obfuscation) — but materially lowers the effort required to understand and automate abuse of the already-accepted `adUnlocks` weakness, and generally increases the ease of any future reverse-engineering effort (e.g., extracting UI/business logic, understanding ad-fraud surfaces).
- **Existing protection:** none at the client layer; the real protection (Firestore rules) is unaffected by this issue.
- **Why it fails:** the release build type explicitly turns optimization off; there is no compensating control (no root/tamper detection, no Play Integrity check referenced anywhere in the codebase).
- **Recommended remediation:** enable R8 full mode for release (`optimization { enable = true }` under AGP 9's DSL, or `isMinifyEnabled = true` / `isShrinkResources = true` depending on final AGP API), add `-keep` rules for anything reflection/Compose-runtime-sensitive, and validate `assembleRelease` + smoke-test the resulting APK.
- **How to verify the fix:** build a release APK, decompile it, confirm class/method names are obfuscated (e.g., `a.b.c` style) and that the app still functions correctly end-to-end (sign-in, purchase flow, ad unlock, sync).

### F-02 — Unrestricted Auto Backup / ADB backup exports the full local database
- **Severity:** Medium
- **Confidence:** CONFIRMED
- **CWE:** CWE-530 (Exposure of Backup File to an Unauthorized Control Sphere)
- **MASVS control:** backup-exposure class (MASTG-TEST-0038 family)
- **File(s):** `app/src/main/AndroidManifest.xml` (`android:allowBackup="true"`, line ~13); `app/src/main/res/xml/backup_rules.xml`; `app/src/main/res/xml/data_extraction_rules.xml` (both are the unedited AGP template — all `<include>`/`<exclude>` directives commented out)
- **Attack prerequisites:** on API 31+, either the user's own Google account cloud-backup being restored to an attacker-controlled device, or physical/ADB access to an unlocked, USB-debugging-enabled device (`adb backup`, or on older/rooted devices without the API 31 restriction)
- **Attack path:** `adb backup -f backup.ab com.pirxhio.affirmity` (or a cloud-backup restore to another device under the same Google account) extracts the entire app-private storage, including the unencrypted Room database (`affirmity.db`) containing free-text mood notes, streak history, and affirmation content.
- **Evidence:**
  ```xml
  android:allowBackup="true"
  android:dataExtractionRules="@xml/data_extraction_rules"
  android:fullBackupContent="@xml/backup_rules"
  ```
  Both referenced XML files contain only commented-out template directives — no actual restriction.
- **Impact:** exposure of personal mood-note free text and usage history to whoever controls the backup channel. No Firebase Auth tokens or credentials are included (Firebase SDK manages its own session storage outside the scope reachable this way, and no token is cached in Room/DataStore per §9), so this is a **privacy** exposure of personal wellness data, not an account-takeover vector.
- **Existing protection:** none — the rules files are present but functionally no-ops.
- **Why it fails:** the template was never customized after being scaffolded.
- **Recommended remediation:** either set `android:allowBackup="false"`, or (preferably, to preserve the UX benefit of backup/restore across devices) populate `data_extraction_rules.xml` to exclude the Room database file(s) from cloud backup and device transfer, keeping only non-sensitive DataStore preference files if desired.
- **How to verify the fix:** attempt `adb backup` (or inspect the compiled `data_extraction_rules.xml` in the APK) and confirm the database file is absent from the resulting backup archive.

### F-03 — Firebase UID logged in plaintext at `Log.d`, unstripped in release builds
- **Severity:** Low-Medium
- **Confidence:** CONFIRMED
- **CWE:** CWE-532 (Insertion of Sensitive Information into Log File)
- **MASVS control:** MASVS-STORAGE-2
- **File(s):** `app/src/main/java/com/pirxhio/affirmity/data/AffirmityAppState.kt`, 5 call sites logging `"... for uid=$uid"` on the FCM/timezone-sync path
- **Attack prerequisites:** `adb logcat` access to a signed-in device (physical access, or a malicious app with the (heavily restricted, but not impossible on some OEM/rooted configurations) `READ_LOGS`-equivalent capability)
- **Attack path:** read logcat while the app performs its FCM token/timezone sync (happens routinely, e.g., on app start while signed in) to recover the victim's stable Firebase UID, enabling correlation of "this device" to "this specific backend user" across sessions/log captures.
- **Evidence:** `Log.d(TAG, "fcm/timezone sync: ... for uid=$uid")` pattern, 5 occurrences.
- **Impact:** UID disclosure alone does not grant data access (Firestore rules still require a valid session for that uid), but it is a stable tracking/correlation identifier and is unnecessary information disclosure. Because R8 is disabled release-wide (F-01), there is no automatic stripping of `Log.d` calls in release builds via ProGuard's typical `-assumenosideeffects android.util.Log` pattern (and no such rule exists in `proguard-rules.pro` regardless).
- **Existing protection:** none — no `BuildConfig.DEBUG` gate around these calls, no Log stripping.
- **Why it fails:** logging statement was written for debugging convenience without a release gate.
- **Recommended remediation:** remove the uid interpolation from these log lines, or gate them behind `if (BuildConfig.DEBUG)`, or (once F-01 is fixed) add a `-assumenosideeffects` rule for `Log.d`/`Log.v` in release ProGuard rules.
- **How to verify the fix:** grep the release-configured build output / decompiled APK for the log strings and confirm the uid is no longer interpolated, or confirm the calls are compiled out entirely.

### F-04 — Rewarded-ad unlock grants are client-observed and self-grantable by a modified client
- **Severity:** Low (by design, explicitly accepted trade-off; documented here for completeness per the audit's adversarial-review mandate)
- **Confidence:** CONFIRMED
- **CWE:** CWE-807 (Reliance on Untrusted Inputs in a Security Decision)
- **MASVS control:** business-logic / MASVS-AUTH (server-side authorization of a state transition)
- **File(s):** `firestore.rules` (`users/{uid}/adUnlocks/{contentKey}`, `users/{uid}/timedUnlocks/{contentKey}` rules); `app/src/main/java/com/pirxhio/affirmity/data/remote/FirestoreAdUnlockRepository.kt`
- **Attack prerequisites:** a modified/rooted client, or direct use of the Firestore SDK/REST API with a valid Firebase ID token for the attacker's own account (no cross-user impact possible — rules still enforce owner-only)
- **Attack path:** call `set()` on `users/{ownUid}/adUnlocks/{contentType}_{contentId}` directly with a well-formed payload (matching the closed field set and doc-id/field agreement the rules require) without ever showing a rewarded ad, granting the attacker's own account unlocked access to ad-gated (but not paid-tier) content.
- **Evidence:** rules comment: *"Client-writable by design... a modified client can self-grant an ad unlock, which is accepted for low-value content."* — this is a self-disclosed, intentional design decision, not a hidden bug.
- **Impact:** limited to the attacker's own account gaining free access to already-shipped, low-value catalog content that is not gated by the paid subscription. Cannot reach `entitlements`, cannot affect other users, cannot be used to erase/back-date an existing grant (create-only for `adUnlocks`; `timedUnlocks` still enforces `expiresAtMillis > grantedAtMillis` on update).
- **Existing protection:** owner-only write, closed field set, doc-id/field self-consistency check, create-only immutability for `adUnlocks`.
- **Why it (partially) fails:** there is genuinely no server-side signal available to distinguish "user watched the full rewarded ad" from "user did not" — AdMob's server-side verification (SSV) callback mechanism exists and could close this gap if adopted, but is not currently wired into a Cloud Function.
- **Recommended remediation (optional, given this is an accepted trade-off):** if the business ever wants this closed, integrate AdMob Server-Side Verification (SSV) callbacks into a Cloud Function that then performs the Firestore write via the Admin SDK, mirroring the `entitlements` pattern, and flip the client-write rule to `allow write: if false`.
- **How to verify a fix (if implemented):** attempt the same direct-SDK write from a test client and confirm `PERMISSION_DENIED`.

### F-05 — No `FLAG_SECURE` on screens displaying personal mood/affirmation data
- **Severity:** Low
- **Confidence:** CONFIRMED
- **CWE:** CWE-200 (Exposure of Sensitive Information, via screen capture)
- **MASVS control:** platform/UI data exposure
- **File(s):** app-wide — zero `FLAG_SECURE` usage found
- **Attack prerequisites:** another app on the device with screen-recording/screenshot capability (e.g., `MediaProjection`-based malware, or OS-level "recent apps" thumbnails), or a malicious accessibility service
- **Attack path:** capture a screenshot or screen recording while the mood-note or affirmation screens are visible.
- **Impact:** low — this app's data class (self-help affirmations, mood notes) is personal but not regulated/financial-grade; disclosure risk is real but limited in severity.
- **Recommended remediation:** apply `FLAG_SECURE` on the mood-entry and any other screen the product owner considers sensitive enough to warrant it; optional given the data class.
- **How to verify the fix:** attempt a screenshot on the flagged screen and confirm it is blocked/blacked-out.

### F-06 — Majority of Firestore rules adversarial test cases unconfirmed to execute
- **Severity:** Low (process/coverage gap, not a live vulnerability — the rules themselves were manually verified sound in §8)
- **Confidence:** CONFIRMED (as a coverage gap; the underlying rules correctness is HIGH CONFIDENCE via manual review, not CONFIRMED via CI)
- **File(s):** `functions/test/firestore.rules.test.ts` (398 lines, 22 `it()` cases across 4 `describe` blocks)
- **Details:** only the `entitlements` suite (3 tests) was confirmed to actually execute in the environment available to this audit. The remaining 19 tests (adUnlocks: 8, timedUnlocks: 9, catalog+catalogOverrides: remainder) are annotated in-file as environment-blocked, citing a JDK version mismatch (JDK 8 present vs. JDK 21 required by the Firebase emulator) and Firebase CLI unavailability.
- **Impact:** the project's *stated* regression protection for the most security-relevant collections (the ones with actual shape/immutability validation) is not currently verified to run, anywhere this audit could observe. This is a process risk: a future rules change could silently break adUnlocks/timedUnlocks validation without CI catching it, if no environment ever successfully runs these tests.
- **Recommended remediation:** fix the local/CI environment to run under JDK 21 with the Firebase CLI installed, execute `npm run test:rules` (or equivalent) end-to-end, and confirm all 22 cases pass; wire this into CI (currently no `.github/workflows/` exists at all — see §11) so it runs on every rules change.
- **How to verify the fix:** run the full suite under the correct JDK/CLI and confirm 22/22 pass with real emulator output, not skip/annotation text.

### Additional lower-priority observations (Informational, not separately numbered as findings)
- `functions/test/firestore.rules.test.ts` has no test coverage at all for `notificationPlans` write-denial or for cross-uid access on the six "simple" owner-only collections (`affirmations`, `dailyCompletions`, `dailyMoods`, `streakHealerUses`, `settings`, `meta`, `fcmTokens`). Low risk (simple, uniform rule shape) but zero automated protection.
- No `signingConfigs` block found in `app/build.gradle.kts` — release signing mechanism could not be confirmed from source alone (§15/§18).
- No account-deletion / server-side data-purge flow was found triggered from sign-out — worth confirming against any privacy-policy commitments (§15).
- Coil's OkHttp version is not independently pinned in the version catalog — inherits whatever Coil 3.3.0 declares; worth confirming during a dependency audit refresh (§11).

---

## 15. Hypotheses requiring dynamic/manual testing

- Whether the compiled release manifest actually has `android:debuggable="false"` (expected via AGP default, not independently verified against a built artifact).
- Whether `adb backup` in practice actually extracts the Room database file given the current backup-rules configuration (F-02 reasoned from rules-as-written; not observed against a real backup archive).
- Whether direct Firestore SDK/REST calls with a forged/malformed `adUnlocks`/`timedUnlocks` payload are actually rejected as the rules text implies (F-06 — rules were reasoned through manually, not executed against the emulator in this environment).
- Whether AdMob's rewarded-ad callback can be triggered by a modified client without actually completing an ad view, and whether any client-side timing/state checks add meaningful friction beyond the Firestore rules already covering the write itself.
- Whether the `syncEntitlement` Cloud Function has any rate-limiting; a client could call it repeatedly with arbitrary purchase tokens (each individually harmless per §7, but worth confirming there's no quota-exhaustion or Play-API-abuse angle at volume).
- Whether Firebase App Check is deployed on the project (not referenced anywhere in the reviewed source, `firebase.json`, or `firestore.rules`) — its absence is not itself a vulnerability given rules are otherwise sound, but it would raise the cost of the accepted `adUnlocks` self-grant abuse (F-04) if added.
- Confirmation of the actual release-signing mechanism (Play App Signing vs. CI-managed keystore vs. other) — not visible from this repository alone.
- Whether an account-deletion / data-purge flow exists anywhere outside the reviewed files (e.g., triggered from a Cloud Function on Firebase Auth user deletion) — not found in the reviewed `auth/` or `functions/` source, but the search was not exhaustive of every function trigger type.

---

## 16. Tests recommended for the local phase (proposed, not implemented)

For `functions/test/firestore.rules.test.ts` (get the existing 19 blocked tests running first, then add):

1. `notificationPlans`: assert owner write denied, non-owner write denied, non-owner read denied.
2. Cross-uid negative tests for each of `affirmations`, `dailyCompletions`, `dailyMoods`, `streakHealerUses`, `settings`, `meta`, `fcmTokens`: user B cannot read or write user A's document.
3. `entitlements`: explicit non-owner write-denied test (currently only owner-write-denied and cross-uid-read-denied are asserted; add the cross-uid-write case for completeness even though it's implied).
4. `adUnlocks`/`timedUnlocks`: wrong-typed field fuzzing (e.g., `contentType` as a number, `grantedAtMillis` as a string) beyond the existing "missing field"/"extra field" cases.
5. `adUnlocks`/`timedUnlocks`: attempt to overwrite an existing `adUnlocks` doc via `update()` explicitly asserting `PERMISSION_DENIED` (currently covered per the inventory, re-list here for completeness once environment is fixed).
6. Unauthenticated read/write attempts against every single collection, not just the ones currently covered, as a systematic sweep (a simple loop over collection names would give full coverage cheaply).
7. `fcmTokens`: attempt to create a token document whose id does not match its own `token` field, and whose `platform` is not `'android'` — confirm both rejected (shape checks exist in rules; add explicit tests).
8. Add a CI job (`.github/workflows/`, currently absent) that runs the full rules-test suite under JDK 21 with the Firebase CLI on every PR touching `firestore.rules` or `functions/`.

For Cloud Functions (`functions/test/*.test.ts`, currently billing/fcm/healer/planner/schedule/streak/seedCatalog covered):

9. `syncEntitlement`: test with a purchase token belonging to a different account than the caller, asserting the write lands on the token's true owner (per Play API) and does not create/modify anything under the caller's own uid.
10. `sendNotification`: test that a request without a valid invoker-SA OIDC token is rejected, and that a request with a valid token but a forged `uid` in the body still only writes within the pre-planned scope set by `planNotifications` (i.e., a caller cannot use this endpoint to push notifications to an arbitrary uid at will).
11. `playRtdn`: test a forged Pub/Sub push payload without a valid OIDC push-claim is rejected before any Play API call or Firestore write is attempted.

---

## 17. Prioritized remediation roadmap

1. **F-01 (Medium):** re-enable R8/ProGuard shrinking + obfuscation for release builds; add minimal `-keep` rules as needed; verify `assembleRelease` still functions end-to-end.
2. **F-02 (Medium):** customize `data_extraction_rules.xml`/`backup_rules.xml` to exclude the Room database (or set `allowBackup="false"` if backup/restore UX is not a product requirement).
3. **F-06 (Low, but blocks confidence in everything else in §8):** fix the JDK/CLI environment and get all 22 Firestore rules tests actually executing; wire into CI (also addresses the total absence of any CI pipeline, noted in §11).
4. **F-03 (Low-Medium):** strip or gate the UID logging in `AffirmityAppState.kt`.
5. **F-05 (Low):** apply `FLAG_SECURE` to mood/personal-data screens if the product owner wants this hardening.
6. **F-04 (Low, accepted trade-off):** leave as-is unless the business decides the ad-unlock abuse surface needs closing, in which case adopt AdMob SSV + a Cloud-Function-mediated write.
7. Close the remaining §15 UNKNOWNs (release signing mechanism, account-deletion flow) via direct confirmation with the project owner/CI configuration, and consider adding Firebase App Check as defense-in-depth around F-04.

---

## 18. LOCAL FOLLOW-UP PLAN (cannot be completed reliably from this environment)

The following require tooling, execution environments, or production access not available to this remote/source-only audit:

- **APK-level analysis:** build the actual release APK, decompile it (jadx/apktool), and confirm in the compiled artifact: R8 status post-fix, whether `debuggable` is truly `false`, whether any string/secret was inlined by the build system that isn't visible in source.
- **Emulator/runtime execution:** run the app on an emulator or device to observe actual runtime behavior of the auth flow, ad-unlock flow, and entitlement sync under normal and adversarial conditions.
- **ADB-based testing:** perform an actual `adb backup`/restore cycle to confirm F-02's real-world impact (what specifically is exported) and confirm the fix once applied.
- **Runtime Intent/component attacks:** although no exported attack surface was found in source, dynamic fuzzing of `MainActivity` with crafted intents (e.g., via `adb shell am start`) would provide empirical confirmation.
- **MobSF or equivalent dynamic analysis:** automated dynamic scan of the built APK for additional runtime-only findings (e.g., in-memory secret exposure, dynamic class loading) not detectable from static source review.
- **Firebase Emulator Suite execution:** actually run `functions/test/firestore.rules.test.ts` (all 22 cases) and the Cloud Functions test suites under a correctly provisioned JDK 21 + Firebase CLI environment — this audit could only review the test source and reason about the rules manually; F-06 exists specifically because this could not be executed here.
- **Traffic interception:** proxy the app's network traffic (e.g., via a MITM proxy with a device-installed CA, or Frida-based SSL unpinning if ever added) to confirm no plaintext-sensitive data crosses the wire and that Firebase/Play Billing traffic behaves as expected under adversarial network conditions.
- **Play Integrity / App Check runtime validation:** confirm whether Play Integrity or Firebase App Check is actually enforced at the project level in production (not referenced in any reviewed source/config, but project-level App Check enforcement settings live in the Firebase Console, not in this repo).
- **Authenticated production Firebase project access:** verify actual deployed `firestore.rules` in production match the repo's `firestore.rules` (no drift), inspect actual Cloud Functions IAM/invoker bindings as deployed (not just as coded), and confirm no additional Firestore collections/documents exist in production that aren't reflected in this repo's rules file.
- **Release signing confirmation:** confirm with the project owner or CI configuration how release APKs/AABs are actually signed (Play App Signing enrollment, CI-injected keystore, etc.) — not determinable from this repository alone.
- **Rate-limiting / abuse-volume testing:** load-test `syncEntitlement` and the AdMob-adjacent write paths to confirm there's no quota-exhaustion or cost-abuse angle at scale.
