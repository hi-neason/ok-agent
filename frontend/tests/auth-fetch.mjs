import assert from 'node:assert/strict';
import { build } from 'esbuild';

const output = await build({
  entryPoints: [new URL('../src/modules/auth/authFetch.ts', import.meta.url).pathname],
  bundle: true, write: false, platform: 'node', format: 'esm',
});
const storage = new Map();
const events = [];
let response;
let sentHeaders;
globalThis.window = {
  location: { origin: 'http://localhost' },
  sessionStorage: {
    getItem: (key) => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: (key) => storage.delete(key),
  },
  dispatchEvent: (event) => events.push(event.type),
  fetch: async (_, init) => { sentHeaders = init.headers; return response; },
};
const api = await import('data:text/javascript;base64,' + Buffer.from(output.outputFiles[0].text).toString('base64'));
api.installAuthenticatedFetch();
api.installAuthenticatedFetch();
api.storeAccessToken('test-token');
const envelope = { success: true, code: 'OK', message: 'OK', data: { content: [{ id: 'agent' }], totalPages: 1 } };
response = Response.json(envelope);
assert.deepEqual(await (await window.fetch('/api/v1/agents')).json(), envelope.data);
assert.equal(sentHeaders.get('Authorization'), 'Bearer test-token');
response = Response.json(envelope);
assert.deepEqual(await (await window.fetch('https://external.example/api/v1/agents')).json(), envelope);
assert.equal(sentHeaders.has('Authorization'), false);
response = Response.json({ success: false, code: 'VALIDATION_ERROR' }, { status: 400 });
const failed = await window.fetch('/api/v1/agents');
assert.equal(failed.status, 400);
assert.equal((await failed.json()).code, 'VALIDATION_ERROR');
response = new Response(null, { status: 204 });
assert.equal((await window.fetch('/api/v1/test')).status, 204);
response = new Response('event: token\ndata: hello\n\n', { headers: { 'content-type': 'text/event-stream' } });
assert.match(await (await window.fetch('/api/v1/test')).text(), /data: hello/);
response = new Response(null, { status: 401 });
await window.fetch('/api/v1/auth/login');
assert.equal(api.getAccessToken(), 'test-token');
response = new Response(null, { status: 401 });
await window.fetch('/api/v1/agents');
assert.equal(api.getAccessToken(), null);
assert.deepEqual(events, [api.AUTH_UNAUTHORIZED_EVENT]);
delete globalThis.window;
console.log('Authenticated response contract regression checks passed');
