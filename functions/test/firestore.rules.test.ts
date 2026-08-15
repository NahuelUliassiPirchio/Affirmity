import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc } from 'firebase/firestore';
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
