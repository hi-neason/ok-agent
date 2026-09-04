import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { build } from 'esbuild';

const directory = await mkdtemp(join(tmpdir(), 'ok-integration-options-'));
const originalFetch = globalThis.fetch;
try {
  for (const domain of ['workflow', 'knowledge']) {
    const outfile = join(directory, domain + '.mjs');
    await build({
      entryPoints: [new URL(`../src/modules/${domain}/api.ts`, import.meta.url).pathname],
      outfile, bundle: true, platform: 'node', format: 'esm',
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
    assert.equal((await api.listAllSources()).length, 101);
    assert.deepEqual(calls, [`/api/v1/${domain}/sources?page=0&size=100`, `/api/v1/${domain}/sources?page=1&size=100`]);
    globalThis.fetch = async () => ({ ok: false, status: 400, json: async () => ({ message: 'Rejected' }) });
    await assert.rejects(api.listAllSources(), /Rejected/);
    globalThis.fetch = async () => ({ ok: true, status: 200, json: async () => ({ content: [], totalPages: 0 }) });
    assert.deepEqual(await api.listAllSources(), []);
    globalThis.fetch = async () => ({ ok: true, status: 200, json: async () => ({ content: [], totalPages: 2 }) });
    await assert.rejects(api.listAllSources(), /Incomplete/);
  }
  console.log('Workflow and knowledge option regression checks passed');
} finally {
  globalThis.fetch = originalFetch;
  await rm(directory, { recursive: true, force: true });
}
