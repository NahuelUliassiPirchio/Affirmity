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
 * Defaults to /Users/pirxhion/Downloads/affirmations-catalog.v2.json (the measured source, design.md).
 */
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { findIllegalBrackets } from "./bracketGate.mjs";
import { buildCatalog } from "./buildCatalog.mjs";

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, "..", "..");

const sourcePath = process.argv[2] ?? "/Users/pirxhion/Downloads/affirmations-catalog.v2.json";
const ASSET_OUT = join(REPO_ROOT, "app/src/main/assets/catalog.v1.json");
const TAXONOMY_OUT = join(
  REPO_ROOT,
  "app/src/main/java/com/pirxhio/affirmity/ui/groups/CatalogTaxonomy.kt",
);

function fail(message) {
  console.error(`[generate-catalog] FAILED: ${message}`);
  process.exit(1);
}

function runBracketGate({ universes, themes, collections, affirmations }) {
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

function main() {
  const raw = readFileSync(sourcePath, "utf8");
  const source = JSON.parse(raw);

  runBracketGate(source);

  let result;
  try {
    result = buildCatalog(source);
  } catch (error) {
    fail(error.message);
    return;
  }

  const { asset, taxonomyKt } = result;

  mkdirSync(dirname(ASSET_OUT), { recursive: true });
  writeFileSync(ASSET_OUT, JSON.stringify(asset), "utf8");

  mkdirSync(dirname(TAXONOMY_OUT), { recursive: true });
  writeFileSync(TAXONOMY_OUT, taxonomyKt, "utf8");

  const collectionsCount = source.collections.length;
  const gatedCount = (taxonomyKt.match(/setOf\(([^)]*)\)/)?.[1].split(",").filter((s) => s.trim()).length) ?? 0;

  console.log(
    `[generate-catalog] OK: ${asset.affirmations.length} affirmations, ${source.universes.length} universes, ` +
      `${collectionsCount} collections. CATALOG_GATED_GROUP_IDS size=${gatedCount}/${source.universes.length}.`,
  );
  console.log(`[generate-catalog] wrote ${ASSET_OUT}`);
  console.log(`[generate-catalog] wrote ${TAXONOMY_OUT}`);
}

main();
