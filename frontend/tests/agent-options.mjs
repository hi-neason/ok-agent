import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { build } from 'esbuild';

const directory = await mkdtemp(join(tmpdir(), 'ok-agent-options-'));
const originalFetch = globalThis.fetch;
try {
  const outfile = join(directory, 'api.mjs');
  await build({
    entryPoints: [new URL('../src/modules/agent/api.ts', import.meta.url).pathname],
    outfile, bundle: true, platform: 'node', format: 'esm',
  });
  const api = await import(pathToFileURL(outfile).href);
  for (const [loader, path] of [
    ['loadModels', 'models'], ['loadAgents', 'agents'],
    ['loadMcpServers', 'mcp-servers'], ['loadSkills', 'skills'], ['loadUsers', 'users'],
  ]) {
    const calls = [];
    globalThis.fetch = async (url) => {
      calls.push(url);
      const page = Number(new URL(url, 'http://localhost').searchParams.get('page'));
      return { ok: true, status: 200, json: async () => ({
        content: page === 0
          ? Array.from({ length: 100 }, (_, id) => ({ id, userId: String(id), name: `Item ${id}`, enabled: true }))
          : [{ id: 100, userId: '100', name: 'Last', enabled: true }, { id: 101, enabled: false }],
        totalPages: 2,
      }) };
    };
    const items = await api[loader]();
    assert.equal(items.length, 101, `${loader} must include later pages and exclude disabled items`);
    assert.deepEqual(calls, [`/api/v1/${path}?page=0&size=100`, `/api/v1/${path}?page=1&size=100`]);
  }
  globalThis.fetch = async () => ({ ok: false, status: 400, json: async () => ({ message: 'Invalid page size' }) });
  await assert.rejects(api.loadModels(), /Invalid page size/);
  globalThis.fetch = async () => ({ ok: true, status: 200, json: async () => ({ content: [], totalPages: 0 }) });
  assert.deepEqual(await api.loadModels(), []);
  globalThis.fetch = async () => ({ ok: true, status: 200, json: async () => ({ content: [] }) });
  await assert.rejects(api.loadModels(), /Invalid option page response/);
  console.log('Agent option loading regression checks passed');
} finally {
  globalThis.fetch = originalFetch;
  await rm(directory, { recursive: true, force: true });
}
