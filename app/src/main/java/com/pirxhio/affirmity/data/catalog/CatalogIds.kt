package com.pirxhio.affirmity.data.catalog

/**
 * Catalog affirmation id = [CATALOG_ID_PREFIX] + the source catalog's own dotted id, verbatim
 * (design D3), e.g. `cat_self_worth.feeling_enough.intrinsic_worth.001`. Disjoint from
 * `UUID.randomUUID()` by construction: a UUID's string form is `[0-9a-f-]` only, so it can never
 * start with `cat_`.
 */
const val CATALOG_ID_PREFIX = "cat_"
