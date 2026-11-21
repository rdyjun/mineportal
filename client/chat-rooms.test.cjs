const test = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const path = require("node:path");

const source = fs.readFileSync(path.join(__dirname, "index.html"), "utf8");
const start = source.indexOf("// CHAT_ROOM_MODEL_START");
const end = source.indexOf("// CHAT_ROOM_MODEL_END");
assert.ok(start >= 0 && end > start, "chat room model must stay extractable");
const context = {};
vm.runInNewContext(source.slice(start, end), context);

function room(extra) {
  return { lines: [], unread: 0, stickToBottom: true, scrollTop: 0, ...extra };
}

test("a background line is kept and counted unread without following", () => {
  const line = { text: "hello", timestamp: 1, system: false };
  const result = context.pushChatLine(room(), line, false, 500);
  assert.equal(result.follow, false);
  assert.equal(result.showJump, false);
  assert.equal(result.room.unread, 1);
  assert.deepEqual(result.room.lines, [line]);
});

test("a visible room at the bottom follows and stays read", () => {
  const result = context.pushChatLine(
    room(),
    { text: "hi", timestamp: 2, system: false },
    true,
    500,
  );
  assert.equal(result.follow, true);
  assert.equal(result.showJump, false);
  assert.equal(result.room.unread, 0);
});

test("a visible room scrolled up keeps its place and asks for a jump", () => {
  const result = context.pushChatLine(
    room({ stickToBottom: false, scrollTop: 20, unread: 1 }),
    { text: "later", timestamp: 3, system: false },
    true,
    500,
  );
  assert.equal(result.follow, false);
  assert.equal(result.showJump, true);
  assert.equal(result.room.unread, 2);
  assert.equal(result.room.scrollTop, 20);
});

test("the room buffer drops lines past the cap", () => {
  let current = room();
  for (let i = 0; i < 12; i++) {
    current = context.pushChatLine(
      current,
      { text: String(i), timestamp: i, system: false },
      false,
      10,
    ).room;
  }
  assert.equal(current.lines.length, 10);
  assert.equal(current.lines[0].text, "2");
  assert.equal(current.unread, 12);
});

test("scrolling back to the bottom marks the room read", () => {
  const next = context.noteRoomScroll(room({ unread: 4, stickToBottom: false }), 80, 128, 40, 48);
  assert.equal(next.stickToBottom, true);
  assert.equal(next.unread, 0);
  const away = context.noteRoomScroll(next, 0, 200, 40, 48);
  assert.equal(away.stickToBottom, false);
  assert.equal(away.unread, 0);
});

test("sidebar selection fills the focused pane and focuses a server already on screen", () => {
  const slots = [{ serverId: "a" }, { serverId: null }, { serverId: "b" }, { serverId: null }];
  const moved = context.selectServerPane(slots, 1, "c");
  assert.equal(moved.focused, 1);
  assert.deepEqual(
    moved.slots.map((slot) => slot.serverId),
    ["a", "c", "b", null],
  );
  const existing = context.selectServerPane(moved.slots, 1, "a");
  assert.equal(existing.focused, 0);
  assert.equal(existing.slots[0].serverId, "a");
  assert.equal(existing.slots[1].serverId, "c");
});

test("dropping a server moves it into that pane", () => {
  const slots = [{ serverId: "a" }, { serverId: "b" }];
  const dropped = context.dropServerOnPane(slots, 1, "a");
  assert.equal(dropped.focused, 1);
  assert.deepEqual(
    dropped.slots.map((slot) => slot.serverId),
    [null, "a"],
  );
});

test("split layout restores hidden panes and the phone width keeps a single pane", () => {
  const memory = ["a", "b", "c", "d"];
  assert.deepEqual(
    [...context.slotsForLayout(memory, 4)].map((slot) => slot.serverId),
    ["a", "b", "c", "d"],
  );
  assert.deepEqual(
    [...context.slotsForLayout(memory, 1)].map((slot) => slot.serverId),
    ["a"],
  );
  assert.equal(context.layoutCount(4, true), 1);
  assert.equal(context.layoutCount(4, false), 4);
  assert.equal(context.layoutCount(2, false), 2);
});
