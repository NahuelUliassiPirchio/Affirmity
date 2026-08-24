import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc } from 'firebase/firestore';
import { afterAll, beforeAll, describe, it } from 'vitest';

// Spec: "Server-write-only entitlement persistence" -- owner-read, deny-all-write on
// users/{uid}/entitlements/{doc} (design.md D4). Requires the Firestore emulator (see
// `firebase.json`'s "emulators.firestore" block); started via `firebase emulators:exec`.
describe('firestore.rules: users/{uid}/entitlements/{doc}', () => {
  let testEnv: RulesTestEnvironment;

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: 'demo-affirmity-rules-test',
      firestore: {
        rules: readFileSync(resolve(__dirname, '../../firestore.rules'), 'utf8'),
        host: '127.0.0.1',
        port: 8080,
      },
    });
  });

  afterAll(async () => {
    await testEnv?.cleanup();
  });

  it('denies a client write attempt, even by the owner', async () => {
    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertFails(
      setDoc(doc(ownerDb, 'users/uid-owner/entitlements/current'), { tier: 'pro' }),
    );
  });

  it('allows the owner to read their own entitlement doc', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'users/uid-owner/entitlements/current'), { tier: 'pro' });
    });

    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertSucceeds(getDoc(doc(ownerDb, 'users/uid-owner/entitlements/current')));
  });

  it('denies reading another uid\'s entitlement doc', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'users/uid-owner/entitlements/current'), { tier: 'pro' });
    });

    const otherDb = testEnv.authenticatedContext('uid-other').firestore();
    await assertFails(getDoc(doc(otherDb, 'users/uid-owner/entitlements/current')));
  });
});

// [ENVIRONMENT-BLOCKED, written but not executed in this sandbox -- see task C.12] Spec:
// "Client-writable, create-only ad-unlock grant persistence" -- owner-only read/create, closed
// field set, doc-id/field agreement, and no update/delete, ever (design.md Spec 1 §8 / Q7).
// Requires the Firestore emulator (see `firebase.json`'s "emulators.firestore" block); started
// via `firebase emulators:exec` -- blocked here because the local JDK is 8 and the emulator
// needs JDK 21+ (matches the free-pro-subscription precedent, tasks 1.4/8.2).
describe('firestore.rules: users/{uid}/adUnlocks/{contentKey}', () => {
  let testEnv: RulesTestEnvironment;

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: 'demo-affirmity-rules-test',
      firestore: {
        rules: readFileSync(resolve(__dirname, '../../firestore.rules'), 'utf8'),
        host: '127.0.0.1',
        port: 8080,
      },
    });
  });

  afterAll(async () => {
    await testEnv?.cleanup();
  });

  const wellShapedGrant = {
    contentType: 'affirmationGroup',
    contentId: 'fuerza_de_voluntad',
    grantedAtMillis: 1_755_216_000_000,
    expiresAtMillis: null,
  };
  const docId = 'affirmationGroup_fuerza_de_voluntad';

  it('1. owner creates a well-shaped grant -> succeeds', async () => {
    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertSucceeds(
      setDoc(doc(ownerDb, `users/uid-owner/adUnlocks/${docId}`), wellShapedGrant),
    );
  });

  it('2. doc id disagreeing with contentType/contentId -> fails', async () => {
    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertFails(
      setDoc(doc(ownerDb, 'users/uid-owner/adUnlocks/meditation_calma'), wellShapedGrant),
    );
  });

  it('3. unknown extra field -> fails', async () => {
    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertFails(
      setDoc(doc(ownerDb, `users/uid-owner/adUnlocks/${docId}`), {
        ...wellShapedGrant,
        extra: 'nope',
      }),
    );
  });

  it('4. missing grantedAtMillis -> fails', async () => {
    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    const { grantedAtMillis: _omit, ...withoutGrantedAt } = wellShapedGrant;
    await assertFails(
      setDoc(doc(ownerDb, `users/uid-owner/adUnlocks/${docId}`), withoutGrantedAt),
    );
  });

  it('5. owner updating an existing grant -> fails', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), `users/uid-owner/adUnlocks/${docId}`), wellShapedGrant);
    });

    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertFails(
      setDoc(doc(ownerDb, `users/uid-owner/adUnlocks/${docId}`), {
        ...wellShapedGrant,
        grantedAtMillis: 9_999_999_999_999,
      }),
    );
  });

  it('6. owner deleting an existing grant -> fails', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), `users/uid-owner/adUnlocks/${docId}`), wellShapedGrant);
    });

    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertFails(deleteDoc(doc(ownerDb, `users/uid-owner/adUnlocks/${docId}`)));
  });

  it('7. a different uid create/read under this uid -> fails', async () => {
    const otherDb = testEnv.authenticatedContext('uid-other').firestore();
    await assertFails(
      setDoc(doc(otherDb, `users/uid-owner/adUnlocks/${docId}`), wellShapedGrant),
    );

    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), `users/uid-owner/adUnlocks/${docId}`), wellShapedGrant);
    });
    await assertFails(getDoc(doc(otherDb, `users/uid-owner/adUnlocks/${docId}`)));
  });

  it('8. unauthenticated create/read -> fails', async () => {
    const anonDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(
      setDoc(doc(anonDb, `users/uid-owner/adUnlocks/${docId}`), wellShapedGrant),
    );

    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), `users/uid-owner/adUnlocks/${docId}`), wellShapedGrant);
    });
    await assertFails(getDoc(doc(anonDb, `users/uid-owner/adUnlocks/${docId}`)));
  });
});
