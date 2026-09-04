import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { build } from 'esbuild';

const directory = await mkdtemp(join(tmpdir(), 'ok-integration-options-'));
const originalFetch = globalThis.fetch;
try {
  for (const [domain, loader, endpoint] of [
    ['workflow', 'listAllSources', 'workflow/sources'],
    ['knowledge', 'listAllSources', 'knowledge/sources'],
    ['product', 'listAllProducts', 'products'],
    ['mcp', 'fetchAllServers', 'mcp-servers'],
    ['usermgmt', 'fetchUserGroups', 'user-groups'],
    ['usermgmt', 'fetchUsers', 'users'],
    ['persona', 'fetchPersonaAgents', 'agents'],
  ]) {
    const outfile = join(directory, domain + '.mjs');
    await build({
      entryPoints: [new URL(`../src/modules/${domain}/api.ts`, import.meta.url).pathname],
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
    const calls = [];
    globalThis.fetch = async (url) => {
      calls.push(url);
      return { ok: true, status: 200, json: async () => ({
        content: calls.length === 1 ? Array.from({ length: 100 }, (_, id) => ({ id })) : [{ id: 100 }],
        totalPages: 2,
      }) };
    };
    assert.equal((await api[loader]()).length, 101);
    assert.deepEqual(calls, [`/api/v1/${endpoint}?page=0&size=100`, `/api/v1/${endpoint}?page=1&size=100`]);
    globalThis.fetch = async () => ({ ok: false, status: 400, json: async () => ({ message: 'Rejected' }) });
    await assert.rejects(api[loader]());
    globalThis.fetch = async () => ({ ok: true, status: 200, json: async () => ({ content: [], totalPages: 0 }) });
    assert.deepEqual(await api[loader](), []);
    globalThis.fetch = async () => ({ ok: true, status: 200, json: async () => ({ content: [], totalPages: 2 }) });
    await assert.rejects(api[loader](), /Incomplete/);
  }
  const outfile = join(directory, 'inbox.mjs');
  await build({
    entryPoints: [new URL('../src/modules/inbox/api.ts', import.meta.url).pathname],
    outfile, bundle: true, platform: 'node', format: 'esm',
  });
  const inbox = await import(pathToFileURL(outfile).href);
  globalThis.fetch = async (url) => {
    const params = new URL(url, 'http://localhost').searchParams;
    assert.equal(params.get('page'), '2');
    assert.equal(params.get('size'), '50');
    assert.equal(params.get('status'), 'OPEN');
    return { ok: true, json: async () => ({ content: [], totalPages: 3 }) };
  };
  await inbox.listWorkItems('OPEN', 2, 50);
  console.log('All option loading and inbox pagination regression checks passed');
} finally {
  globalThis.fetch = originalFetch;
  await rm(directory, { recursive: true, force: true });
}
