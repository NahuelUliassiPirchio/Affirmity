import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

import { findIllegalBrackets } from '../../tools/catalog/bracketGate.mjs';

/**
 * RED-first for design D1 (the bracket gate). Driven by the shared cross-language fixture file
 * `tools/catalog/bracket-fixtures.json` so the JS and Kotlin twins (`CatalogTextSanitizerTest.kt`)
 * assert the exact same edge cases.
 */

const __dirname = dirname(fileURLToPath(import.meta.url));
const fixturesPath = join(__dirname, '..', '..', 'tools', 'catalog', 'bracket-fixtures.json');

interface BracketFixture {
  text: string;
  illegalOffsets: number[];
}

const fixtures: BracketFixture[] = JSON.parse(readFileSync(fixturesPath, 'utf8'));

describe('findIllegalBrackets', () => {
  for (const fixture of fixtures) {
    it(`"${fixture.text}" -> ${JSON.stringify(fixture.illegalOffsets)}`, () => {
      expect(findIllegalBrackets(fixture.text)).toEqual(fixture.illegalOffsets);
    });
  }

  it('returns empty for clean text with no brackets at all', () => {
    expect(findIllegalBrackets('Mi valor no depende de cuánto haga hoy.')).toEqual([]);
  });
});
