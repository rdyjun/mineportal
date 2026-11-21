const test = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const path = require("node:path");

const source = fs.readFileSync(path.join(__dirname, "index.html"), "utf8");
const downloads = fs.readFileSync(path.join(__dirname, "downloads.js"), "utf8");

function slice(start, end) {
  const from = source.indexOf(start);
  const to = source.indexOf(end, from + start.length);
  assert.ok(from >= 0 && to > from, start + " must be found");
  return source.slice(from, to);
}

const emoji = /[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}\u{27F0}-\u{27FF}]/u;

test("the visible client has no emoji and the web header keeps only Try it", () => {
  assert.equal(emoji.test(source), false);
  assert.equal(emoji.test(downloads), false);
  assert.match(source, /html\.web-client #tabServersBtn \{ display: none !important; \}/);
  assert.match(source, /padding: 6px 12px/);
  assert.equal(source.includes('fetch("/api/servers"'), false);
  assert.equal(source.includes("/api/servers/recent"), false);
  assert.match(source, /id="tabServersBtn"/);
});

test("a desktop server list is stored on this computer", () => {
  const store = {};
  const context = {
    IS_DESKTOP: true,
    WEB_CLIENT: false,
    currentPlayMode: "real",
    localStorage: {
      getItem: (key) => (Object.prototype.hasOwnProperty.call(store, key) ? store[key] : null),
      setItem: (key, value) => {
        store[key] = value;
      },
    },
    servers: [],
  };
  vm.runInNewContext(slice("function usesTrialCatalog()", "const translations"), context);
  assert.equal(context.usesTrialCatalog(), false);
  const parsed = context.parseServerAddress("play.example.com:25566");
  assert.equal(parsed.host, "play.example.com");
  assert.equal(parsed.port, 25566);
  context.servers = [
    { id: "local-1", host: "Play.Example.com", port: 25565, version: "auto", lastConnectedAt: 5 },
  ];
  context.writeLocalServers();
  const read = context.readLocalServers();
  assert.equal(read.length, 1);
  assert.equal(read[0].host, "Play.Example.com");
  assert.equal(read[0].connected, false);
  assert.equal(read[0].status, "idle");
  assert.equal(JSON.parse(store["mineportal.servers"])[0].status, undefined);
});
