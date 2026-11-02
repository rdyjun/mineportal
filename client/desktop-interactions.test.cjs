const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8');
function between(start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from);
  assert.ok(from >= 0 && to > from, 'production interaction code must be found');
  return source.slice(from, to);
}

test('an active login attempt can be canceled from the slot button', () => {
  const calls = [];
  const button = { classList: { add() {}, remove() {} } };
  const context = {
    slots: [{ serverId: 'server-a' }],
    servers: [{ id: 'server-a', phase: 'active', connected: false }],
    slotRefs: [{ connectBtnEl: button }],
    t: key => key,
    i: 0,
    connectBtnEl: { addEventListener(_name, handler) { context.click = handler; } },
    disconnectServerInSlot: i => calls.push(['disconnect', i]),
    connectServerInSlot: i => calls.push(['connect', i]),
  };
  vm.runInNewContext(between('function updateSlotConnectBtn(i)', 'async function connectServerInSlot(i)'), context);
  context.updateSlotConnectBtn(0);
  assert.equal(button.disabled, false);
  assert.equal(button.textContent, 'cancelConnectBtn');
  vm.runInNewContext(between('connectBtnEl.addEventListener("click",', '// Drop target'), context);
  context.click();
  assert.deepEqual(calls, [['disconnect', 0]]);
});

test('a completed desktop pairing closes its blocking modal and enters real mode', () => {
  const calls = [];
  const context = {
    socket: {},
    account: { status: 'logged-in', desktopConnected: false },
    autoPairArmed: true,
    IS_DESKTOP: true,
    renderAccount() {},
    pairDesktopModalBackdrop: { classList: { remove: name => calls.push(['remove', name]) } },
    stopAutoPair() { context.autoPairArmed = false; },
    setPlayMode: (...args) => calls.push(['mode', ...args]),
    attemptAutoPair() { throw new Error('already paired: no new grant should be requested'); },
  };
  vm.runInNewContext(between('socket.onmessage = (evt) => {', 'socket.onclose ='), context);
  context.socket.onmessage({ data: JSON.stringify({ type: 'account', account: { status: 'logged-in', desktopConnected: true } }) });
  assert.equal(context.autoPairArmed, false);
  assert.equal(context.desktopAvailable, true);
  assert.deepEqual(calls, [['remove', 'open'], ['mode', 'real', false]]);
});

function browserLoginContext(userAgent, replies) {
  const calls = [];
  const button = { setAttribute() {}, removeAttribute() {}, addEventListener() {} };
  const context = {
    navigator: { userAgent },
    document: { getElementById: () => button },
    window: { location: { replace: value => calls.push(['replace', value]) } },
    lang: 'ko', showToast() {}, setTimeout: resolve => resolve(),
    fetch: async (url, options) => {
      calls.push([url, options]);
      const reply = replies.shift();
      assert.ok(reply, 'unexpected extra request');
      return { ok: reply.ok !== false, json: async () => reply };
    },
  };
  const from = source.indexOf('let desktopLoginPending = false;');
  const end = source.indexOf('document.getElementById("msLoginBtn").addEventListener', from);
  vm.runInNewContext(source.slice(from, end), context);
  return { context, calls };
}

test('browser sign-in proof stays in the app and completion reloads only the portal', async () => {
  const id = '11111111-1111-1111-1111-111111111111';
  const { context, calls } = browserLoginContext('MinePortalDesktop/1.2', [
    { id, proof: 'private-polling-proof' }, { ready: true },
  ]);
  let prevented = false;
  await context.startDesktopBrowserLogin({ preventDefault() { prevented = true; } });
  assert.equal(prevented, true);
  assert.equal(context.window.status, 'mineportal:login:' + id);
  assert.equal(calls[1][0], `/api/account/desktop-login/${id}/claim`);
  assert.equal(calls[1][1].method, 'POST');
  assert.deepEqual(JSON.parse(calls[1][1].body), { proof: 'private-polling-proof' });
  assert.deepEqual(calls[2], ['replace', '/']);
  assert.ok(calls.every(([url]) => !url.includes('private-polling-proof')));
});

test('a failed browser handoff does not fall back to the embedded password page', async () => {
  const { context, calls } = browserLoginContext('MinePortalDesktop/1.2', [{ ok: false }]);
  let prevented = false;
  await context.startDesktopBrowserLogin({ preventDefault() { prevented = true; } });
  assert.equal(prevented, true);
  assert.equal(calls.length, 1);
  assert.equal(context.window.status, undefined);
});

test('ordinary web sign-in keeps its existing navigation', async () => {
  const { context, calls } = browserLoginContext('Safari', []);
  await context.startDesktopBrowserLogin({ preventDefault() { assert.fail('web login intercepted'); } });
  assert.equal(calls.length, 0);
});
