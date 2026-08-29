#!/usr/bin/env node
/**
 * Source JSON -> `app/src/main/assets/catalog.v1.json` (bundled seed) +
 * `app/src/main/java/com/pirxhio/affirmity/ui/groups/CatalogTaxonomy.kt` (compiled taxonomy).
 *
 * Design D11 (bracket gate): FAILS the build on any literal `[`/`]` in a text/title/description/
 * coreNeed field. Also fails on: duplicate affirmation id, an affirmation referencing an unknown
 * collectionId, a collection declaring `tier: "free"` with a non-null `rewardedUnlockHours`, and a
 * non-positive `rewardedUnlockHours`.
 *
 * Usage: node tools/catalog/generate-catalog.mjs [path/to/source.json]
 * Defaults to /Users/pirxhion/Downloads/affirmations-catalog.v1.json (the measured source, design.md).
 */
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, "..", "..");

const sourcePath = process.argv[2] ?? "/Users/pirxhion/Downloads/affirmations-catalog.v1.json";
const ASSET_OUT = join(REPO_ROOT, "app/src/main/assets/catalog.v1.json");
const TAXONOMY_OUT = join(
  REPO_ROOT,
  "app/src/main/java/com/pirxhio/affirmity/ui/groups/CatalogTaxonomy.kt",
);
const CATALOG_ID_PREFIX = "cat_";

function findIllegalBrackets(text) {
  const offsets = [];
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (c === "[" || c === "]") offsets.push(i);
  }
  return offsets;
}

function fail(message) {
  console.error(`[generate-catalog] FAILED: ${message}`);
  process.exit(1);
}

function main() {
  const raw = readFileSync(sourcePath, "utf8");
  const data = JSON.parse(raw);

  const { catalogVersion, universes, themes, collections, affirmations } = data;

  // --- Bracket gate (D11): scan every authored text field. ---
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
    ...affirmations.map((a) => ["affirmation.text", a.id, a.text]),
  ];
  for (const [field, id, value] of textFields) {
    if (typeof value !== "string") continue;
    const offsets = findIllegalBrackets(value);
    if (offsets.length > 0) {
      fail(`literal [ or ] found in ${field} (id=${id}) at offsets ${offsets.join(",")}`);
    }
  }

  // --- Taxonomy validation ---
  const universeById = new Map(universes.map((u) => [u.id, u]));
  const themeById = new Map(themes.map((t) => [t.id, t]));
  const collectionById = new Map(collections.map((c) => [c.id, c]));

  for (const c of collections) {
    if (c.access.tier === "free" && c.access.rewardedUnlockHours !== null) {
      fail(`collection ${c.id} declares tier=free with non-null rewardedUnlockHours`);
    }
    if (c.access.rewardedUnlockHours !== null && c.access.rewardedUnlockHours <= 0) {
      fail(`collection ${c.id} declares non-positive rewardedUnlockHours`);
    }
    if (!universeById.has(c.universeId)) fail(`collection ${c.id} references unknown universeId ${c.universeId}`);
    if (!themeById.has(c.themeId)) fail(`collection ${c.id} references unknown themeId ${c.themeId}`);
  }

  const seenIds = new Set();
  const catalogGatedGroupIds = new Set();
  for (const c of collections) {
    if (c.access.tier === "pro") catalogGatedGroupIds.add(c.universeId);
  }

  // Assign a per-group sequential sortOrder: the source's own `order` restarts at 1 inside each
  // collection, so raw `order` cannot be used directly as the feed's ORDER BY column (design's
  // `catalog_affirmations` query orders by groupId, sortOrder). Sort by
  // (theme.order, collection.order, affirmation.order) inside each universe and assign a dense
  // sequence -- deterministic, and stable across re-generation as long as the source is stable.
  const byGroup = new Map();
  for (const a of affirmations) {
    if (seenIds.has(a.id)) fail(`duplicate affirmation id ${a.id}`);
    seenIds.add(a.id);

    const collection = collectionById.get(a.collectionId);
    if (!collection) fail(`affirmation ${a.id} references unknown collectionId ${a.collectionId}`);

    const theme = themeById.get(collection.themeId);
    const list = byGroup.get(collection.universeId) ?? [];
    list.push({ a, collection, theme });
    byGroup.set(collection.universeId, list);
  }

  const outAffirmations = [];
  for (const [groupId, rows] of byGroup) {
    rows.sort((x, y) => {
      if (x.theme.order !== y.theme.order) return x.theme.order - y.theme.order;
      if (x.collection.order !== y.collection.order) return x.collection.order - y.collection.order;
      return x.a.order - y.a.order;
    });
    rows.forEach(({ a, collection }, index) => {
      outAffirmations.push({
        id: `${CATALOG_ID_PREFIX}${a.id}`,
        text: a.text,
        groupId,
        themeId: collection.themeId,
        collectionId: collection.id,
        sortOrder: index,
      });
    });
  }

  if (seenIds.size !== affirmations.length) fail("affirmation id count mismatch after dedup check");

  // --- Emit bundled asset ---
  mkdirSync(dirname(ASSET_OUT), { recursive: true });
  const assetJson = JSON.stringify({ version: catalogVersion, affirmations: outAffirmations });
  writeFileSync(ASSET_OUT, assetJson, "utf8");

  // --- Emit compiled Kotlin taxonomy ---
  const universesSorted = [...universes].sort((a, b) => a.order - b.order);

  function kotlinStringLiteral(s) {
    return JSON.stringify(s);
  }

  const groupEntries = universesSorted
    .map((u) => {
      const tier = catalogGatedGroupIds.has(u.id) ? "" : ""; // group-level tier is always Free (D6)
      return `    AffirmationGroup(
        id = ${kotlinStringLiteral(u.id)},
        titleRes = R.string.affirmation_group_${u.id}_title,
        descriptionRes = R.string.affirmation_group_${u.id}_description,
        icon = Icons.Filled.AutoAwesome,
        access = ContentAccess.Free,
    ),`;
    })
    .join("\n");

  function contentAccessLiteral(access) {
    if (access.tier === "free") return "ContentAccess.Free";
    if (access.rewardedUnlockHours === null) return "ContentAccess.Pro";
    return `ContentAccess.ProOrAdTimed(${access.rewardedUnlockHours})`;
  }

  const collectionsSorted = [...collections].sort((a, b) => {
    if (a.universeId !== b.universeId) return a.universeId.localeCompare(b.universeId);
    return a.order - b.order;
  });

  const collectionEntries = collectionsSorted
    .map(
      (c) => `    CatalogCollection(
        id = ${kotlinStringLiteral(c.id)},
        universeId = ${kotlinStringLiteral(c.universeId)},
        themeId = ${kotlinStringLiteral(c.themeId)},
        access = ${contentAccessLiteral(c.access)},
        order = ${c.order},
    ),`,
    )
    .join("\n");

  const gatedIdsLiteral = [...catalogGatedGroupIds]
    .sort()
    .map(kotlinStringLiteral)
    .join(", ");

  const kt = `package com.pirxhio.affirmity.ui.groups

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.ContentAccess

/**
 * GENERATED by \`tools/catalog/generate-catalog.mjs\`. DO NOT EDIT BY HAND.
 * Source catalog version: ${catalogVersion}. 14 universes, ${collectionsSorted.length} collections.
 *
 * All 14 universe groups are emitted as [ContentAccess.Free] (design D6/D5): the source declares
 * \`access\` only on collections, never on universes or themes, so a group-level tier would be
 * fabricated. Effective per-affirmation access is \`mostRestrictive(group, collection)\`.
 */

/** The access unit (design D5/D6). \`order\` is the source collection's own editorial order,
 * used only for taxonomy bookkeeping -- feed order comes from \`CatalogAffirmationEntity.sortOrder\`. */
data class CatalogCollection(
    val id: String,
    val universeId: String,
    val themeId: String,
    val access: ContentAccess,
    val order: Int,
)

/** 14 universe-derived groups, order-sorted. All [ContentAccess.Free] at the group level (D6). */
fun catalogUniverseGroups(): List<AffirmationGroup> = listOf(
${groupEntries}
)

/** ${collectionsSorted.length} collections across all 14 universes. */
fun catalogCollections(): List<CatalogCollection> = listOf(
${collectionEntries}
)

private val catalogCollectionsByIdCache: Map<String, CatalogCollection> by lazy {
    catalogCollections().associateBy { it.id }
}

fun catalogCollectionsById(): Map<String, CatalogCollection> = catalogCollectionsByIdCache

/** Universe ids with >=1 Pro collection (design D19). Measured at generation time -- never
 * hard-coded -- so the selector's partial-lock badge reflects the actual editorial split. */
val CATALOG_GATED_GROUP_IDS: Set<String> = setOf(${gatedIdsLiteral})
`;

  mkdirSync(dirname(TAXONOMY_OUT), { recursive: true });
  writeFileSync(TAXONOMY_OUT, kt, "utf8");

  console.log(
    `[generate-catalog] OK: ${outAffirmations.length} affirmations, ${universes.length} universes, ` +
      `${collectionsSorted.length} collections. CATALOG_GATED_GROUP_IDS size=${catalogGatedGroupIds.size}/${universes.length}.`,
  );
  console.log(`[generate-catalog] wrote ${ASSET_OUT}`);
  console.log(`[generate-catalog] wrote ${TAXONOMY_OUT}`);
}

main();
