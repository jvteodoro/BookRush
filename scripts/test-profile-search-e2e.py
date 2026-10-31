"""Disposable product tokens -> versioned gateway -> profile service/PostgreSQL."""
import json
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = 'http://gateway'


def request(path, token=None, data=None, method='GET'):
    headers = {'Origin': 'http://gateway'}
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


def search(q, bearer, page=0, size=20):
    return request('/api/v1/profile/search?' + urllib.parse.urlencode(dict(q=q, page=page, size=size)), bearer)


ana, bruno = token('ana.reader'), token('bruno.reader')
for attempt in range(90):
    try:
        if request('/api/v1/profile', ana)[0] == 200: break
    except OSError: pass
    time.sleep(1)
else: raise AssertionError('Profile service unavailable through gateway')
assert request('/api/v1/profile', bruno)[0] == 200
assert search('ana', None)[0] == 401
assert search('ana', 'invalid-token')[0] == 401
status, page = search('  @ANA.READER  ', bruno)
assert status == 200 and page['totalItems'] == 1, (status, page)
user = page['items'][0]
assert user['username'] == 'ana.reader' and 'Ana' in user['displayName'], user
assert set(user) <= {'subject', 'displayName', 'username'}
assert request('/api/v1/profile/public?' + urllib.parse.urlencode({'subject': user['subject']}), bruno)[1]['displayName'] == user['displayName']
assert search('', ana)[1]['totalItems'] == 0
assert search('nobody', ana)[1]['totalItems'] == 0
assert search('reader', ana, size=1)[1]['totalPages'] == 2
first = search('reader', ana, size=1)[1]['items'][0]['subject']
second = search('reader', ana, page=1, size=1)[1]['items'][0]['subject']
assert first != second
for page_value, size_value in [(-1, 20), (10001, 20), (0, 0), (0, 51)]:
    assert search('ana', ana, page_value, size_value)[0] == 400
assert search('a' * 101, ana)[0] == 400
# Literal wildcards, display names, repeated identity sync, and SQL-injection input.
assert request('/api/v1/profile', ana, {'displayName': 'Custom Ana_%', 'bio': 'private evidence', 'isPublic': True}, 'PUT')[0] == 200
assert request('/api/v1/profile', ana)[1]['display_name'] == 'Custom Ana_%'
assert search('_%', bruno)[1]['totalItems'] == 1
assert search("' OR 1=1 --", bruno)[1]['totalItems'] == 0
privacy = dict(profileVisibility='PRIVATE', activityVisibility='PUBLIC', libraryVisibility='PRIVATE',
               recentVisibility='PRIVATE', socialGraphVisibility='PUBLIC')
for visibility in ['PRIVATE', 'FOLLOWERS']:
    privacy['profileVisibility'] = visibility
    assert request('/api/v1/profile/privacy', ana, privacy, 'PUT')[0] == 200
    assert search('ana', bruno)[1]['totalItems'] == 0
    # Even an old client setting is_public=true must not override granular privacy.
    request('/api/v1/profile', ana, {'displayName': 'Custom Ana_%', 'isPublic': True}, 'PUT')
    assert search('ana', bruno)[1]['totalItems'] == 0
privacy['profileVisibility'] = 'PUBLIC'
request('/api/v1/profile/privacy', ana, privacy, 'PUT')
assert search('ana', bruno)[1]['totalItems'] == 1
request('/api/v1/profile', ana, {'displayName': 'Custom Ana_%', 'isPublic': False}, 'PUT')
assert search('ana', bruno)[1]['totalItems'] == 0
print('Profile search E2E: real JWT, gateway, privacy, identity sync, literal search and pagination OK')
