import { describe, expect, it } from 'vitest';

import { buildCompassAnswerDoc, wasCompassAnsweredToday } from '../src/compassAnswers';

// Design D9: `users/{uid}/compassAnswers/{localDay}` -- backs the send-time `compassAnsweredToday`
// check (design §3 suppression table) and records which question was answered.

describe('wasCompassAnsweredToday', () => {
  it('is false when no doc exists yet', () => {
    expect(wasCompassAnsweredToday(undefined, 100)).toBe(false);
    expect(wasCompassAnsweredToday(null, 100)).toBe(false);
  });

  it('is true when the doc records an answer for the requested local day', () => {
    const doc = buildCompassAnswerDoc(100, 'q_042', 1_700_000);
    expect(wasCompassAnsweredToday(doc, 100)).toBe(true);
  });

  it('is false when the doc belongs to a different (stale) local day', () => {
    const doc = buildCompassAnswerDoc(99, 'q_041', 1_600_000);
    expect(wasCompassAnsweredToday(doc, 100)).toBe(false);
  });
});

describe('buildCompassAnswerDoc', () => {
  it('captures the answered local day, the question id, and the answer timestamp', () => {
    expect(buildCompassAnswerDoc(100, 'q_042', 1_700_000)).toEqual({
      localDay: 100,
      questionId: 'q_042',
      answeredAtMillis: 1_700_000,
    });
  });
});
