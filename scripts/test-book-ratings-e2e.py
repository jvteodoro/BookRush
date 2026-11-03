"""Disposable product tokens -> versioned gateway -> social and catalog services/PostgreSQL."""
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


BOOK = '11111111-1111-4111-8111-111111111111'
OTHER = '22222222-2222-4222-8222-222222222222'
PATH = '/api/v1/social/books/' + BOOK + '/ratings'
ana, bruno = token('ana.reader'), token('bruno.reader')
import sys
if '--restart' in sys.argv:
    assert request(PATH, ana)[1] == dict(bookId=BOOK, totalRatings=1, averageRating=5, myRating=5)
    print('Book ratings E2E: persisted rating survives restart')
    raise SystemExit(0)
if '--catalog-down' in sys.argv:
    for method in ['GET', 'PUT', 'DELETE']:
        assert request(PATH, ana, {'stars': 4} if method == 'PUT' else None, method)[0] == 503
    print('Book ratings E2E: unavailable catalog returns 503 without writes')
    raise SystemExit(0)
for attempt in range(90):
    try:
        if request(PATH, ana)[0] == 200: break
    except OSError: pass
    time.sleep(1)
else: raise AssertionError('Social/catalog readiness failed')
for method in ['GET', 'PUT', 'DELETE']:
    assert request(PATH, data={'stars': 5} if method == 'PUT' else None, method=method)[0] == 401
assert request(PATH, 'invalid')[0] == 401
assert request(PATH, ana)[1] == dict(bookId=BOOK, totalRatings=0)
for stars in [0, 6, -1, 1.5, None, 'bad']:
    assert request(PATH, ana, {'stars': stars}, 'PUT')[0] == 400
assert request(PATH, ana, {}, 'PUT')[0] == 400
assert request('/api/v1/social/books/invalid/ratings', ana)[0] == 400
assert request(PATH.replace(BOOK, '33333333-3333-4333-8333-333333333333'), ana, {'stars': 5}, 'PUT')[0] == 404
for method in ['PUT', 'DELETE']:
    headers = {'Origin': ORIGIN, 'Access-Control-Request-Method': method,
               'Access-Control-Request-Headers': 'authorization,content-type'}
    with urllib.request.urlopen(urllib.request.Request(BASE + PATH, headers=headers, method='OPTIONS')) as response:
        assert response.status == 200 and response.headers['Access-Control-Allow-Origin'] == ORIGIN
try:
    urllib.request.urlopen(urllib.request.Request(BASE + PATH, headers={**headers, 'Origin': 'https://untrusted.test'}, method='OPTIONS'))
    raise AssertionError('Untrusted origin accepted')
except urllib.error.HTTPError as error: assert error.code == 403
assert request(PATH, ana, {'stars': 5, 'subject': 'bruno.reader'}, 'PUT')[1] == dict(bookId=BOOK, totalRatings=1, averageRating=5, myRating=5)
assert request(PATH, ana, {'stars': 5}, 'PUT')[1]['totalRatings'] == 1
assert request(PATH, bruno)[1] == dict(bookId=BOOK, totalRatings=1, averageRating=5)
assert request(PATH, bruno, {'stars': 2}, 'PUT')[1] == dict(bookId=BOOK, totalRatings=2, averageRating=3.5, myRating=2)
assert request(PATH, ana, {'stars': 4}, 'PUT')[1] == dict(bookId=BOOK, totalRatings=2, averageRating=3, myRating=4)
assert request(PATH.replace(BOOK, OTHER), ana)[1] == dict(bookId=OTHER, totalRatings=0)
# Concurrent repeats from one subject cannot increase the number of raters.
from concurrent.futures import ThreadPoolExecutor
with ThreadPoolExecutor(max_workers=4) as pool:
    responses = list(pool.map(lambda _: request(PATH, ana, {'stars': 4}, 'PUT'), range(8)))
assert all(status == 200 and data['totalRatings'] == 2 for status, data in responses)
assert request(PATH, ana, method='DELETE')[1] == dict(bookId=BOOK, totalRatings=1, averageRating=2)
assert request(PATH, ana, method='DELETE')[1]['totalRatings'] == 1
assert request(PATH, bruno)[1]['myRating'] == 2
assert request(PATH, bruno, method='DELETE')[1] == dict(bookId=BOOK, totalRatings=0)
# Leave data to verify persistence across the subsequent service restart.
assert request(PATH, ana, {'stars': 5}, 'PUT')[0] == 200
print('Book ratings E2E: real JWT, gateway, catalog, CORS, ownership, validation, aggregation and concurrent upserts OK')
