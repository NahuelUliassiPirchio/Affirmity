import { readFileSync } from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

import { selectVariant } from '../src/copyCatalog';
import {
  buildCopyWritePlan,
  chunkCopyWrites,
  seedCopyCatalog,
  type CopyCatalogFile,
  type CopyCommitter,
  type CopyFirestoreWrite,
} from '../tools/seedCopyCatalog';

// Keep this detector aligned with NeutralSpanishResourcesTest on Android.
const VOSEO_WORDS = /(?<![\p{L}])(anclate|asentate|dejate|abrí|acomodate|activá|agregá|alterná|ampliá|anotá|armá|boludo|cambiá|caminá|cerrá|che|completá|considerá|contemplá|continuá|contá|cultivá|dale|decidí|dejalo|dejá|desbloqueá|descansá|deslizá|detenete|elegí|empezá|encontrá|entrá|escribí|esperá|establecé|exhalá|expandí|explorá|extendé|guardalo|guardá|hablá|hacelo|hacé|imaginate|imaginá|inhalá|iniciá|intentá|laburo|leé|llevá|llevátela|mantené|meditá|miralo|mirá|nombrá|notá|observá|ofrecete|orá|parpadeá|pedí|pensá|permanecé|ponete|practicá|preparate|probá|quedate|reconocé|recordá|recorré|recuperá|reflexioná|registrá|regulá|relajá|repetí|respirá|respondete|respondé|seguí|sentate|sentí|sincronizá|soltá|sos|sostené|tensá|tocá|tomate|usalo|usá|visualizá|volvé|vos|zumbá)(?![\p{L}])/iu;
const VOSEO_ENDING = /(?<![\p{L}])\p{L}+(ás|és|ís)(?![\p{L}])/giu;
const NEUTRAL_ENDINGS = new Set(['demás', 'estás', 'más', 'además', 'después', 'país', 'atrás', 'detrás', 'quizás', 'jamás', 'través', 'interés', 'inglés', 'francés', 'hablarás', 'tendrás', 'podrás', 'serás', 'estarás', 'harás', 'dirás', 'vendrás', 'saldrás', 'querrás', 'sabrás', 'pondrás', 'valdrás', 'habrás', 'irás']);
// Reviewed neutral words include future forms; do not exempt every -rás (mirás is voseo).
const usesVoseo = (text: string): boolean =>
  VOSEO_WORDS.test(text) ||
  (text.match(VOSEO_ENDING) ?? []).some((word) => {
    const lower = word.toLowerCase();
    return !NEUTRAL_ENDINGS.has(lower);
  });

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

  it('every Spanish notification variant uses neutral Spanish', () => {
    for (const variant of catalog.variants) {
      const { title, body } = variant.locales.es;
      expect(usesVoseo(`${title} ${body}`), `${variant.key} es uses voseo/regional forms`).toBe(false);
    }
  });

  it('the voseo detector flags common Argentine forms and accepts neutral Spanish', () => {
    for (const sample of ['Llevás 5 días', 'Vos sabes', 'Todavía llegás', 'Ya venís', 'Seguís sumando', 'Tenés tiempo', 'Sos genial', 'Hacelo hoy', 'Usalo hoy', 'Mantené la racha', 'Leé una hoy', 'Sentate un rato', 'Dale que va', 'Creés que puedes', 'Acomodate', 'Respondete', 'Mirá aquí', 'Inhalá', 'Quedate', 'PreparATE', 'Imaginate', 'GUARDALO', 'Mirás', 'Estirás', 'Anclate', 'Asentate', 'Dejate']) {
      expect(usesVoseo(sample), sample).toBe(true);
    }
    for (const sample of ['Llevas 5 días meditando', 'Lee una hoy', 'Siéntate un momento', 'Sigues sumando', 'Aún estás a tiempo', 'Tienes tiempo', 'Después de las prácticas', 'Hablarás después', 'Tendrás tiempo', 'Darle espacio', 'Date un momento', 'Más interés en el país', 'Creo que puedes', 'Las demás prácticas']) {
      expect(usesVoseo(sample), sample).toBe(false);
    }
  });

  describe('overall streak variants', () => {
    const BREAKDOWNS = {
      streak_both: ['streakCount', 'meditationDays', 'affirmationsDays'],
      streak_meditation_only: ['streakCount', 'meditationDays'],
      streak_affirmations_only: ['streakCount', 'affirmationsDays'],
    } as const;
    const BANDS = ['streak_1_3', 'streak_4_13', 'streak_14plus'];
    const MAX_TITLE_CHARS = 80;
    const MAX_BODY_CHARS = 110;
    const OVERALL: Record<'es' | 'en', RegExp> = { es: /general/i, en: /overall/i };
    const FALLBACK_TAG = 'streak_generic';
    const activeStreak = () => catalog.variants.filter((v) => v.family === 'streak' && v.enabled);
    const isFallback = (v: { context: string[] }) => v.context.includes(FALLBACK_TAG);
    // Breakdown variants: the primary attempt's pool (band + breakdown tags).
    const active = () => activeStreak().filter((v) => !isFallback(v));
    const fallback = () => activeStreak().filter(isFallback);

    it('has at least one enabled variant for every band x breakdown combination', () => {
      for (const band of BANDS) {
        for (const breakdown of Object.keys(BREAKDOWNS)) {
          const matching = active().filter((v) => v.context.includes(band) && v.context.includes(breakdown));
          expect(matching.length, `${band} ${breakdown}`).toBeGreaterThanOrEqual(1);
        }
      }
    });

    it('every enabled streak variant has exactly a band + breakdown tag and matching placeholders', () => {
      for (const variant of active()) {
        const [band, breakdown] = [
          variant.context.find((t) => BANDS.includes(t)),
          variant.context.find((t) => t in BREAKDOWNS),
        ];
        expect(band, `${variant.key} band`).toBeDefined();
        expect(breakdown, `${variant.key} breakdown`).toBeDefined();
        expect(variant.context).toHaveLength(2);
        expect(variant.placeholders).toEqual(BREAKDOWNS[breakdown as keyof typeof BREAKDOWNS]);
      }
    });

    it('every title says OVERALL, shows the general count, and the body explains the breakdown', () => {
      for (const variant of active()) {
        for (const locale of ['es', 'en'] as const) {
          const { title, body } = variant.locales[locale];
          expect(title, `${variant.key} ${locale} title has {streakCount}`).toContain('{streakCount}');
          expect(title, `${variant.key} ${locale} title says overall`).toMatch(OVERALL[locale]);
          for (const placeholder of variant.placeholders.filter((p) => p !== 'streakCount')) {
            expect(body, `${variant.key} ${locale} body has {${placeholder}}`).toContain(`{${placeholder}}`);
          }
          const render = (text: string) =>
            text.replaceAll('{streakCount}', '14').replaceAll(/\{\w+Days\}/g, '14 días');
          expect(render(title).length, `${variant.key} ${locale} title length`).toBeLessThanOrEqual(MAX_TITLE_CHARS);
          expect(render(body).length, `${variant.key} ${locale} body length`).toBeLessThanOrEqual(MAX_BODY_CHARS);
        }
      }
    });

    it('has an enabled fallback pool tagged only streak_generic, needing only {streakCount}', () => {
      expect(fallback().length).toBeGreaterThanOrEqual(2);
      for (const variant of fallback()) {
        expect(variant.context, variant.key).toEqual([FALLBACK_TAG]);
        expect(variant.placeholders, variant.key).toEqual(['streakCount']);
        for (const locale of ['es', 'en'] as const) {
          const { title, body } = variant.locales[locale];
          expect(title, `${variant.key} ${locale} title has {streakCount}`).toContain('{streakCount}');
          expect(title, `${variant.key} ${locale} title says overall`).toMatch(OVERALL[locale]);
          expect(`${title} ${body}`, `${variant.key} ${locale} no breakdown placeholders`).not.toMatch(/\{\w*Days\}/);
          expect(title.replaceAll('{streakCount}', '14').length, `${variant.key} ${locale} title length`).toBeLessThanOrEqual(MAX_TITLE_CHARS);
          expect(body.length, `${variant.key} ${locale} body length`).toBeLessThanOrEqual(MAX_BODY_CHARS);
        }
      }
    });

    it('fallback and primary pools never cross-match, and only enabled streak_overall/generic keys are selectable', () => {
      const pool = catalog.variants.filter((v) => v.enabled);
      for (const band of BANDS) {
        for (const breakdown of Object.keys(BREAKDOWNS)) {
          const picked = selectVariant(pool, 'streak', [band, breakdown], [], () => 0);
          expect(picked?.key, `${band} ${breakdown}`).toMatch(/^streak_overall_/);
          for (let i = 0; i < 20; i++) {
            const any = selectVariant(pool, 'streak', [band, breakdown], [], () => i / 20);
            expect(any && isFallback(any), `${band} ${breakdown} primary must not match fallback`).toBe(false);
            expect(any?.key.startsWith('streak_risk_') || any?.key.startsWith('streak_activity_')).toBe(false);
          }
        }
      }
      for (let i = 0; i < 20; i++) {
        const any = selectVariant(pool, 'streak', [FALLBACK_TAG], [], () => i / 20);
        expect(any && isFallback(any), 'fallback context only matches fallback variants').toBe(true);
      }
    });

    it('keeps the superseded streak_risk_* / streak_activity_* keys but disabled, so re-seeding retires them', () => {
      const legacy = catalog.variants.filter(
        (v) => v.key.startsWith('streak_risk_') || v.key.startsWith('streak_activity_'),
      );
      expect(legacy.length).toBeGreaterThan(0);
      for (const variant of legacy) expect(variant.enabled, variant.key).toBe(false);
    });
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
