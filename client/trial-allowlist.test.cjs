const test = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const path = require("node:path");

const source = fs.readFileSync(path.join(__dirname, "index.html"), "utf8");
const reason =
  "웹 체험 모드에서는 허용된 서버에만 접속할 수 있어요. 다른 서버는 데스크톱 앱으로 접속해 주세요.";

function between(start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from + start.length);
  assert.ok(from >= 0 && to > from, "trial denial UI code must be found");
  return source.slice(from, to);
}

test("the add-server form can show the Korean trial denial", () => {
  assert.match(source, /id="addServerError"/);
  assert.ok(source.includes(reason));
});

test("a denied trial server shows the Korean reason from the server", () => {
  const context = {
    t: (key) => (key === "trialServerNotAllowed" ? reason : key),
  };
  vm.runInNewContext(
    between("function trialDeniedReason(body)", "function openAddServerModal()"),
    context,
  );

  assert.equal(
    context.trialDeniedReason({
      code: "trial-server-not-allowed",
      error: reason,
    }),
    reason,
  );
  assert.equal(context.trialDeniedReason({ code: "trial-server-not-allowed" }), reason);
  assert.equal(context.trialDeniedReason({ code: "not-logged-in", error: "login" }), null);
  assert.equal(context.trialDeniedReason(null), null);
});
