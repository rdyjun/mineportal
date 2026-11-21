const test = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const path = require("node:path");

const source = fs.readFileSync(path.join(__dirname, "index.html"), "utf8");
const start = source.indexOf("// CONNECTION_KIND_START");
const end = source.indexOf("// CONNECTION_KIND_END");
assert.ok(start >= 0 && end > start);
const context = {};
vm.runInNewContext(source.slice(start, end), context);

test("a server that was never joined has no connection badge", () => {
  assert.equal(context.connectionKind({ status: "idle", phase: "idle", connected: false }), null);
  assert.equal(
    context.connectionKind({ status: "", phase: "idle", connected: false, online: true }),
    null,
  );
  assert.equal(context.connectionKind(null), null);
});

test("joining, retrying, and being in game are different connection states", () => {
  assert.equal(
    context.connectionKind({ status: "connecting", phase: "active", connected: false }),
    "connecting",
  );
  assert.equal(
    context.connectionKind({ status: "reconnecting", phase: "active", connected: false }),
    "reconnecting",
  );
  assert.equal(
    context.connectionKind({ status: "connected", phase: "active", connected: true }),
    "connected",
  );
  assert.equal(
    context.connectionKind({ status: "idle", phase: "active", connected: true }),
    "connected",
  );
});

test("a dropped session and a failed login stay distinct from server availability", () => {
  assert.equal(
    context.connectionKind({
      status: "disconnected: left",
      phase: "closed",
      connected: false,
      online: true,
    }),
    "disconnected",
  );
  assert.equal(
    context.connectionKind({
      status: "connection failed: banned",
      phase: "closed",
      connected: false,
      online: false,
    }),
    "error",
  );
  assert.equal(
    context.connectionKind({
      status: "account session expired",
      phase: "closed",
      connected: false,
    }),
    "disconnected",
  );
});
