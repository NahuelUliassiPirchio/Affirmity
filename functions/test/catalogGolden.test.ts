import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

import { csvToSource } from '../../tools/catalog/csvSource.mjs';
import { generateCatalogFiles } from '../../tools/catalog/generate-catalog.mjs';

const REPO = join(__dirname, '..', '..');
const read = (p: string) => readFileSync(join(REPO, p), 'utf8');

describe('catalog golden', () => {
  const source = csvToSource(
    read('tools/catalog/source/affirmations.v5.csv'),
    JSON.parse(read('tools/catalog/source/taxonomy.json')),
  );
  const out = generateCatalogFiles(source);

  it('regenerates the committed catalog.v1.json byte-for-byte', () => {
    expect(out.assetJson).toBe(read('app/src/main/assets/catalog.v1.json'));
  });
  it('regenerates the committed CatalogTaxonomy.kt byte-for-byte', () => {
    expect(out.taxonomyKt).toBe(read('app/src/main/java/com/pirxhio/affirmity/ui/groups/CatalogTaxonomy.kt'));
  });
});
