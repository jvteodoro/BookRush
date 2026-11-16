import assert from "node:assert/strict";
import { test } from "node:test";
import { readFile } from "node:fs/promises";
import { runInNewContext } from "node:vm";
import { URL } from "node:url";
import { setTimeout } from "node:timers";

const legacy = await readFile(
  new URL("../public/pageloop.js", import.meta.url),
  "utf8",
);
const code = legacy.slice(
  legacy.indexOf("function renderReader("),
  legacy.indexOf("function renderLibrary("),
);
const tick = () => new Promise((resolve) => setTimeout(resolve, 0));
const locator = {
  href: "/versions/v1/chapter",
  type: "text/html",
  locations: { progression: 0.4321 },
};

async function setup({
  save,
  list,
  rename,
  remove,
  cancelDialog = false,
} = {}) {
  const nodes = {};
  const makeNode = () => ({
    dataset: {},
    children: [],
    listeners: {},
    isConnected: true,
    setAttribute(name, value) {
      this[name] = value;
    },
    addEventListener(event, callback) {
      this.listeners[event] = callback;
    },
    append(...children) {
      this.children.push(...children);
    },
    replaceChildren() {
      this.children = [];
    },
  });
  for (const id of [
    "readerShell",
    "readerAppearance",
    "readium-reader",
    "bookDetailsBtn",
    "readerPrevious",
    "readerNext",
    "bookmarkBtn",
    "bookmarksBtn",
    "bookmarksPanel",
    "readerBookmarks",
  ])
    nodes[id] = makeNode();
  let closed = 0;
  let destination;
  const messages = [];
  const state = { readerBook: "book-1", route: "reader" };
  const context = {
    state,
    book: () => ({ id: "book-1", title: "Book" }),
    icon: () => "",
    esc: String,
    getMode: () => "app",
    shell() {},
    toast: (text) => messages.push(text),
    document: {
      getElementById: (id) => nodes[id] ?? null,
      querySelectorAll: () => [],
      querySelector: () => null,
      createElement: () => makeNode(),
    },
    window: {
      __BOOKRUSH_BOOKMARK_DIALOGS__: {
        name: async (_title, _initial, submit) => {
          if (cancelDialog) return null;
          await submit("Meu trecho");
          return "Meu trecho";
        },
        remove: async (_name, submit) => {
          if (cancelDialog) return false;
          await submit();
          return true;
        },
      },
      __BOOKRUSH_APPEARANCE__: {
        mount() {},
        panels: () => ({
          close() {
            closed++;
          },
        }),
      },
      __BOOKRUSH_API__: {
        open: async () => {},
        bookmark: save ?? (async () => ({ id: "mark-1" })),
        bookmarks: list ?? (async () => [{ id: "mark-1", locator }]),
        renameBookmark:
          rename ??
          (async () => ({ id: "mark-1", name: "Meu trecho", locator })),
        deleteBookmark: remove ?? (async () => {}),
      },
      __BOOKRUSH_READIUM__: {
        mount: async () => {},
        chapters: async () => [],
        currentLocator: () => locator,
        goToLocator: async (value) => {
          destination = value;
          return true;
        },
      },
    },
  };
  runInNewContext(`${code}\nglobalThis.renderReader=renderReader;`, context);
  context.renderReader();
  await tick();
  return {
    nodes,
    state,
    messages,
    closed: () => closed,
    destination: () => destination,
  };
}

test("reader saves the current locator and resumes a listed bookmark", async () => {
  let payload;
  const ui = await setup({
    save: async (book, value) => {
      payload = { book, value };
    },
  });
  await ui.nodes.bookmarkBtn.listeners.click();
  assert.equal(payload.book, "book-1");
  assert.deepEqual(payload.value, locator);
  assert.deepEqual(ui.messages, ["Posição marcada."]);
  ui.nodes.bookmarksPanel.dataset.open = "true";
  ui.nodes.bookmarksBtn.listeners.click();
  await tick();
  await ui.nodes.readerBookmarks.children[0].children[0].listeners.click();
  assert.deepEqual(ui.destination(), locator);
  assert.equal(ui.closed(), 1);
});

test("save failure has no success confirmation and an in-flight save blocks duplicate clicks", async () => {
  let rejectSave;
  const ui = await setup({
    save: () =>
      new Promise((_, reject) => {
        rejectSave = reject;
      }),
  });
  const pending = ui.nodes.bookmarkBtn.listeners.click();
  assert.equal(ui.nodes.bookmarkBtn.disabled, true);
  rejectSave(new Error("HTTP 502"));
  await pending;
  assert.deepEqual(ui.messages, ["HTTP 502"]);
  assert.equal(ui.nodes.bookmarkBtn.disabled, false);
});

test("a failed list can be retried and legacy marks cannot navigate", async () => {
  let attempts = 0;
  const ui = await setup({
    list: async () => {
      if (!attempts++) throw new Error("HTTP 401");
      return [{ id: "legacy", locator: null }];
    },
  });
  ui.nodes.bookmarksPanel.dataset.open = "true";
  ui.nodes.bookmarksBtn.listeners.click();
  await tick();
  assert.match(ui.nodes.readerBookmarks.textContent, /HTTP 401/);
  ui.nodes.bookmarksBtn.listeners.click();
  await tick();
  assert.equal(ui.nodes.readerBookmarks.children[0].children[0].disabled, true);
});

test("late save completion cannot confirm a different book", async () => {
  let finish;
  const ui = await setup({
    save: () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  });
  const pending = ui.nodes.bookmarkBtn.listeners.click();
  ui.state.readerBook = "book-2";
  finish();
  await pending;
  assert.deepEqual(ui.messages, []);
});

test("cancelling the name dialog does not create a bookmark", async () => {
  let writes = 0;
  const ui = await setup({
    cancelDialog: true,
    save: async () => {
      writes++;
    },
  });
  await ui.nodes.bookmarkBtn.listeners.click();
  assert.equal(writes, 0);
  assert.deepEqual(ui.messages, []);
});

test("pencil renames by ID and trash removes the confirmed bookmark", async () => {
  let removed;
  const ui = await setup({
    remove: async (book, id) => {
      removed = { book, id };
    },
  });
  ui.nodes.bookmarksPanel.dataset.open = "true";
  ui.nodes.bookmarksBtn.listeners.click();
  await tick();
  await ui.nodes.readerBookmarks.children[0].children[1].listeners.click();
  assert.match(
    ui.nodes.readerBookmarks.children[0].children[0].textContent,
    /Meu trecho/,
  );
  await ui.nodes.readerBookmarks.children[0].children[2].listeners.click();
  assert.deepEqual(removed, { book: "book-1", id: "mark-1" });
  assert.equal(ui.nodes.readerBookmarks.children.length, 0);
});
