import assert from 'node:assert/strict';
import { build } from 'esbuild';

const output = await build({
  entryPoints: [new URL('../src/modules/shared/loadAllPages.ts', import.meta.url).pathname],
  bundle: true, write: false, platform: 'node', format: 'esm',
});
const api = await import('data:text/javascript;base64,' + Buffer.from(output.outputFiles[0].text).toString('base64'));

const calls = [];
const items = await api.loadAllPages(async (page, size) => {
  calls.push([page, size]);
  return {
    content: page === 0 ? [{ id: 'one' }] : [{ id: 'two' }],
    totalPages: 2,
    totalElements: 2,
    number: page,
    size,
  };
});
assert.deepEqual(items.map((item) => item.id), ['one', 'two']);
assert.deepEqual(calls, [[0, 100], [1, 100]]);

await assert.rejects(
  api.loadAllPages(async () => ({
    content: [],
    totalPages: 51,
    totalElements: 0,
    number: 0,
    size: 100,
  })),
  /page limit/,
);

await assert.rejects(
  api.loadAllPages(async () => ({
    content: [{ id: 'one' }, { id: 'two' }],
    totalPages: 1,
    totalElements: 2,
    number: 0,
    size: 100,
  }), { maxItems: 1 }),
  /item limit/,
);

await assert.rejects(
  api.loadAllPages(async () => ({
    content: [],
    totalPages: 0,
    totalElements: 0,
    number: 0,
    size: 100,
  }), { maxPages: 0 }),
  /Invalid pagination limit/,
);

await assert.rejects(
  api.loadAllPages(async () => ({ content: new Array(200_000).fill(null), totalPages: 1 })),
  /Pagination response exceeds item limit/,
);

for (const key of ['maxPages', 'maxItems']) {
  let requested = false;
  await assert.rejects(api.loadAllPages(async () => {
    requested = true;
    return { content: [], totalPages: 0 };
  }, { [key]: Number.MAX_SAFE_INTEGER + 1 }), /Invalid pagination limit/);
  assert.equal(requested, false, 'Invalid limits must be rejected before requesting data');
}

console.log('Bounded loadAllPages regression checks passed');
