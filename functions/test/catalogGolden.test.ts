import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

import { parseCsv } from '../../tools/catalog/csvSource.mjs';
import { generateAllLocales } from '../../tools/catalog/generate-catalog.mjs';

const REPO = join(__dirname, '..', '..');
const read = (p: string) => readFileSync(join(REPO, p), 'utf8');

const ES_CSV = 'tools/catalog/source/affirmations.v5.csv';
const EN_CSV = 'tools/catalog/source/affirmations.v5.en.csv';
const ASSETS = { es: 'app/src/main/assets/catalog.v1.json', en: 'app/src/main/assets/catalog.v1.en.json' } as const;
const taxonomy = () => JSON.parse(read('tools/catalog/source/taxonomy.json'));

const NEW_ROW_ID = 'cat_work_money_growth.career_growth.allow_bigger_ambition.013';
const TOKEN_ROW_IDS = [
  'cat_connection_belonging.friendships.care_for_valuable_friendships.v4.03',
  'cat_motivation_discipline_responsibility.procrastination.make_it_small.v4.02',
  'cat_motivation_discipline_responsibility.discipline_consistency.build_sustainable_habits.v4.06',
  'cat_motivation_discipline_responsibility.finish_what_i_start.finisher_identity.v4.08',
  'cat_work_money_growth.money_relationship.receive_without_guilt.v4.02',
];

function rowsById(csvPath: string) {
  const [header, ...body] = parseCsv(read(csvPath));
  const col = (n: string) => header.indexOf(n);
  return new Map(body.map((r) => [r[col('id')], { title: r[col('title')], subtitle: r[col('subtitle')] }]));
}

describe('catalog golden', () => {
  const out = generateAllLocales({ es: read(ES_CSV), en: read(EN_CSV) }, taxonomy());

  it.each(['es', 'en'] as const)('regenerates the committed %s asset byte-for-byte', (locale) => {
    expect(out.assets[locale].assetJson).toBe(read(ASSETS[locale]));
  });

  it('regenerates the committed CatalogTaxonomy.kt byte-for-byte', () => {
    expect(out.taxonomyKt).toBe(read('app/src/main/java/com/pirxhio/affirmity/ui/groups/CatalogTaxonomy.kt'));
  });

  it('fails the golden when the committed en asset is stale while es still passes', () => {
    const stale = read(ASSETS.en).replace('"title":"', '"title":"HAND EDIT ');
    expect(out.assets.es.assetJson).toBe(read(ASSETS.es));
    expect(out.assets.en.assetJson).not.toBe(stale);
  });
});

describe('en source CSV (REQ-PAR-1)', () => {
  const es = rowsById(ES_CSV);
  const en = rowsById(EN_CSV);
  const hasToken = (t: string) => /\[[^[\]]+]/.test(t);

  it('contains the new 5.1.0 row in both CSVs', () => {
    expect(es.has(NEW_ROW_ID)).toBe(true);
    expect(en.has(NEW_ROW_ID)).toBe(true);
  });

  it.each([...TOKEN_ROW_IDS, NEW_ROW_ID])('row %s carries a [token] in both CSVs', (id) => {
    const e = es.get(id)!;
    const n = en.get(id)!;
    expect(hasToken(e.title) || hasToken(e.subtitle)).toBe(true);
    expect(hasToken(n.title) || hasToken(n.subtitle)).toBe(true);
  });
});
