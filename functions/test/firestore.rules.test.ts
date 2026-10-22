import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, orderBy, query, setDoc, where } from 'firebase/firestore';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

describe('firestore.rules: activity journal', () => {
  let testEnv: RulesTestEnvironment;
  const event = {
    eventId: 'session', kind: 'guided', epochDay: 20000, occurredAtMillis: 1000,
    startMillis: 0, endMillis: 1000, zoneId: 'UTC', meditationId: 'breathing',
    nameSnapshot: 'Breathing', activeSeconds: 120, sourceId: 'device',
  };
  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: 'demo-affirmity-rules-test',
      firestore: { rules: readFileSync(resolve(__dirname, '../../firestore.rules'), 'utf8'), host: '127.0.0.1', port: 8080 },
    });
  });
  afterAll(async () => { await testEnv?.cleanup(); });
  it('owner persists an event and may retry the identical payload', async () => {
    const ref = doc(testEnv.authenticatedContext('activity-owner').firestore(), 'users/activity-owner/activityEvents/session');
    await assertSucceeds(setDoc(ref, event));
    await assertSucceeds(setDoc(ref, event));
  });
  it('rejects event mutation and deletion but allows owner reads', async () => {
    const ref = doc(testEnv.authenticatedContext('activity-owner').firestore(), 'users/activity-owner/activityEvents/session');
    await assertSucceeds(setDoc(ref, event));
    await assertSucceeds(getDoc(ref));
    await assertFails(setDoc(ref, { ...event, activeSeconds: 121 }));
    await assertFails(deleteDoc(ref));
  });
  it('denies foreign and unauthenticated reads and writes', async () => {
    for (const context of [testEnv.authenticatedContext('other'), testEnv.unauthenticatedContext()]) {
      const ref = doc(context.firestore(), 'users/activity-owner/activityEvents/session');
      await assertFails(getDoc(ref));
      await assertFails(setDoc(ref, event));
    }
  });
  it('rejects malformed event identities, types, metrics and extra fields', async () => {
    const db = testEnv.authenticatedContext('activity-owner').firestore();
    const invalid = [
      { eventId: 'different' }, { extra: true },
      { kind: 'abandoned' }, { activeSeconds: -1 },
      { activeSeconds: '120' }, { endMillis: -1 },
      { nameSnapshot: '' }, { sourceId: '' }, { kind: 'view' },
    ];
    for (const [index, payload] of invalid.entries()) {
      const id = `invalid-${index}`;
      await assertFails(setDoc(doc(db, `users/activity-owner/activityEvents/${id}`), { ...event, eventId: id, ...payload }));
    }
  });
  it('accepts views and free sessions without conflating their shape', async () => {
    const db = testEnv.authenticatedContext('activity-owner').firestore();
    await assertSucceeds(setDoc(doc(db, 'users/activity-owner/activityEvents/view'),
      { ...event, eventId: 'view', kind: 'view', nameSnapshot: null, meditationId: null, activeSeconds: 0 }));
    await assertSucceeds(setDoc(doc(db, 'users/activity-owner/activityEvents/free'),
      { ...event, eventId: 'free', kind: 'free', nameSnapshot: 'Free timer', meditationId: null }));
  });
  const coverageId = 'a'.repeat(64);
  const coverage = { sourceId: 'device', recordingSchema: 1, fromEpochDay: 20000, toEpochDay: 20001, uncertain: false };
  it('owner persists immutable scalar coverage intervals with identical retry', async () => {
    const ref = doc(testEnv.authenticatedContext('activity-owner').firestore(), `users/activity-owner/activityCoverage/${coverageId}`);
    await assertSucceeds(setDoc(ref, coverage));
    await assertSucceeds(setDoc(ref, coverage));
  });
  it('rejects coverage mutation and deletion but allows owner reads', async () => {
    const ref = doc(testEnv.authenticatedContext('activity-owner').firestore(), `users/activity-owner/activityCoverage/${coverageId}`);
    await assertSucceeds(setDoc(ref, coverage));
    await assertSucceeds(getDoc(ref));
    await assertFails(setDoc(ref, { ...coverage, toEpochDay: 20002 }));
    await assertFails(deleteDoc(ref));
  });
  it('denies foreign and unauthenticated access to coverage', async () => {
    for (const context of [testEnv.authenticatedContext('other'), testEnv.unauthenticatedContext()]) {
      const ref = doc(context.firestore(), `users/activity-owner/activityCoverage/${coverageId}`);
      await assertFails(getDoc(ref));
      await assertFails(setDoc(ref, coverage));
    }
  });
  it('rejects malformed scalar coverage on creation, not just conflicting updates', async () => {
    const db = testEnv.authenticatedContext('activity-owner').firestore();
    const invalid = [
      { extra: [] }, { sourceId: '' }, { sourceId: 'x'.repeat(129) }, { sourceId: 'bad/source' },
      { recordingSchema: 0 }, { recordingSchema: 1.5 }, { recordingSchema: '1' },
      { fromEpochDay: 20002 }, { toEpochDay: '20001' }, { uncertain: 0 }, { uncertain: null },
    ];
    for (const [index, payload] of invalid.entries()) {
      const id = index.toString(16).padStart(64, '0');
      await assertFails(setDoc(doc(db, `users/activity-owner/activityCoverage/${id}`), { ...coverage, ...payload }));
    }
    await assertFails(setDoc(doc(db, 'users/activity-owner/activityCoverage/not-a-hash'), coverage));
    const { uncertain: _omit, ...missing } = coverage;
    await assertFails(setDoc(doc(db, `users/activity-owner/activityCoverage/${'b'.repeat(64)}`), missing));
  });
  it('queries only overlapping scalar intervals for the requested month', async () => {
    const db = testEnv.authenticatedContext('overlap-owner').firestore();
    const ranges = [[19900, 19999], [19999, 20002], [20020, 20040], [20032, 20040]];
    for (const [index, range] of ranges.entries()) {
      await assertSucceeds(setDoc(doc(db, `users/overlap-owner/activityCoverage/${index.toString(16).repeat(64)}`),
        { ...coverage, fromEpochDay: range[0], toEpochDay: range[1] }));
    }
    const result = await assertSucceeds(getDocs(query(collection(db, 'users/overlap-owner/activityCoverage'),
      where('fromEpochDay', '<=', 20031), where('toEpochDay', '>=', 20000),
      orderBy('fromEpochDay'), orderBy('toEpochDay'))));
    expect(result.docs.map((snapshot) => snapshot.id)).toEqual(['1'.repeat(64), '2'.repeat(64)]);
  });
  it('declares and wires the production interval-overlap composite index', () => {
    const indexes: unknown = JSON.parse(readFileSync(resolve(__dirname, '../../firestore.indexes.json'), 'utf8'));
    expect(indexes).toMatchObject({ indexes: expect.arrayContaining([{
      collectionGroup: 'activityCoverage', queryScope: 'COLLECTION', fields: [
        { fieldPath: 'fromEpochDay', order: 'ASCENDING' }, { fieldPath: 'toEpochDay', order: 'ASCENDING' },
      ],
    }]) });
    const firebase: unknown = JSON.parse(readFileSync(resolve(__dirname, '../../firebase.json'), 'utf8'));
    expect(firebase).toMatchObject({ firestore: { indexes: 'firestore.indexes.json' } });
  });
});

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

// [ENVIRONMENT-BLOCKED, written but not executed in this sandbox -- same JDK-21 blocker as the
// adUnlocks suite above, task 1.17] Spec: "Repeating Time-Limited Ad Unlock" -- owner-only
// read/create/UPDATE (unlike adUnlocks, which is create-only), closed field set, doc-id/field
// agreement, expiresAtMillis strictly greater than grantedAtMillis, and no delete, ever
// (design.md D16). Requires the Firestore emulator.
describe('firestore.rules: users/{uid}/timedUnlocks/{contentKey}', () => {
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
    expiresAtMillis: 1_755_302_400_000,
  };
  const docId = 'affirmationGroup_fuerza_de_voluntad';

  it('1. owner creates a well-shaped grant -> succeeds', async () => {
    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertSucceeds(
      setDoc(doc(ownerDb, `users/uid-owner/timedUnlocks/${docId}`), wellShapedGrant),
    );
  });

  it('2. owner UPDATING an existing grant (re-earning after expiry) -> succeeds, unlike adUnlocks', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), `users/uid-owner/timedUnlocks/${docId}`), wellShapedGrant);
    });

    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertSucceeds(
      setDoc(doc(ownerDb, `users/uid-owner/timedUnlocks/${docId}`), {
        ...wellShapedGrant,
        grantedAtMillis: 9_000_000_000_000,
        expiresAtMillis: 9_086_400_000_000,
      }),
    );
  });

  it('3. doc id disagreeing with contentType/contentId -> fails', async () => {
    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertFails(
      setDoc(doc(ownerDb, 'users/uid-owner/timedUnlocks/meditation_calma'), wellShapedGrant),
    );
  });

  it('4. unknown extra field -> fails', async () => {
    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertFails(
      setDoc(doc(ownerDb, `users/uid-owner/timedUnlocks/${docId}`), {
        ...wellShapedGrant,
        extra: 'nope',
      }),
    );
  });

  it('5. missing expiresAtMillis -> fails (non-null here, unlike adUnlocks)', async () => {
    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    const { expiresAtMillis: _omit, ...withoutExpiry } = wellShapedGrant;
    await assertFails(
      setDoc(doc(ownerDb, `users/uid-owner/timedUnlocks/${docId}`), withoutExpiry),
    );
  });

  it('6. expiresAtMillis <= grantedAtMillis -> fails', async () => {
    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertFails(
      setDoc(doc(ownerDb, `users/uid-owner/timedUnlocks/${docId}`), {
        ...wellShapedGrant,
        expiresAtMillis: wellShapedGrant.grantedAtMillis,
      }),
    );
  });

  it('7. owner deleting an existing grant -> fails', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), `users/uid-owner/timedUnlocks/${docId}`), wellShapedGrant);
    });

    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertFails(deleteDoc(doc(ownerDb, `users/uid-owner/timedUnlocks/${docId}`)));
  });

  it('8. a different uid create/read under this uid -> fails', async () => {
    const otherDb = testEnv.authenticatedContext('uid-other').firestore();
    await assertFails(
      setDoc(doc(otherDb, `users/uid-owner/timedUnlocks/${docId}`), wellShapedGrant),
    );

    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), `users/uid-owner/timedUnlocks/${docId}`), wellShapedGrant);
    });
    await assertFails(getDoc(doc(otherDb, `users/uid-owner/timedUnlocks/${docId}`)));
  });

  it('9. unauthenticated create/read -> fails', async () => {
    const anonDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(
      setDoc(doc(anonDb, `users/uid-owner/timedUnlocks/${docId}`), wellShapedGrant),
    );

    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), `users/uid-owner/timedUnlocks/${docId}`), wellShapedGrant);
    });
    await assertFails(getDoc(doc(anonDb, `users/uid-owner/timedUnlocks/${docId}`)));
  });
});

// [ENVIRONMENT-BLOCKED, written but not executed in this sandbox -- same missing-`firebase`-CLI
// blocker as the suites above] Spec: Notifications V2 server-owned copy catalog (design §1) --
// `notificationCopy/{variantKey}` denies BOTH read and write to every client, always (unlike the
// `describe.each` block below, which is owner-read/deny-write on per-uid subcollections):
// `notificationCopy` is a top-level collection with no owner at all, and the client never reads it
// directly (it renders server-side only, inside `sendNotification`) -- see `firestore.rules`'s
// `match /notificationCopy/{variantKey}` block.
describe('firestore.rules: notificationCopy/{variantKey}', () => {
  let testEnv: RulesTestEnvironment;
  const variantId = 'affirmation_a';
  const seededVariant = { key: variantId, family: 'reminder', enabled: true };

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

  it('denies an authenticated client write, even from an otherwise-plausible owner-like caller', async () => {
    const callerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertFails(setDoc(doc(callerDb, `notificationCopy/${variantId}`), seededVariant));
  });

  it('denies an authenticated client read', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), `notificationCopy/${variantId}`), seededVariant);
    });

    const callerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertFails(getDoc(doc(callerDb, `notificationCopy/${variantId}`)));
  });

  it('denies an unauthenticated client read and write', async () => {
    const anonDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(anonDb, `notificationCopy/${variantId}`)));
    await assertFails(setDoc(doc(anonDb, `notificationCopy/${variantId}`), seededVariant));
  });
});

// Regression guard (task 1.17): the existing adUnlocks suite above must still pass byte-for-byte
// -- this new timedUnlocks block must not weaken users/{uid}/adUnlocks' update/delete-denied
// guarantee. No new assertions needed here: the `firestore.rules: users/{uid}/adUnlocks/{contentKey}`
// describe block above is untouched and re-runs unchanged.

// [ENVIRONMENT-BLOCKED, written but not executed in this sandbox -- the `firebase` CLI is not
// installed here, so `npm run test:rules` cannot start the emulator] Spec: "Shared Catalog
// Storage Path" -- world-readable, client-write-denied on all 5 shared catalog collections
// (design D1/D2/D12/task 3.13).
describe('firestore.rules: shared catalog collections', () => {
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

  const collections = [
    'catalogAffirmations',
    'catalogUniverses',
    'catalogThemes',
    'catalogCollections',
  ];

  for (const collection of collections) {
    it(`allows an unauthenticated read of ${collection}`, async () => {
      const anonDb = testEnv.unauthenticatedContext().firestore();
      await assertSucceeds(getDoc(doc(anonDb, `${collection}/some-id`)));
    });

    it(`denies any client write to ${collection}, even by an authenticated user`, async () => {
      const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
      await assertFails(setDoc(doc(ownerDb, `${collection}/some-id`), { text: 'hacked' }));
    });
  }

  it('allows an unauthenticated read of the catalogMeta version marker', async () => {
    const anonDb = testEnv.unauthenticatedContext().firestore();
    await assertSucceeds(getDoc(doc(anonDb, 'catalogMeta/version')));
  });

  it('denies a client write to the catalogMeta version marker', async () => {
    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertFails(setDoc(doc(ownerDb, 'catalogMeta/version'), { version: '9.9.9' }));
  });
});

// [ENVIRONMENT-BLOCKED, written but not executed in this sandbox -- same missing-`firebase`-CLI
// blocker as the suite above] Spec: "Per-User Override Sync Surface" -- owner-only read/write, cross-uid
// and unauthenticated access denied (design D9/task 3.13).
describe('firestore.rules: users/{uid}/catalogOverrides/{catalogAffirmationId}', () => {
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

  const docId = 'cat_self_worth.feeling_enough.intrinsic_worth.001';
  const overridePayload = { overrides: { name: 'Alex' } };

  it('owner can read and write their own override doc', async () => {
    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertSucceeds(
      setDoc(doc(ownerDb, `users/uid-owner/catalogOverrides/${docId}`), overridePayload),
    );
    await assertSucceeds(getDoc(doc(ownerDb, `users/uid-owner/catalogOverrides/${docId}`)));
  });

  it('a different uid cannot read or write another owner\'s override doc', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), `users/uid-owner/catalogOverrides/${docId}`), overridePayload);
    });

    const otherDb = testEnv.authenticatedContext('uid-other').firestore();
    await assertFails(getDoc(doc(otherDb, `users/uid-owner/catalogOverrides/${docId}`)));
    await assertFails(setDoc(doc(otherDb, `users/uid-owner/catalogOverrides/${docId}`), overridePayload));
  });

  it('an unauthenticated client cannot read or write an override doc', async () => {
    const anonDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(anonDb, `users/uid-owner/catalogOverrides/${docId}`)));
    await assertFails(setDoc(doc(anonDb, `users/uid-owner/catalogOverrides/${docId}`), overridePayload));
  });
});

// [ENVIRONMENT-BLOCKED, written but not executed in this sandbox -- same missing-`firebase`-CLI
// blocker as the suites above] Spec: Notifications V2 server-only state (design §2/§3/D9) --
// `notificationState`, `notificationDeliveries`, and `compassAnswers` all share the same posture:
// owner can read their own doc, write is unconditionally denied (Admin SDK bypasses rules).
describe.each([
  ['notificationState', 'current'],
  ['notificationDeliveries', '19900'],
  ['compassAnswers', '19900'],
])('firestore.rules: users/{uid}/%s/{doc}', (collection, docId) => {
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
      setDoc(doc(ownerDb, `users/uid-owner/${collection}/${docId}`), { seeded: true }),
    );
  });

  it('allows the owner to read their own doc', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), `users/uid-owner/${collection}/${docId}`), { seeded: true });
    });

    const ownerDb = testEnv.authenticatedContext('uid-owner').firestore();
    await assertSucceeds(getDoc(doc(ownerDb, `users/uid-owner/${collection}/${docId}`)));
  });

  it('denies a different uid from reading or writing this doc', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), `users/uid-owner/${collection}/${docId}`), { seeded: true });
    });

    const otherDb = testEnv.authenticatedContext('uid-other').firestore();
    await assertFails(getDoc(doc(otherDb, `users/uid-owner/${collection}/${docId}`)));
    await assertFails(setDoc(doc(otherDb, `users/uid-owner/${collection}/${docId}`), { seeded: true }));
  });

  it('denies an unauthenticated client from reading or writing this doc', async () => {
    const anonDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(anonDb, `users/uid-owner/${collection}/${docId}`)));
    await assertFails(setDoc(doc(anonDb, `users/uid-owner/${collection}/${docId}`), { seeded: true }));
  });
});
