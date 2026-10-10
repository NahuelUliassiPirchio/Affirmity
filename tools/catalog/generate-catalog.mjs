#!/usr/bin/env node
/**
 * I/O shell (design D5): reads the v2 source JSON, runs the bracket gate (D1) over every authored
 * text field, then delegates the pure `source -> {asset, taxonomyKt}` transform to
 * `buildCatalog.mjs`, and writes both outputs to disk.
 *
 * Source JSON -> `app/src/main/assets/catalog.v1.json` (bundled seed) +
 * `app/src/main/java/com/pirxhio/affirmity/ui/groups/CatalogTaxonomy.kt` (compiled taxonomy).
 *
 * Bracket gate (design D1): FAILS the build on any residual `[`/`]` left over after masking legal,
 * non-blank `[token]` sequences in `title`/`subtitle` and all taxonomy text (universe/theme/
 * collection title+description+coreNeed). Also fails on: duplicate affirmation id, an affirmation
 * referencing an unknown collectionId, a themeId/universeId mismatch against the resolved
 * collection, a collection declaring `tier: "free"` with a non-null `rewardedUnlockHours`, and a
 * non-positive `rewardedUnlockHours` (the latter two enforced inside `buildCatalog.mjs`).
 *
 * Usage: node tools/catalog/generate-catalog.mjs [path/to/source.json]
 * With no args, builds from the in-repo sources: `tools/catalog/source/affirmations.v5.csv`
 * (content) + `tools/catalog/source/taxonomy.json` (universes/themes/collections), joined by
 * `csvSource.mjs`. An explicit JSON path (full source shape) is still accepted for back-compat.
 */
import { existsSync, readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { findIllegalBrackets } from "./bracketGate.mjs";
import { buildCatalog } from "./buildCatalog.mjs";
import { csvToSource } from "./csvSource.mjs";
import { assertLocaleParity } from "./localeParity.mjs";

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, "..", "..");

const sourcePath = process.argv[2];
const CSV_SOURCE = join(__dirname, "source/affirmations.v5.csv");
const TAXONOMY_SOURCE = join(__dirname, "source/taxonomy.json");
const ASSET_OUT = join(REPO_ROOT, "app/src/main/assets/catalog.v1.json");
/** Supported locales (REQ-PAR-2). `es` is the base: parity and the compiled taxonomy come from it. */
export const LOCALES = [
  { locale: "es", csv: join(__dirname, "source/affirmations.v5.csv"), asset: ASSET_OUT },
  {
    locale: "en",
    csv: join(__dirname, "source/affirmations.v5.en.csv"),
    asset: join(REPO_ROOT, "app/src/main/assets/catalog.v1.en.json"),
  },
];
const TAXONOMY_OUT = join(
  REPO_ROOT,
  "app/src/main/java/com/pirxhio/affirmity/ui/groups/CatalogTaxonomy.kt",
);

/** Thrown (not `console.error` + `process.exit`) so `runBracketGate`/`main` stay callable -- and
 * therefore testable -- from a plain function call; `main()` below is the only place this process
 * actually exits. */
function fail(message) {
  throw new Error(`[generate-catalog] FAILED: ${message}`);
}

/** Fix 6: title fields (`universe.title`, `theme.title`, `collection.title`,
 * `affirmation.title`) are REQUIRED, non-empty strings -- unlike `description`/`subtitle`, which
 * can legitimately be absent/blank. The bracket-gate loop below silently `continue`s past any
 * non-string value (which is correct for those optional fields), so a non-string `title` must be
 * rejected HERE, before that loop, or it skips validation entirely and reaches
 * `buildCatalog`/the bundled asset with a bad shape -- surfacing only later, at runtime, on a
 * user's device (`CatalogAssetParser.kt`'s non-null `title` read, per this fix's task description).
 */
function requireStringTitles(field, entries) {
  for (const entry of entries) {
    const { id, title } = entry;
    if (typeof title !== "string" || title.trim().length === 0) {
      fail(`${field} (id=${id ?? "<unknown>"}): expected non-empty string "title", got ${typeof title}`);
    }
  }
}

export function runBracketGate({ universes, themes, collections, affirmations }) {
  requireStringTitles("universe.title", universes);
  requireStringTitles("theme.title", themes);
  requireStringTitles("collection.title", collections);
  requireStringTitles("affirmation.title", affirmations);

  const textFields = [
    ...universes.flatMap((u) => [
      ["universe.title", u.id, u.title],
      ["universe.description", u.id, u.description],
      ["universe.coreNeed", u.id, u.coreNeed],
    ]),
    ...themes.flatMap((t) => [
      ["theme.title", t.id, t.title],
      ["theme.description", t.id, t.description],
    ]),
    ...collections.flatMap((c) => [
      ["collection.title", c.id, c.title],
      ["collection.description", c.id, c.description],
    ]),
    ...affirmations.flatMap((a) => [
      ["affirmation.title", a.id, a.title],
      ["affirmation.subtitle", a.id, a.subtitle],
    ]),
  ];
  for (const [field, id, value] of textFields) {
    if (typeof value !== "string") continue;
    const offsets = findIllegalBrackets(value);
    if (offsets.length > 0) {
      fail(`illegal bracket found in ${field} (id=${id}) at offsets ${offsets.join(",")}`);
    }
  }
}

/** Pure source -> exact file contents the generator writes (no I/O). Runs the bracket gate first. */
export function generateCatalogFiles(source) {
  runBracketGate(source);
  const { asset, taxonomyKt } = buildCatalog(source);
  return { asset, assetJson: JSON.stringify(asset), taxonomyKt };
}

/**
 * Pure multi-locale transform (no I/O). `csvByLocale` MUST contain `es` (the base). Parity of every
 * other locale against es runs BEFORE anything is returned, so a caller that writes only the result
 * never writes a partial set. The taxonomy Kotlin comes from es and must be identical for the rest.
 * @returns {{assets: Record<string, {asset: object, assetJson: string}>, taxonomyKt: string, warnings: string[]}}
 */
export function generateAllLocales(csvByLocale, taxonomy) {
  if (typeof csvByLocale.es !== "string") fail("generateAllLocales requires the base locale 'es'");
  const sources = Object.fromEntries(
    Object.entries(csvByLocale).map(([locale, csv]) => [locale, csvToSource(csv, taxonomy)]),
  );

  const warnings = [];
  for (const [locale, source] of Object.entries(sources)) {
    if (locale === "es") continue;
    const { warnings: localeWarnings } = assertLocaleParity(sources.es, source);
    warnings.push(...localeWarnings.map((w) => `[${locale}] ${w}`));
  }

  const esFiles = generateCatalogFiles(sources.es);
  const taxonomyKt = esFiles.taxonomyKt;
  const assets = { es: { asset: esFiles.asset, assetJson: esFiles.assetJson } };
  for (const [locale, source] of Object.entries(sources)) {
    if (locale === "es") continue;
    const files = generateCatalogFiles(source);
    if (files.taxonomyKt !== taxonomyKt) fail(`taxonomy Kotlin for locale '${locale}' differs from the es taxonomy`);
    assets[locale] = { asset: files.asset, assetJson: files.assetJson };
  }
  return { assets, taxonomyKt, warnings };
}

function main() {
  let result;
  let source;
  const pending = [];
  try {
    if (sourcePath) {
      // Back-compat: explicit full-shape JSON builds the es asset only.
      source = JSON.parse(readFileSync(sourcePath, "utf8"));
      const files = generateCatalogFiles(source);
      result = { assets: { es: { asset: files.asset, assetJson: files.assetJson } }, taxonomyKt: files.taxonomyKt, warnings: [] };
    } else {
      const taxonomy = JSON.parse(readFileSync(TAXONOMY_SOURCE, "utf8"));
      const csvByLocale = {};
      for (const { locale, csv } of LOCALES) {
        if (existsSync(csv)) csvByLocale[locale] = readFileSync(csv, "utf8");
        else if (locale === "es") fail(`missing base CSV ${csv}`);
        else pending.push(locale);
      }
      source = csvToSource(csvByLocale.es, taxonomy);
      result = generateAllLocales(csvByLocale, taxonomy);
    }
  } catch (error) {
    console.error(error.message.startsWith("[generate-catalog] FAILED:") ? error.message : `[generate-catalog] FAILED: ${error.message}`);
    process.exit(1);
    return;
  }

  const { assets, taxonomyKt, warnings } = result;

  // Parity already passed for every locale: only now touch the disk.
  for (const { locale, asset: assetOut } of LOCALES) {
    if (!assets[locale]) continue;
    mkdirSync(dirname(assetOut), { recursive: true });
    writeFileSync(assetOut, assets[locale].assetJson, "utf8");
    console.log(`[generate-catalog] wrote ${assetOut}`);
  }

  mkdirSync(dirname(TAXONOMY_OUT), { recursive: true });
  writeFileSync(TAXONOMY_OUT, taxonomyKt, "utf8");

  const collectionsCount = source.collections.length;
  const gatedCount = (taxonomyKt.match(/setOf\(([^)]*)\)/)?.[1].split(",").filter((s) => s.trim()).length) ?? 0;

  console.log(
    `[generate-catalog] OK: ${assets.es.asset.affirmations.length} affirmations, ${source.universes.length} universes, ` +
      `${collectionsCount} collections. CATALOG_GATED_GROUP_IDS size=${gatedCount}/${source.universes.length}.`,
  );
  console.log(`[generate-catalog] wrote ${TAXONOMY_OUT}`);
  for (const w of warnings) console.warn(`[generate-catalog] WARNING: ${w}`);
  for (const locale of pending) console.warn(`[generate-catalog] skipped locale '${locale}': source CSV not present yet`);
}

// ESM equivalent of CommonJS's `require.main === module` -- only run `main()` when this file is
// executed directly (`node tools/catalog/generate-catalog.mjs ...`), never when it's `import`ed
// (e.g. by `runBracketGate`'s vitest suite). Before this guard, importing this module for testing
// ran the real publish pipeline as a side effect -- including overwriting the bundled
// `catalog.v1.json`/`CatalogTaxonomy.kt` from whatever the default source path resolved to.
if (import.meta.url === `file://${process.argv[1]}`) {
  main();
}
