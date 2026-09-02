import { readFileSync } from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

import {
  buildCopyWritePlan,
  chunkCopyWrites,
  seedCopyCatalog,
  type CopyCatalogFile,
  type CopyCommitter,
  type CopyFirestoreWrite,
} from '../tools/seedCopyCatalog';

/**
 * Mirrors `seedCatalog.test.ts`'s style: a fake `CopyCommitter`, no Admin SDK app, no emulator.
 * Also asserts the committed catalog's data-quality invariants (design §1's seed test):
 * ES/EN parity, declared placeholders vs. actual `{...}` occurrences, unique keys.
 */

function catalogFile(overrides: Partial<CopyCatalogFile> = {}): CopyCatalogFile {
  return {
    version: '1.0.0',
    variants: [
      {
        key: 'affirmation_a',
        family: 'reminder',
        context: [],
        placeholders: [],
        enabled: true,
        order: 1,
        locales: {
          es: { title: 'Titulo ES', body: 'Cuerpo ES' },
          en: { title: 'Title EN', body: 'Body EN' },
        },
      },
    ],
    ...overrides,
  };
}

function manyVariants(count: number): CopyCatalogFile['variants'] {
  return Array.from({ length: count }, (_, i) => ({
    key: `v_${i}`,
    family: 'reminder' as const,
    context: [],
    placeholders: [],
    enabled: true,
    order: i,
    locales: {
      es: { title: `ES ${i}`, body: `Cuerpo ${i}` },
      en: { title: `EN ${i}`, body: `Body ${i}` },
    },
  }));
}

class RecordingCommitter implements CopyCommitter {
  readonly commits: CopyFirestoreWrite[][] = [];

  async commit(writes: CopyFirestoreWrite[]): Promise<void> {
    this.commits.push(writes);
  }
}

describe('buildCopyWritePlan / chunkCopyWrites', () => {
  it('writes each variant to `notificationCopy/{key}` with its full shape', () => {
    const writes = buildCopyWritePlan(catalogFile());
    expect(writes).toEqual([
      {
        path: 'notificationCopy/affirmation_a',
        data: {
          family: 'reminder',
          context: [],
          placeholders: [],
          enabled: true,
          order: 1,
          locales: {
            es: { title: 'Titulo ES', body: 'Cuerpo ES' },
            en: { title: 'Title EN', body: 'Body EN' },
          },
        },
      },
    ]);
  });

  it('chunks writes at 450 ops per batch', () => {
    const writes = buildCopyWritePlan(catalogFile({ variants: manyVariants(1000) }));
    const chunks = chunkCopyWrites(writes);
    expect(chunks).toHaveLength(3);
    expect(chunks[0]).toHaveLength(450);
    expect(chunks[1]).toHaveLength(450);
    expect(chunks[2]).toHaveLength(100);
  });
});

describe('seedCopyCatalog', () => {
  it('commits every variant, chunked, and is idempotent on re-run', async () => {
    const catalog = catalogFile({ variants: manyVariants(1000) });
    const first = new RecordingCommitter();
    const second = new RecordingCommitter();

    await seedCopyCatalog(catalog, first);
    await seedCopyCatalog(catalog, second);

    expect(first.commits.flat()).toHaveLength(1000);
    for (const commit of first.commits) {
      expect(commit.length).toBeLessThanOrEqual(450);
    }
    expect(second.commits).toEqual(first.commits);
  });
});

describe('notification-copy.v1.json data quality', () => {
  const catalog = JSON.parse(
    readFileSync(path.join(__dirname, '../tools/notification-copy.v1.json'), 'utf8'),
  ) as CopyCatalogFile;

  it('has at least one variant', () => {
    expect(catalog.variants.length).toBeGreaterThan(0);
  });

  it('has unique keys', () => {
    const keys = catalog.variants.map((v) => v.key);
    expect(new Set(keys).size).toBe(keys.length);
  });

  it('has ES/EN parity -- both locales present and non-empty for every variant', () => {
    for (const variant of catalog.variants) {
      expect(variant.locales.es.title.length).toBeGreaterThan(0);
      expect(variant.locales.es.body.length).toBeGreaterThan(0);
      expect(variant.locales.en.title.length).toBeGreaterThan(0);
      expect(variant.locales.en.body.length).toBeGreaterThan(0);
    }
  });

  it('declared placeholders are a subset of the actual `{...}` occurrences in both locales', () => {
    for (const variant of catalog.variants) {
      for (const locale of ['es', 'en'] as const) {
        const text = `${variant.locales[locale].title} ${variant.locales[locale].body}`;
        const actual = [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]);
        for (const declared of variant.placeholders) {
          expect(actual).toContain(declared);
        }
      }
    }
  });

  it('never leaves an undeclared `{...}` occurrence in either locale', () => {
    for (const variant of catalog.variants) {
      for (const locale of ['es', 'en'] as const) {
        const text = `${variant.locales[locale].title} ${variant.locales[locale].body}`;
        const actual = [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]);
        for (const found of actual) {
          expect(variant.placeholders).toContain(found);
        }
      }
    }
  });
});
