const test = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const path = require("node:path");

const source = fs.readFileSync(path.join(__dirname, "index.html"), "utf8");
const notice = "웹 체험하기에서는 서버를 추가할 수 없어요. 다른 서버는 데스크톱 앱으로 접속해 주세요.";

function slice(start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from + start.length);
  assert.ok(from >= 0 && to > from, start + " must be found");
  return source.slice(from, to);
}

test("trial mode keeps the add button but shows the notice instead of adding", () => {
  assert.match(source, /id="addServerBtn"/);
  assert.match(source, /id="trialAddNoticeBackdrop"/);
  assert.ok(source.includes(notice));
  assert.ok(source.includes("You can't add a server in web Try it. Use the desktop app to connect to other servers."));
  assert.ok(source.includes("현재 이용 가능한 체험 서버가 없어요"));
  assert.ok(source.includes("No trial servers are available right now."));
});

test("the preset list is used on the web and in native trial mode only", () => {
  const context = { IS_DESKTOP: false, WEB_CLIENT: true, currentPlayMode: null };
  vm.runInNewContext(slice("function usesTrialCatalog()", "function directNativePlay()"), context);
  assert.equal(context.usesTrialCatalog(), true);

  context.WEB_CLIENT = false;
  context.currentPlayMode = "trial";
  assert.equal(context.usesTrialCatalog(), true);

  context.currentPlayMode = "real";
  assert.equal(context.usesTrialCatalog(), false);

  context.IS_DESKTOP = true;
  context.WEB_CLIENT = false;
  context.currentPlayMode = "trial";
  assert.equal(context.usesTrialCatalog(), false);
});
