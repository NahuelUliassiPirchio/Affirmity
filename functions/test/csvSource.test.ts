import { describe, expect, it } from 'vitest';

import { parseCsv, csvToSource, CATALOG_SOURCE_VERSION } from '../../tools/catalog/csvSource.mjs';

const HEADER = 'group,theme,collection,id,sortOrder,tone,semanticAngle,title,subtitle';

describe('parseCsv', () => {
  it('strips a UTF-8 BOM', () => {
    expect(parseCsv('﻿a,b\n1,2')).toEqual([['a', 'b'], ['1', '2']]);
  });
  it('handles quoted fields with commas, escaped quotes and embedded newlines', () => {
    expect(parseCsv('a,b\n"x, y","say ""hi""\nthere"')).toEqual([['a', 'b'], ['x, y', 'say "hi"\nthere']]);
  });
  it('handles CRLF and a trailing newline without emitting an empty row', () => {
    expect(parseCsv('a,b\r\n1,2\r\n')).toEqual([['a', 'b'], ['1', '2']]);
  });
  it('keeps empty fields', () => {
    expect(parseCsv('a,b,c\n1,,3')).toEqual([['a', 'b', 'c'], ['1', '', '3']]);
  });
  it('fails on an unterminated quote', () => {
    expect(() => parseCsv('a\n"oops')).toThrow(/unterminated/i);
  });
});

describe('csvToSource', () => {
  const taxonomy = { universes: [{ id: 'u' }], themes: [{ id: 'u.t' }], collections: [{ id: 'u.t.c' }] };
  const row = (id: string, title = 'T', sub = 'S') => `u,u.t,u.t.c,${id},1,direct,action,${title},${sub}`;

  it('maps rows, strips the cat_ prefix, preserves CSV order and sets the version', () => {
    const csv = `﻿${HEADER}\r\n${row('cat_u.t.c.v4.02', '"A, b"')}\r\n${row('cat_u.t.c.v4.01')}\r\n`;
    const src = csvToSource(csv, taxonomy);
    expect(src.catalogVersion).toBe(CATALOG_SOURCE_VERSION);
    expect(CATALOG_SOURCE_VERSION).toBe('5.0.0');
    expect(src.universes).toBe(taxonomy.universes);
    expect(src.affirmations.map((a: { id: string }) => a.id)).toEqual(['u.t.c.v4.02', 'u.t.c.v4.01']);
    expect(src.affirmations[0]).toMatchObject({
      collectionId: 'u.t.c', themeId: 'u.t', universeId: 'u', title: 'A, b', subtitle: 'S',
      tone: 'direct', semanticAngle: 'action', order: 0,
    });
    expect(src.affirmations[1].order).toBe(1);
  });
  it('fails loudly on a missing column', () => {
    expect(() => csvToSource('group,theme,id\nu,t,x', taxonomy)).toThrow(/missing column/i);
  });
  it('fails loudly on an empty id', () => {
    expect(() => csvToSource(`${HEADER}\n${row('')}`, taxonomy)).toThrow(/empty id/i);
  });
  it('fails on a row with the wrong field count', () => {
    expect(() => csvToSource(`${HEADER}\nu,u.t`, taxonomy)).toThrow(/fields/i);
  });
  it('rejects an id that lacks the cat_ prefix, naming the line and id', () => {
    expect(() => csvToSource(`${HEADER}\n${row('x')}`, taxonomy)).toThrow(/line 2.*"x".*cat_/i);
  });
  it('rejects an id that is only the cat_ prefix', () => {
    expect(() => csvToSource(`${HEADER}\n${row('cat_')}`, taxonomy)).toThrow(/line 2.*"cat_".*after/i);
  });
});
