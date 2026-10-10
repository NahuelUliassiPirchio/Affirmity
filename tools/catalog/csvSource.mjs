/**
 * Pure CSV -> source-JSON transform (no I/O). The v5 catalog content is authored as a CSV
 * (`source/affirmations.v5.csv`); the taxonomy lives in `source/taxonomy.json`. This module joins
 * them into the `{catalogVersion, universes, themes, collections, affirmations}` shape that
 * `buildCatalog.mjs` expects.
 */

export const CATALOG_SOURCE_VERSION = '5.1.0';

const REQUIRED_COLUMNS = ['group', 'theme', 'collection', 'id', 'tone', 'semanticAngle', 'title', 'subtitle'];
const CSV_ID_PREFIX = 'cat_';

function fail(message) {
  throw new Error(`[csvSource] ${message}`);
}

/** RFC-4180-ish parser: optional BOM, quoted fields, `""` escapes, CRLF/LF, newlines in quotes. */
export function parseCsv(text) {
  const input = text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
  const rows = [];
  let row = [];
  let field = '';
  let inQuotes = false;
  let i = 0;
  while (i < input.length) {
    const ch = input[i];
    if (inQuotes) {
      if (ch === '"') {
        if (input[i + 1] === '"') { field += '"'; i += 2; continue; }
        inQuotes = false; i++; continue;
      }
      field += ch; i++; continue;
    }
    if (ch === '"') { inQuotes = true; i++; }
    else if (ch === ',') { row.push(field); field = ''; i++; }
    else if (ch === '\r' || ch === '\n') {
      if (ch === '\r' && input[i + 1] === '\n') i++;
      i++;
      row.push(field); field = '';
      rows.push(row); row = [];
    } else { field += ch; i++; }
  }
  if (inQuotes) fail('unterminated quoted field');
  if (field !== '' || row.length > 0) { row.push(field); rows.push(row); }
  return rows;
}

/** @param {string} csvText @param {{universes: object[], themes: object[], collections: object[]}} taxonomy */
export function csvToSource(csvText, taxonomy) {
  const [header, ...body] = parseCsv(csvText);
  if (!header) fail('empty CSV');
  const col = new Map(header.map((name, index) => [name.trim(), index]));
  for (const name of REQUIRED_COLUMNS) {
    if (!col.has(name)) fail(`missing column "${name}"`);
  }

  const affirmations = body.map((fields, index) => {
    const line = index + 2;
    if (fields.length !== header.length) {
      fail(`line ${line}: expected ${header.length} fields, got ${fields.length}`);
    }
    const get = (name) => fields[col.get(name)];
    const rawId = get('id').trim();
    if (rawId === '') fail(`line ${line}: empty id`);
    if (!rawId.startsWith(CSV_ID_PREFIX)) {
      fail(`line ${line}: id "${rawId}" must start with "${CSV_ID_PREFIX}"`);
    }
    const id = rawId.slice(CSV_ID_PREFIX.length);
    if (id === '') fail(`line ${line}: id "${rawId}" has nothing after "${CSV_ID_PREFIX}"`);
    return {
      id,
      collectionId: get('collection'),
      themeId: get('theme'),
      universeId: get('group'),
      title: get('title'),
      subtitle: get('subtitle'),
      // buildCatalog sorts by this within a collection; CSV row order is authoritative.
      order: index,
      tone: get('tone') || null,
      semanticAngle: get('semanticAngle') || null,
    };
  });

  return {
    catalogVersion: CATALOG_SOURCE_VERSION,
    universes: taxonomy.universes,
    themes: taxonomy.themes,
    collections: taxonomy.collections,
    affirmations,
  };
}
