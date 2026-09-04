import assert from 'node:assert/strict';
import { build } from 'esbuild';
const result = await build({
  entryPoints: [new URL('../src/modules/inbox/customerGroups.ts', import.meta.url).pathname],
  bundle: true, write: false, platform: 'node', format: 'esm',
});
const { groupCustomers, channelKey } = await import('data:text/javascript;base64,' + Buffer.from(result.outputFiles[0].text).toString('base64'));
const item = (sessionId, userId, channelType, updatedAt) => ({ sessionId, userId, channelType, updatedAt, customerName: 'Same name' });
const groups = groupCustomers([
  item('one', 'u1', 'FEISHU', '2026-01-01'),
  item('two', 'u1', 'WECHAT', '2026-01-03'),
  item('three', 'u2', 'FEISHU', '2026-01-02'),
  item('four', null, null, '2026-01-01'),
  item('five', null, null, '2026-01-01'),
]);
assert.equal(groups.length, 4);
assert.equal(groups[0].key, 'user:u1');
assert.equal(groups[0].sessions.length, 2);
assert.deepEqual(groups[0].channels, ['WECHAT', 'FEISHU']);
assert.equal(groups[1].key, 'user:u2');
assert.equal(channelKey(item('dbg-test')), 'DEBUG');
assert.equal(channelKey(item('ch-legacy')), 'UNKNOWN');
assert.deepEqual(groupCustomers([]), []);
console.log('Customer grouping regression checks passed');
