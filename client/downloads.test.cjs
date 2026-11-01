const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const script = fs.readFileSync(__dirname + '/downloads.js', 'utf8');
function run(navigator) {
  const context = { navigator, document: { documentElement: { classList: { toggle() {} } } }, localStorage: { getItem() { throw new Error('disabled'); } } };
  vm.createContext(context);
  vm.runInContext(script, context);
  return context;
}
test('OS routing handles client hints, Safari on M1, mobile and unsupported platforms', () => {
  const context = run({});
  for (const [nav, expected] of [
    [{ userAgentData: { platform: 'Windows' } }, 'windows'],
    [{ platform: 'Win32' }, 'windows'],
    [{ platform: 'MacIntel', userAgent: 'Mozilla/5.0 Macintosh', maxTouchPoints: 0 }, 'mac'],
    [{ userAgentData: { platform: 'macOS' } }, 'mac'],
    [{ platform: 'MacIntel', maxTouchPoints: 5 }, null],
    [{ userAgent: 'Mozilla/5.0 iPhone like Mac OS X' }, null],
    [{ userAgent: 'Android', platform: 'Linux' }, null],
    [{ platform: 'Linux x86_64' }, null],
  ]) assert.equal(context.downloadPlatform(nav), expected);
});
test('download buttons recover from unsupported state and survive blocked localStorage', () => {
  const context = run({ platform: 'Linux', language: 'ko' });
  const attrs = {};
  const classes = new Set();
  const el = {
    classList: { toggle(key, enabled) { enabled ? classes.add(key) : classes.delete(key); } },
    setAttribute(key, value) { attrs[key] = value; },
    removeAttribute(key) { delete attrs[key]; if (key === 'href') delete this.href; },
  };
  context.applyDownloadAvailability(el);
  assert.equal(el.href, undefined);
  assert.equal(attrs['aria-disabled'], 'true');
  context.navigator.platform = 'MacIntel';
  context.applyDownloadAvailability(el);
  assert.match(el.href, /MinePortal-mac-arm64\.dmg$/);
  assert.equal(classes.has('btn-disabled'), false);
  assert.equal(attrs['aria-disabled'], 'false');
  context.navigator.platform = 'Win32';
  context.applyDownloadAvailability(el);
  assert.match(el.href, /MinePortal-win\.zip$/);
});
