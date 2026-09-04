import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { build } from 'esbuild';

const directory = await mkdtemp(join(tmpdir(), 'ok-release-options-'));
const originalFetch = globalThis.fetch;
try {
  const outfile = join(directory, 'api.mjs');
  await build({
    entryPoints: [new URL('../src/modules/release/api.ts', import.meta.url).pathname],
    outfile, bundle: true, platform: 'node', format: 'esm',
    plugins: [{
      name: 'test-i18n',
      setup(builder) {
        builder.onResolve({ filter: /\/i18n$/ }, () => ({ path: 'i18n', namespace: 'test' }));
        builder.onLoad({ filter: /.*/, namespace: 'test' }, () => ({
          contents: 'export default { t: (key) => key };', loader: 'js',
        }));
      },
    }],
  });
  const api = await import(pathToFileURL(outfile).href);
  for (const [loader, path] of [['listAgents', 'agents'], ['listChannels', 'channels']]) {
    const calls = [];
    globalThis.fetch = async (url) => {
      calls.push(url);
      return { ok: true, status: 200, json: async () => ({
        content: calls.length === 1
          ? Array.from({ length: 100 }, (_, id) => ({ id, name: 'Option', enabled: true }))
          : [{ id: 100, name: 'Last', enabled: true }],
        totalPages: 2,
      }) };
    };
    assert.equal((await api[loader]()).length, 101);
    assert.deepEqual(calls, [`/api/v1/${path}?page=0&size=100`, `/api/v1/${path}?page=1&size=100`]);
    globalThis.fetch = async () => ({ ok: false, status: 400, json: async () => ({ message: 'Invalid page size' }) });
    await assert.rejects(api[loader](), /Invalid page size/);
    globalThis.fetch = async () => ({ ok: true, status: 200, json: async () => ({ content: [], totalPages: 0 }) });
    assert.deepEqual(await api[loader](), []);
    globalThis.fetch = async () => ({ ok: true, status: 200, json: async () => ({ content: [] }) });
    await assert.rejects(api[loader]());
  }
  console.log('Release option loading regression checks passed');
} finally {
  globalThis.fetch = originalFetch;
  await rm(directory, { recursive: true, force: true });
}
