"""Disposable product tokens -> versioned gateway -> reader BFF/state, catalog and PostgreSQL/PostgreSQL."""
import json
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = 'http://gateway'
ORIGIN = 'https://bookrush.jteodoro.tec.br'


def request(path, token=None, data=None, method='GET'):
    headers = {'Origin': ORIGIN}
    if token: headers['Authorization'] = 'Bearer ' + token
    if data is not None: headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(BASE + path, headers=headers, method=method,
                                 data=json.dumps(data).encode() if data is not None else None)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, None


def token(username):
    body = urllib.parse.urlencode(dict(client_id='search-web', grant_type='password',
                                       username=username, password='disposable-pass')).encode()
    failure = 'not ready'
    for attempt in range(180):
        try:
            with urllib.request.urlopen('http://keycloak:8080/realms/search-tests/protocol/openid-connect/token', data=body, timeout=3) as response:
                return json.load(response)['access_token']
        except urllib.error.HTTPError as error:
            failure = 'HTTP ' + str(error.code)
        except (OSError, KeyError):
            failure = 'connection or token payload unavailable'
        time.sleep(1)
    raise AssertionError('Disposable Keycloak token unavailable: ' + failure)


import sys
BOOK = '11111111-1111-4111-8111-111111111111'
OTHER = '22222222-2222-4222-8222-222222222222'
PATH = '/api/v1/reader/books/seen?bookIds=' + BOOK + ',' + OTHER
ana, bruno = token('ana.reader'), token('bruno.reader')
if '--state-down' in sys.argv:
    assert request(PATH, ana)[0] == 502
    print('Book metadata E2E: unavailable state is reported as 502, not unseen')
    raise SystemExit(0)
for attempt in range(180):
    try:
        if request(PATH, ana)[0] == 200 and request('/api/v1/books/' + BOOK)[0] == 200: break
    except OSError: pass
    time.sleep(1)
else: raise AssertionError('Metadata services readiness failed')
if '--history' in sys.argv:
    recent = request('/api/v1/reader/recent', ana)[1]
    assert len(recent) == 50 and all(item['book_id'] != BOOK for item in recent)
    assert request(PATH, ana)[1]['items'][0]['bookId'] == BOOK
    print('Book metadata E2E: seen status survives recent-history truncation')
    raise SystemExit(0)
if '--restart' in sys.argv:
    assert request(PATH, ana)[1]['items'][0]['bookId'] == BOOK
    assert request(PATH, bruno)[1] == {'items': []}
    print('Book metadata E2E: persisted seen status, isolation and Flyway validation after restart OK')
    raise SystemExit(0)
category = dict(id='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', name='Adventure', scheme='LCSH')
assert request('/api/v1/books/' + BOOK)[1]['categories'] == [category]
other = request('/api/v1/books/' + OTHER)[1]
assert other['categories'] == [] and other['originalLanguage'] == 'pt'
page = request('/api/v1/books?q=Adventure')[1]
assert page['totalItems'] == 1 and page['items'][0]['categories'] == [category]
assert request('/api/v1/books')[1]['totalItems'] == 2
assert request(PATH)[0] == 401
assert request(PATH, 'invalid')[0] == 401
assert request(PATH, ana)[1] == {'items': []}
for path in ['/api/v1/reader/books/seen', '/api/v1/reader/books/seen?bookIds=bad', '/api/v1/reader/books/seen?bookIds=',
             '/api/v1/reader/books/seen?bookIds=' + ','.join([BOOK] * 101)]:
    assert request(path, ana)[0] == 400, path
for method, path in [('GET', PATH), ('POST', '/api/v1/reader/books/' + BOOK + '/opened')]:
    headers = {'Origin': ORIGIN, 'Access-Control-Request-Method': method,
               'Access-Control-Request-Headers': 'authorization,content-type'}
    with urllib.request.urlopen(urllib.request.Request(BASE + path, headers=headers, method='OPTIONS')) as response:
        assert response.status == 200 and response.headers['Access-Control-Allow-Origin'] == ORIGIN
try:
    urllib.request.urlopen(urllib.request.Request(BASE + PATH, headers={**headers, 'Origin': 'https://untrusted.test'}, method='OPTIONS'))
    raise AssertionError('Untrusted origin accepted')
except urllib.error.HTTPError as error: assert error.code == 403
assert request('/api/v1/reader/books/' + BOOK + '/opened', method='POST')[0] == 401
assert request('/api/v1/reader/books/' + BOOK + '/opened', ana, {'subject': 'another-reader'}, 'POST')[0] == 200
first = request(PATH, ana)[1]['items']
assert len(first) == 1 and first[0]['bookId'] == BOOK
assert first[0]['firstSeenAt'] and first[0]['lastSeenAt']
assert request(PATH, bruno)[1] == {'items': []}
assert request('/api/v1/reader/books/' + BOOK + '/progress', ana)[1] == []
assert request('/api/v1/reader/books/' + BOOK + '/opened', ana, method='POST')[0] == 200
second = request(PATH, ana)[1]['items']
assert len(second) == 1 and second[0]['firstSeenAt'] == first[0]['firstSeenAt']
assert second[0]['lastSeenAt'] >= first[0]['lastSeenAt']
assert request('/api/v1/reader/books/seen?bookIds=' + BOOK + ',' + BOOK, ana)[1]['items'] == second
assert request(PATH + '&subject=someone-else', bruno)[1] == {'items': []}
print('Book metadata E2E: real categories, deduplication, unclassified fallback, JWT, ownership, batch validation and CORS OK')
