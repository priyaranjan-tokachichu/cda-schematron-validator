const assert = require('node:assert/strict');
const { test } = require('node:test');
const vm = require('node:vm');
const fs = require('node:fs');
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
async function workspace(maxXmlBytes = 20 * 1024 * 1024) {
  const nodes = new Map();
  const node = () => ({ value: '', checked: true, hidden: false, disabled: false, firstElementChild: { textContent: '' }, classList: { add() {}, remove() {} }, listeners: {}, addEventListener(type, fn) { this.listeners[type] = fn; }, setAttribute() {}, replaceChildren(...children) { this.children = children; }, append() {} });
  const get = id => { if (!nodes.has(id)) nodes.set(id, node()); return nodes.get(id); };
  const pending = [];
  const context = vm.createContext({ TextEncoder, TextDecoder, console, document: { getElementById: get, body: node(), createElement: node, createTextNode: node }, fetch: (url, options) => { if (url === '/v1/profiles') return Promise.resolve({ ok: true, json: async () => ({ profiles: [], limits: { maxXmlBytes, requestCapacity: 6, maxConcurrentUploads: 1, uploadQueueWaitSeconds: 30 } }) }); const d = deferred(); pending.push({ url, options, ...d }); return d.promise; } });
  vm.runInContext(fs.readFileSync('service/web/app.js', 'utf8'), context);
  vm.runInContext('renderReport = report => { latestReport = report; };', context);
  await new Promise(setImmediate);
  get('xml').value = '<old-document/>'; get('profile').value = 'ccda-2.1';
  return { get, pending, run: code => vm.runInContext(code, context) };
}
const report = { findings: [], profile: {}, marker: 'old' };
for (const finishWhileBusy of [true, false]) test(`pending file cannot overwrite validated input (busy=${finishWhileBusy})`, async () => {
  const w = await workspace(), file = deferred();
  const load = w.run('loadFile');
  const loading = load({ size: 10, name: 'new.xml', arrayBuffer: () => file.promise });
  const validating = w.get('validate').onclick();
  if (!finishWhileBusy) { w.pending[0].resolve({ ok: true, json: async () => report }); await validating; }
  file.resolve(new TextEncoder().encode('<new-document/>')); await loading;
  assert.equal(w.get('xml').value, '<old-document/>');
  if (finishWhileBusy) { w.pending[0].resolve({ ok: true, json: async () => report }); await validating; }
  assert.equal(w.run('latestReport.marker'), 'old');
});
test('late example load cannot overwrite validated input', async () => {
  const w = await workspace(); const loading = w.get('load-example').onclick();
  const validating = w.get('validate').onclick();
  w.pending[1].resolve({ ok: true, json: async () => report }); await validating;
  w.pending[0].resolve({ ok: true, text: async () => '<new-document/>' }); await loading;
  assert.equal(w.get('xml').value, '<old-document/>');
});
test('editing input discards the previous validation response', async () => {
  const w = await workspace(); const validating = w.get('validate').onclick();
  w.get('xml').value = '<new-document/>'; w.get('xml').listeners.input();
  w.pending[0].resolve({ ok: true, json: async () => report }); await validating;
  assert.equal(w.run('latestReport'), null);
});
test('latest file selection wins even when reads resolve out of order', async () => {
  const w = await workspace(), first = deferred(), second = deferred(), load = w.run('loadFile');
  const a = load({ size: 1, name: 'first.xml', arrayBuffer: () => first.promise });
  const b = load({ size: 1, name: 'second.xml', arrayBuffer: () => second.promise });
  second.resolve(new TextEncoder().encode('<second/>')); await b;
  first.resolve(new TextEncoder().encode('<first/>')); await a;
  assert.equal(w.get('xml').value, '<second/>');
});

test('clearing input cancels an outstanding file load', async () => {
  const w = await workspace(), file = deferred();
  const loading = w.run('loadFile')({ size: 1, name: 'pending.xml', arrayBuffer: () => file.promise });
  w.get('clear').onclick();
  file.resolve(new TextEncoder().encode('<pending/>')); await loading;
  assert.equal(w.get('xml').value, '');
});
test('upload allowance and label follow the server configuration', async () => {
  const w = await workspace(40 * 1024 * 1024);
  assert.equal(w.get('upload-limit').textContent, 'UTF-8 XML · up to 40 MiB');
  const load = w.run('loadFile');
  await load({ size: 21 * 1024 * 1024, name: 'allowed.xml', arrayBuffer: async () => new TextEncoder().encode('<allowed/>') });
  assert.equal(w.get('xml').value, '<allowed/>');
  await load({ size: 41 * 1024 * 1024, name: 'large.xml', arrayBuffer: () => { throw new Error('must not read oversized input'); } });
  assert.equal(w.get('xml').value, '<allowed/>');
  assert.match(w.get('notice').textContent, /40 MiB/);
});

test('service capacity explains automatic queuing and heap tuning', async () => {
  const w = await workspace();
  assert.match(w.get('capacity-note').textContent, /accepts up to 6 requests/);
  assert.match(w.get('capacity-note').textContent, /wait in the queue for up to 30 seconds/);
  assert.match(w.get('capacity-note').textContent, /Increase the Java heap and container memory/);
});

test('profile details distinguish active checks from imported terminology data', async () => {
  const w = await workspace();
  w.get('profile').value = 'ccda-5.0';
  w.run("profiles = [{id:'ccda-5.0',release:'2026JUL',limitations:['Coverage remains incomplete'],supplementalChecks:[{ruleId:'81-9371'}],terminologySnapshots:[{activeForValidation:true},{activeForValidation:false}]}]; profileChanged();");
  let text = w.get('limitations').children.map(node => node.textContent).join('\n');
  assert.match(text, /Additional checks installed: 81-9371/);
  assert.match(text, /1 active snapshot\(s\), 2 imported/);
  assert.match(text, /Imported data alone does not establish complete validation coverage/);
  assert.match(text, /Coverage remains incomplete/);
  w.run('profiles[0].terminologySnapshots[0].activeForValidation = false; profileChanged();');
  text = w.get('limitations').children.map(node => node.textContent).join('\n');
  assert.match(text, /0 active snapshot\(s\), 2 imported/);
});
