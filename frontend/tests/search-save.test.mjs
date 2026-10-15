import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';
import { URL } from 'node:url';

const source = await readFile(new URL('../public/pageloop.js', import.meta.url), 'utf8');
const searchCode = source.slice(source.indexOf('const pendingSearchSaves'), source.indexOf('async function openBook('));

function setup(save, initiallySaved = false) {
  const button = { dataset: { save: 'book-id' }, disabled: false };
  const state = { route: 'search', saved: { 'book-id': initiallySaved }, search: '', searchFilter: 'Todos' };
  const messages = [];
  const context = {
    state, BOOKS: [], window: { __BOOKRUSH_API__: { save } },
    document: { getElementById: () => ({}), querySelectorAll: selector => selector === '[data-save]' ? [button] : [] },
    shell: () => {}, save: () => {}, toast: text => messages.push(text), icon: () => '', esc: String,
  };
  runInNewContext(`${searchCode}\nglobalThis.__renderSearch = renderSearch;`, context);
  context.__renderSearch();
  return { button, state, messages };
}

test('search saves and removes through the API before updating its visible state', async () => {
  const calls = [];
  let finish;
  const { button, state } = setup((id, enabled) => {
    calls.push([id, enabled]);
    return new Promise(resolve => { finish = resolve; });
  });
  const saving = button.onclick();
  assert.equal(state.saved['book-id'], false);
  assert.equal(button.disabled, true);
  await button.onclick();
  assert.equal(calls.length, 1, 'duplicate click must not issue another mutation');
  finish(); await saving;
  assert.equal(state.saved['book-id'], true);
  const removing = button.onclick();
  assert.equal(state.saved['book-id'], true);
  finish(); await removing;
  assert.equal(state.saved['book-id'], false);
  assert.deepEqual(calls, [['book-id', true], ['book-id', false]]);
});

test('a rejected library mutation preserves the previous search state and reports the failure', async () => {
  const { button, state, messages } = setup(async () => { throw new Error('API indisponível'); }, true);
  await button.onclick();
  assert.equal(state.saved['book-id'], true);
  assert.equal(button.disabled, false);
  assert.deepEqual(messages, ['API indisponível']);
});
