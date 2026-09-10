/**
 * Pure `source -> {asset, taxonomyKt}` transform (design D5). No I/O, no bracket gate -- the
 * bracket gate is a separate, orthogonal step run by `generate-catalog.mjs`'s I/O shell over
 * `title`+`subtitle`+taxonomy text before this function is even called.
 *
 * Keeps the `collectionById` join as a cross-check (design D3): the affirmation's own denormalized
 * `themeId`/`universeId` are validated against the resolved collection's, and `fail()`s naming the
 * affirmation id on any disagreement -- the collection is authoritative for taxonomy since access
 * and order already come from it.
 */

const CATALOG_ID_PREFIX = 'cat_';

function fail(message) {
  throw new Error(`[buildCatalog] ${message}`);
}

function contentAccessLiteral(access) {
  if (access.tier === 'free') return 'ContentAccess.Free';
  if (access.rewardedUnlockHours === null) return 'ContentAccess.Pro';
  return `ContentAccess.ProOrAdTimed(${access.rewardedUnlockHours})`;
}

function kotlinStringLiteral(s) {
  return JSON.stringify(s);
}

/** @param {object} source - parsed v2 source catalog (shape: `SourceCatalog`, see seedCatalog.ts) */
export function buildCatalog(source) {
  const { catalogVersion, universes, themes, collections, affirmations } = source;

  const universeById = new Map(universes.map((u) => [u.id, u]));
  const collectionById = new Map(collections.map((c) => [c.id, c]));

  for (const c of collections) {
    if (c.access.tier === 'free' && c.access.rewardedUnlockHours !== null) {
      fail(`collection ${c.id} declares tier=free with non-null rewardedUnlockHours`);
    }
    if (c.access.rewardedUnlockHours !== null && c.access.rewardedUnlockHours <= 0) {
      fail(`collection ${c.id} declares non-positive rewardedUnlockHours`);
    }
    if (!universeById.has(c.universeId)) fail(`collection ${c.id} references unknown universeId ${c.universeId}`);
  }

  const catalogGatedGroupIds = new Set();
  for (const c of collections) {
    if (c.access.tier === 'pro') catalogGatedGroupIds.add(c.universeId);
  }

  const seenIds = new Set();
  const byGroup = new Map();
  for (const a of affirmations) {
    if (seenIds.has(a.id)) fail(`duplicate affirmation id ${a.id}`);
    seenIds.add(a.id);

    const collection = collectionById.get(a.collectionId);
    if (!collection) fail(`affirmation ${a.id} references unknown collectionId ${a.collectionId}`);

    // D3: the collection is authoritative for taxonomy -- fail loudly on any disagreement rather
    // than silently trusting the source's own denormalized fields.
    if (a.themeId !== collection.themeId) {
      fail(`affirmation ${a.id} themeId "${a.themeId}" disagrees with resolved collection "${collection.themeId}"`);
    }
    if (a.universeId !== collection.universeId) {
      fail(`affirmation ${a.id} universeId "${a.universeId}" disagrees with resolved collection "${collection.universeId}"`);
    }

    const theme = themes.find((t) => t.id === collection.themeId);
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
        title: a.title,
        subtitle: a.subtitle,
        groupId,
        themeId: collection.themeId,
        collectionId: collection.id,
        sortOrder: index,
      });
    });
  }

  const asset = { version: catalogVersion, affirmations: outAffirmations };

  // --- Kotlin taxonomy source ---
  const universesSorted = [...universes].sort((a, b) => a.order - b.order);
  const groupEntries = universesSorted
    .map(
      (u) => `    AffirmationGroup(
        id = ${kotlinStringLiteral(u.id)},
        titleRes = R.string.affirmation_group_${u.id}_title,
        descriptionRes = R.string.affirmation_group_${u.id}_description,
        icon = Icons.Filled.AutoAwesome,
        access = ContentAccess.Free,
    ),`,
    )
    .join('\n');

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
    .join('\n');

  const gatedIdsLiteral = [...catalogGatedGroupIds].sort().map(kotlinStringLiteral).join(', ');

  const taxonomyKt = `package com.pirxhio.affirmity.ui.groups

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.ContentAccess

/**
 * GENERATED by \`tools/catalog/generate-catalog.mjs\`. DO NOT EDIT BY HAND.
 * Source catalog version: ${catalogVersion}. ${universesSorted.length} universes, ${collectionsSorted.length} collections.
 *
 * All ${universesSorted.length} universe groups are emitted as [ContentAccess.Free] (design D6/D5): the source declares
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

/** ${universesSorted.length} universe-derived groups, order-sorted. All [ContentAccess.Free] at the group level (D6). */
fun catalogUniverseGroups(): List<AffirmationGroup> = listOf(
${groupEntries}
)

/** ${collectionsSorted.length} collections across all ${universesSorted.length} universes. */
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

  return { asset, taxonomyKt };
}
