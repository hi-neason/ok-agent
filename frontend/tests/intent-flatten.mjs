import assert from 'node:assert/strict';
import { build } from 'esbuild';

const output = await build({
  entryPoints: [new URL('../src/modules/intent/api.ts', import.meta.url).pathname],
  bundle: true, write: false, platform: 'node', format: 'esm',
});
const { flatten } = await import('data:text/javascript;base64,' + Buffer.from(output.outputFiles[0].text).toString('base64'));
assert.deepEqual(flatten([]), []);
assert.deepEqual(flatten([
  { node: { id: 'a' }, children: [{ node: { id: 'b' }, children: [] }] },
  { node: { id: 'c' }, children: [] },
]).map(n => n.id), ['a', 'b', 'c']);
let tree = [];
for (let id = 19_999; id >= 0; id -= 1) tree = [{ node: { id }, children: tree }];
const nodes = flatten(tree);
assert.equal(nodes.length, 20_000);
assert.equal(nodes[0].id, 0);
assert.equal(nodes.at(-1).id, 19_999);
console.log('Intent tree flatten regression checks passed');
