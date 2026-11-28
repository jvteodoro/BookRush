"""Assertions for the isolated Premium/Family topology. Never print bearer tokens."""
import base64
from datetime import datetime, timedelta, timezone
import subprocess
import json
import os
from pathlib import Path
import re
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from uuid import uuid4

KEYCLOAK = "http://keycloak:8080/realms/"
GATEWAY = "http://gateway:8080"
SUBSCRIPTION = "http://subscription-service:8102"
FAMILY = "/api/v1/family"
SELF = "/api/v1/subscription/me"
CHECKOUT = "/api/v1/subscription/checkout"
SIMULATE = "/api/internal/v1/subscription/billing/mock/simulate"
STATE = Path("/tmp/premium-family-state.json")
USERS = ["holder", "other", *(f"invitee-{i}" for i in range(6))]


def realms(output):
    root = Path("infrastructure/keycloak")
    product = json.loads((root / "subscription/bookrush.fragment.json").read_text())
    product["clientScopes"] += json.loads((root / "family/bookrush.fragment.json").read_text())["clientScopes"]
    product.update(realm="bookrush", enabled=True, accessTokenLifespan=900,
                   sslRequired="none", clients=[
        {"clientId": name, "publicClient": True, "directAccessGrantsEnabled": True,
         "standardFlowEnabled": False, "defaultClientScopes": ["basic", *scopes]}
        for name, scopes in [
            ("fixture-product", ["subscription.read", "family.manage", "family.invites", "supervision.request"]),
            ("fixture-no-scope", []),
            ("fixture-invite-only", ["family.invites"]),
        ]
    ], users=[
        {"username": name, "enabled": True, "firstName": "Fixture", "lastName": "Reader",
         "email": name + "@example.invalid", "emailVerified": True, "credentials": [
            {"type": "password", "value": "fixture-reader-only", "temporary": False}]}
        for name in USERS
    ])
    age = json.loads((root / "family/identity-reader.fragment.json").read_text())
    age["clients"][0]["secret"] = "fixture-age-only"
    for field, value in age.items():
        if isinstance(value, list):
            product.setdefault(field, []).extend(value)
        else:
            product[field] = value
    platform = json.loads((root / "subscription/bookrush-platform.fragment.json").read_text())
    family_workload = json.loads((root / "family/bookrush-platform.fragment.json").read_text())
    platform["clientScopes"] += family_workload["clientScopes"]
    platform["clients"] += family_workload["clients"]
    platform.update(realm="bookrush-platform", enabled=True, sslRequired="none")
    for client, secret in zip(platform["clients"], ("fixture-family-only", "fixture-billing-only", "fixture-membership-only", "fixture-supervision-admin-only", "fixture-reader-state-only"), strict=True):
        client["secret"] = secret
    if os.environ.get("BOOKRUSH_E2E_READER_STATE") == "1":
        reading = json.loads((root / "family/reading-supervision.fragment.json").read_text())
        product["clientScopes"] += reading["bookrush"]["clientScopes"]
        platform["clientScopes"] += reading["bookrush-platform"]["clientScopes"]
        platform["clients"].append({**reading["bookrush-platform"]["client"], "secret": "fixture-reading-only"})
        session = json.loads((root / "family/session.fragment.json").read_text())
        product["clientScopes"].append(session["scope"])
        platform["clients"].append({**session["client"], "secret": "fixture-session-only"})
        for name in ("bookrush-web", "bookrush-mobile"):
            product["clients"].append({**product["clients"][0], "clientId": name,
                "defaultClientScopes": ["basic", "supervision.read", "supervision.request", "session.view", "family.invites"]})
    if os.environ.get("BOOKRUSH_E2E_CONTENT") == "1":
        platform["clientScopes"].append({
            "name": "bookrush.catalog.read", "protocol": "openid-connect",
            "attributes": {"include.in.token.scope": "true"},
            "protocolMappers": [{
                "name": "catalog audience", "protocol": "openid-connect", "protocolMapper": "oidc-audience-mapper",
                "config": {"included.custom.audience": "bookrush-catalog-admin", "access.token.claim": "true"},
            }],
        })
        platform["clients"].append({"clientId": "fixture-catalog-operator", "enabled": True, "protocol": "openid-connect",
             "publicClient": False, "standardFlowEnabled": False, "directAccessGrantsEnabled": False,
             "serviceAccountsEnabled": True, "secret": "fixture-cat-operator-only",
             "defaultClientScopes": ["basic", "bookrush.catalog.read"]})
        distribution = json.loads((root / "family/content-distribution.fragment.json").read_text())
        for client in distribution["clients"]:
            client["secret"] = "fixture-content-only"
            platform["clients"].append(client)
    if os.environ.get("BOOKRUSH_E2E_COMMUNITY") == "1":
        community = json.loads((root / "family/community.fragment.json").read_text())
        platform["clientScopes"].extend(community["clientScopes"])
        for client in community["clients"]:
            client["secret"] = "fixture-community-only"
            platform["clients"].append(client)
        for identifier, scope, audience, group in (
            ("fixture-social-operator", "fixture.moderation", "bookrush-ingestion", True),
            ("fixture-social-non-operator", "fixture.no-moderation", "bookrush-ingestion", False),
            ("fixture-behavior-read", "bookrush.behavior.read", "bookrush-behavior", False),
        ):
            mappers = [{"name": "audience", "protocol": "openid-connect", "protocolMapper": "oidc-audience-mapper",
                        "config": {"included.custom.audience": audience, "access.token.claim": "true"}}]
            if group:
                mappers.append({"name": "operator group", "protocol": "openid-connect",
                    "protocolMapper": "oidc-hardcoded-claim-mapper", "config": {
                        "claim.name": "groups", "claim.value": '["operators"]',
                        "jsonType.label": "JSON", "access.token.claim": "true"}})
            platform["clientScopes"].append({"name": scope, "protocol": "openid-connect",
                "attributes": {"include.in.token.scope": "true"}, "protocolMappers": mappers})
            platform["clients"].append({"clientId": identifier, "enabled": True, "publicClient": False,
                "protocol": "openid-connect", "directAccessGrantsEnabled": False, "standardFlowEnabled": False,
                "serviceAccountsEnabled": True, "secret": "fixture-community-only",
                "defaultClientScopes": ["basic", scope]})
    for client_id in ("fixture-decision-reader", "fixture-decision-unlisted"):
        platform["clients"].append({
            "clientId": client_id, "protocol": "openid-connect", "enabled": True, "publicClient": False,
            "standardFlowEnabled": False, "directAccessGrantsEnabled": False, "serviceAccountsEnabled": True,
            "secret": "fixture-decision-only", "defaultClientScopes": ["basic", "product-access.evaluate"],
        })
    for fixture in (product, platform):
        (Path(output) / (fixture["realm"] + ".json")).write_text(json.dumps(fixture))


def request(url, status=200, *, token=None, payload=None, method=None, headers=None, parse=True):
    headers = dict(headers or {})
    if token:
        headers["Authorization"] = "Bearer " + token
    body = None
    if payload is not None:
        body = json.dumps(payload).encode()
        headers["Content-Type"] = "application/json"
    req = Request(url, data=body, headers=headers, method=method)
    try:
        response = urlopen(req, timeout=10)
    except HTTPError as error:
        response = error
    with response:
        expected = status if isinstance(status, tuple) else (status,)
        if response.status not in expected:
            authentication_error = response.headers.get("WWW-Authenticate", "")
            problem = response.read(2000).decode("utf-8", "replace")
            code = re.search(r'"code":"([A-Z_]+)"', problem)
            detail = f" code={code.group(1)}" if code else ""
            raise AssertionError(f"{req.get_method()} {url}: expected {status}, got {response.status}{detail} {authentication_error}")
        if isinstance(status, tuple):
            return response.status
        data = response.read()
        return json.loads(data) if parse and data else data


def wait(url, status="200"):
    for _ in range(90):
        try:
            request(url, int(status), parse=False)
            return
        except (URLError, AssertionError, TimeoutError):
            time.sleep(1)
    raise SystemExit(f"Readiness timeout: {url}")


def token(user=None, client=None, secret=None, scope=None):
    realm = "bookrush" if user else "bookrush-platform"
    form = dict(client_id=client or "fixture-product")
    if user:
        form.update(grant_type="password", username=user, password="fixture-reader-only")
    else:
        form.update(grant_type="client_credentials", client_secret=secret)
    if scope:
        form["scope"] = scope
    try:
        with urlopen(KEYCLOAK + realm + "/protocol/openid-connect/token", urlencode(form).encode(), timeout=10) as response:
            return json.load(response)["access_token"]
    except HTTPError as error:
        detail = json.load(error).get("error")
        raise AssertionError(f"Keycloak token grant failed for fixture client {form['client_id']}: {error.code} {detail}") from None


def claims(value):
    body = value.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(body + "=" * (-len(body) % 4)))


def identity(pass_number):
    product = claims(token("holder"))
    family = claims(token(client="bookrush-family-s2s", secret="fixture-family-only"))
    billing = claims(token(client="bookrush-billing-s2s", secret="fixture-billing-only"))
    assert product["iss"] == KEYCLOAK + "bookrush" and product["sub"]
    assert {"bookrush-family", "bookrush-subscription"} <= set(product["aud"])
    assert {"family.manage", "family.invites", "subscription.read"} <= set(product["scope"].split())
    for workload, scope, client in [
        (family, "entitlements.evaluate", "bookrush-family-s2s"),
        (billing, "subscription.transitions.write", "bookrush-billing-s2s"),
    ]:
        assert workload["iss"] == KEYCLOAK + "bookrush-platform" and workload["sub"]
        assert "bookrush-subscription" in workload["aud"]
        assert scope in workload["scope"].split() and workload["azp"] == client
    membership = claims(token(client="bookrush-product-access-s2s", secret="fixture-membership-only"))
    assert membership["iss"] == KEYCLOAK + "bookrush-platform" and membership["sub"]
    assert "bookrush-family" in membership["aud"] and "family.membership.read" in membership["scope"].split()
    snapshot = {name: claims(value)["sub"] for name, value in [("holder", token("holder"))]}
    if pass_number == "1":
        STATE.write_text(json.dumps(snapshot))
    else:
        assert snapshot == json.loads(STATE.read_text())
    print(f"Keycloak reconcile {pass_number}: identity, issuer, audiences and scopes OK")



def dates():
    rows = []
    for name, birthdate in [("holder", "1985-01-01"), ("other", "1990-01-01"), ("invitee-0", "2012-01-01"), ("invitee-2", "2013-01-01")]:
        identity = claims(token(name))
        rows.append(json.dumps({"issuer": identity["iss"], "subject": identity["sub"], "birthdate": birthdate}))
    subprocess.run([sys.executable, "/tmp/reconcile-age-profile.py", "fill"],
                   input="\n".join(rows), text=True, check=True)

def write(url, value, *, payload=None, status=200, key=None):
    return request(url, status, token=value, method="POST", payload=payload,
                   headers={"Idempotency-Key": key or str(uuid4())})


COMMUNITY_E2E = os.environ.get("BOOKRUSH_E2E_COMMUNITY") == "1"
CONTENT_E2E = os.environ.get("BOOKRUSH_E2E_CONTENT") == "1"
READER_E2E = os.environ.get("BOOKRUSH_E2E_READER_STATE") == "1"
COMMENTS_E2E = COMMUNITY_E2E and READER_E2E
READER = "http://reader-state-service:8094/api/v1/reader"
BFF = "http://reader-bff-service:8101/api/v1/reader"
LEGACY_BOOK = "8f21709f-9880-4689-9605-f941d355cc67"
EVENTS = "http://event-sink:8888/events"


def event_sink():
    from http.server import BaseHTTPRequestHandler, HTTPServer

    events = set()

    class Sink(BaseHTTPRequestHandler):
        def do_POST(self):
            if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
                chunks = []
                while size := int(self.rfile.readline().split(b";", 1)[0], 16):
                    chunks.append(self.rfile.read(size))
                    self.rfile.readline()
                self.rfile.readline()
                raw = b"".join(chunks)
            else:
                raw = self.rfile.read(int(self.headers["Content-Length"]))
            body = json.loads(raw)
            keys = {event["eventKey"] for event in body["events"]}
            previously_received = keys <= events
            events.update(keys)
            self.send_response(204 if previously_received else 503)
            self.end_headers()

        def do_GET(self):
            body = json.dumps({"count": len(events)}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            pass

    HTTPServer(("0.0.0.0", 8888), Sink).serve_forever()


def reader_restricted(minor):
    book = str(uuid4())
    record = f"/books/{book}"
    marker = str(uuid4())
    before = request(EVENTS)["count"]
    checks = {
        "GET": ["/library", "/recent", "/streak", "/plant", f"/books/seen?bookIds={book}",
                record + "/progress", record + "/bookmarks"],
        "POST": [record + "/bookmarks", record + "/opened", "/sessions", f"/sessions/{marker}/end", "/plant"],
        "PUT": [f"/library/{book}", record + "/progress", record + f"/bookmarks/{marker}"],
        "DELETE": [f"/library/{book}", record + f"/bookmarks/{marker}"],
    }
    assert sum(map(len, checks.values())) == 17
    for method, paths in checks.items():
        for path in paths:
            payload = {
                ("POST", record + "/bookmarks"): {"positionCodepoint": 4},
                ("POST", "/plant"): {"eventId": marker, "activeSeconds": 30,
                                    "endedAt": datetime.now(timezone.utc).isoformat()},
                ("POST", "/sessions"): {"bookId": book},
                ("POST", f"/sessions/{marker}/end"): {"activeSeconds": 60},
                ("PUT", record + "/progress"): {"positionCodepoint": 4, "percent": 10},
                ("PUT", record + f"/bookmarks/{marker}"): {"name": "Fixture"},
            }.get((method, path))
            result = request(READER + path, 403, token=minor, method=method, payload=payload)
            assert result["code"] == "CALLER_FORBIDDEN"
    request(READER + "/library", 403, token=minor, method="HEAD", parse=False)
    request(BFF + "/library", 403, token=minor, parse=False)
    request(READER + "/library", 401, parse=False)
    request(BFF + "/library", 401, parse=False)
    assert request(EVENTS)["count"] == before


def reader_free_adult(adult, other):
    before = request(EVENTS)["count"]
    assert request(BFF + "/library", token=adult)[0]["book_id"] == LEGACY_BOOK
    assert request(BFF + "/library", token=other,
                   headers={"X-Subject": claims(adult)["sub"], "X-Owner": claims(adult)["sub"]}) == []
    book = str(uuid4())
    path = f"/books/{book}"
    request(BFF + f"/library/{book}", token=adult, method="PUT")
    write(BFF + path + "/opened", adult)
    assert request(BFF + "/books/seen?bookIds=" + book, token=adult)["items"][0]["bookId"] == book
    request(BFF + path + "/progress", token=adult, method="PUT", payload={"positionCodepoint": 20, "percent": 10})
    assert request(BFF + path + "/progress", token=adult)[0]["position_codepoint"] == 20
    bookmark = write(BFF + path + "/bookmarks", adult, payload={"positionCodepoint": 20})
    assert write(BFF + path + "/bookmarks", adult, payload={"positionCodepoint": 20})["id"] == bookmark["id"]
    request(BFF + path + f"/bookmarks/{bookmark['id']}", 404, token=other, method="DELETE", parse=False)
    session = write(BFF + "/sessions", adult, payload={"bookId": book})
    ending = BFF + f"/sessions/{session['id']}/end"
    write(ending, other, status=404, payload={"activeSeconds": 0})
    write(ending, adult, payload={"activeSeconds": 0})
    write(ending, adult, status=404, payload={"activeSeconds": 0})
    after = request(EVENTS)["count"]
    assert after == before + 4, (before, after)
    result = request("http://reader-state-untrusted:8094/api/v1/reader/library", 503, token=adult)
    assert result["code"] == "DECISION_UNAVAILABLE"
    assert request(BFF + "/streak", token=adult)["currentDays"] >= 0
    assert request(READER + "/plant", token=adult)["streakDays"] >= 0


CONTENT = "/api/v1/content/books"
CATALOG = "http://catalog-service:8080"
CONTENT_DIRECT = "http://book-content-service:8093"
EPUB = "8f000000-0000-4000-8000-000000000001"
TEXT = "8f000000-0000-4000-8000-000000000002"
ASSET = "8f000000-0000-4000-8000-000000000021"
CHAPTER = "8f000000-0000-4000-8000-000000000041"
LEGACY_DOWNLOAD = f"/api/books/{EPUB}/assets/{ASSET}/download-url"
CONTENT_MARKER = b"synthetic-content"


def content_fixture(output):
    from zipfile import ZipFile
    with ZipFile(Path(output) / "fixture.epub", "w") as archive:
        archive.writestr("META-INF/container.xml",
            '<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/content.opf"/></rootfiles></container>')
        archive.writestr("OPS/content.opf", '<package xmlns="http://www.idpf.org/2007/opf"><manifest><item id="c" href="chapter.xhtml" '
            'media-type="application/xhtml+xml"/><item id="style" href="style.css" media-type="text/css"/>'
            '</manifest><spine><itemref idref="c"/></spine></package>')
        archive.writestr("OPS/chapter.xhtml", '<html xmlns="http://www.w3.org/1999/xhtml"><head>'
            '<link rel="stylesheet" href="style.css"/></head><body>synthetic-content</body></html>')
        archive.writestr("OPS/style.css", "body { color: black; }")
    (Path(output) / "fixture.txt").write_bytes(CONTENT_MARKER)


def content_denied(actor, status):
    queries = [
        (CONTENT_DIRECT + f"{CONTENT}/{EPUB}/publication.json", "GET"),
        (GATEWAY + f"{CONTENT}/{TEXT}/chapters/{CHAPTER}/content", "GET"),
        (GATEWAY + f"/api/v1/reader/books/{EPUB}/resources/OPS/chapter.xhtml", "GET"),
        (CATALOG + f"/api/v1/books/{EPUB}/reader-assets/{ASSET}/content", "HEAD"),
        (GATEWAY + LEGACY_DOWNLOAD, "GET"),
    ]
    from urllib.request import HTTPRedirectHandler, build_opener

    class NoRedirect(HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None

    opener = build_opener(NoRedirect)
    headers = {"Range": "bytes=0-3"}
    if actor:
        headers["Authorization"] = "Bearer " + actor
    for url, method in queries:
        try:
            response = opener.open(Request(url, method=method, headers=headers), timeout=10)
        except HTTPError as rejected:
            response = rejected
        with response:
            assert response.status == status, (method, url, status, response.status)
            assert not response.headers.get("Location")
            body = response.read()
            assert all(marker not in body for marker in (CONTENT_MARKER, b"readingOrder", b"X-Amz-Signature", b'"url"'))


def content_operator_and_old_link():
    from urllib.parse import urlsplit

    operator = token(client="fixture-catalog-operator", secret="fixture-cat-operator-only")
    request(CATALOG + "/api/internal/v1/catalog/books", token=operator)
    admin_base = f"/api/admin/books/{EPUB}/assets/{ASSET}"
    host = {"Host": "bookrush.jteodoro.tec.br"}
    protected = request(GATEWAY + admin_base + "/download-url", token=operator, headers=host)
    assert protected["url"] == admin_base + "/content"
    assert request(GATEWAY + protected["url"], token=operator, headers=host, parse=False).startswith(b"PK")
    old = request("http://legacy-catalog:8080" + LEGACY_DOWNLOAD)
    old_target = urlsplit(old["url"])
    assert old_target.netloc == "seaweedfs-s3-bookrush.jteodoro.tec.br" and old_target.query
    try:
        response = urlopen(Request(GATEWAY + old_target.path + "?" + old_target.query,
                                   headers={"Host": old_target.netloc}), timeout=10)
    except HTTPError as rejected:
        assert rejected.code in {403, 418}
        assert not rejected.read().startswith(b"PK")
    else:
        response.close()
        raise AssertionError("old signed link was served through the protected storage gateway")


def content_allowed(actor):
    from io import BytesIO
    from zipfile import ZipFile

    catalog_assets = f"/api/v1/books/{EPUB}/reader-assets"
    assert any(asset["id"] == ASSET for asset in request(CATALOG + catalog_assets, token=actor))
    archive = request(CATALOG + catalog_assets + f"/{ASSET}/content", token=actor, parse=False)
    with ZipFile(BytesIO(archive)) as epub:
        assert "OPS/chapter.xhtml" in epub.namelist()
    manifest = request(GATEWAY + f"/api/v1/reader/books/{EPUB}/publication.json", token=actor)
    assert "bookrushArtifactKey" not in manifest
    assert manifest["readingOrder"], "EPUB manifest contains no spine entries"
    assert CONTENT_MARKER in request(GATEWAY + manifest["readingOrder"][0]["href"], token=actor, parse=False)
    assert b"color" in request(GATEWAY + f"{CONTENT}/{EPUB}/resources/OPS/style.css", token=actor, parse=False)
    fallback = request(CONTENT_DIRECT + f"{CONTENT}/{TEXT}/publication.json", token=actor)
    assert CONTENT_MARKER in request(GATEWAY + fallback["readingOrder"][0]["href"], token=actor, parse=False)
    link = request(GATEWAY + LEGACY_DOWNLOAD, token=actor)
    assert link["url"] == f"/api/v1/books/{EPUB}/reader-assets/{ASSET}/content"
    assert link.get("expiresAt") is None
    url = GATEWAY + link["url"]
    assert request(url, token=actor, method="HEAD", parse=False) == b""
    probe = Request(url, headers={"Authorization": "Bearer " + actor, "Range": "bytes=0-3"})
    with urlopen(probe, timeout=10) as response:
        assert response.status == 200 and response.read().startswith(b"PK")
        assert "no-store" in response.headers["Cache-Control"]
        assert response.headers.get("Location") is None
    request(CATALOG + f"/api/v1/books/{TEXT}/reader-assets/{ASSET}/content", 404, token=actor, parse=False)
    request(CATALOG + link["url"], 401, headers={"X-Content-Service-Token": "legacy-fixture"}, parse=False)
    for host, path in (
        ("seaweedfs-s3-bookrush.jteodoro.tec.br", "/books-public/fixture.epub"),
        ("seaweedfs-bookrush.jteodoro.tec.br", "/buckets/books-public/fixture.epub"),
    ):
        request(GATEWAY + path, (403, 418), headers={"Host": host}, parse=False)
    request("http://seaweedfs:8333/books-public/fixture.epub", 403, parse=False)


SOCIAL = "http://social-service:8096/api/v1/social"
PROFILE = "http://reader-profile-service:8095/api/v1/profile"
BEHAVIOR = "http://behavior-service:8097/api/v1/behavior"


def event_batch(key=None, **identity):
    return {"events": [{"eventKey": key or str(uuid4()), "eventType": "BOOK_OPEN", "bookId": EPUB,
                        "occurredAt": datetime.now(timezone.utc).isoformat(), **identity}]}


def community_denied(minor, status=403):
    comment = str(uuid4())
    target = f"?user={uuid4()}"
    social_routes = [
        ("PUT", f"/books/{EPUB}/like", None), ("DELETE", f"/books/{EPUB}/like", None),
        ("PUT", "/follow" + target, None), ("GET", "/follow" + target, None),
        ("DELETE", "/follow" + target, None), ("GET", "/followers" + target, None),
        ("POST", "/reports", {"bookId": EPUB, "reason": "fixture"}),
        ("POST", f"/books/{EPUB}/share", {"channel": "fixture"}),
        ("GET", f"/books/{EPUB}/likes", None), ("GET", f"/books/{EPUB}/comments", None),
        ("POST", f"/books/{EPUB}/comments", {"body": "denied fixture"}),
        ("DELETE", f"/comments/{comment}", None), ("GET", f"/books/{EPUB}/ratings", None),
        ("PUT", f"/books/{EPUB}/ratings", {"stars": 1}), ("DELETE", f"/books/{EPUB}/ratings", None),
    ]
    for method, path, payload in social_routes:
        request(SOCIAL + path, status, token=minor, method=method, payload=payload, parse=False)
    for method, path, payload in [
        ("GET", "", None), ("GET", "/search?q=fixture", None), ("GET", "/public?subject=fixture", None),
        ("GET", "/privacy", None), ("PUT", "", {"displayName": "denied profile"}),
        ("PUT", "/privacy", {key: "PUBLIC" for key in
            ("profileVisibility", "activityVisibility", "libraryVisibility", "recentVisibility", "socialGraphVisibility")}),
    ]:
        request(PROFILE + path, status, token=minor, method=method, payload=payload, parse=False)
    request(BEHAVIOR + "/aggregates/me", status, token=minor, parse=False)
    request(GATEWAY + "/api/v1/profile", status, token=minor, parse=False)
    request(GATEWAY + f"/api/v1/social/books/{EPUB}/likes", status, token=minor, parse=False)
    request(BEHAVIOR + "/events", status, token=minor, method="POST", payload=event_batch(), parse=False)


def community_free_adult(adult, minor):
    adult_sub = claims(adult)["sub"]
    minor_sub = claims(minor)["sub"]
    request(PROFILE, token=adult, method="PUT", payload={"displayName": "Fixture adult"})
    assert request(PROFILE + "/public?subject=" + adult_sub, token=adult)["displayName"] == "Fixture adult"
    assert request(PROFILE + "/search?q=fixture", token=adult)["totalItems"] == 1
    request(PROFILE + "/public?subject=" + minor_sub, 404, token=adult, parse=False)
    profile = request(PROFILE + "?subject=" + minor_sub, token=adult, method="PUT",
                      payload={"displayName": "Fixture adult", "subject": minor_sub})
    assert adult_sub in profile["subject_key"] and minor_sub not in profile["subject_key"]
    privacy = request(PROFILE + "/privacy", token=adult)
    request(PROFILE + "/privacy", token=adult, method="PUT", payload={**privacy, "profileVisibility": "PRIVATE"})
    assert request(PROFILE + "/search?q=fixture", token=adult)["totalItems"] == 0
    request(PROFILE + "/public?subject=" + adult_sub, 404, token=adult, parse=False)
    request(PROFILE + "/privacy", token=adult, method="PUT", payload=privacy)
    comment = write(SOCIAL + f"/books/{EPUB}/comments", adult, payload={"body": "fixture adult comment"})
    request(SOCIAL + f"/books/{EPUB}/ratings", token=adult, method="PUT", payload={"stars": 4})
    request(SOCIAL + "/moderation/reports", 401, token=adult, parse=False)
    operator = token(client="fixture-social-operator", secret="fixture-community-only")
    non_operator = token(client="fixture-social-non-operator", secret="fixture-community-only")
    request(SOCIAL + "/moderation/reports", 403, token=non_operator, parse=False)
    request(SOCIAL + "/moderation/reports", token=operator)
    request(SOCIAL + f"/books/{EPUB}/likes", 401, token=operator, parse=False)
    request(PROFILE, 401, token=token("other", "fixture-no-scope"), parse=False)
    request(BEHAVIOR + "/aggregates/me", 401, token=operator, parse=False)
    request(SOCIAL + f"/moderation/comments/{comment['id']}", token=operator,
            method="PUT", payload={"visibility": "HIDDEN", "reasonCode": "FIXTURE"})
    request(BEHAVIOR + "/events", 400, token=adult, method="POST",
            payload=event_batch(issuer="fixture", subject=minor_sub), parse=False)
    request(BEHAVIOR + "/events", 400, token=adult, method="POST",
            payload=event_batch(payload={"subject": minor_sub}), parse=False)
    event = event_batch("community-fixture-adult")
    assert write(BEHAVIOR + "/events", adult, status=202, payload=event)["accepted"] == 1
    assert write(BEHAVIOR + "/events", adult, status=202, payload=event)["duplicates"] == 1
    request(BEHAVIOR + "/aggregates/me", token=adult)
    request(BEHAVIOR + "/events/count", 401, token=adult, parse=False)
    request(BEHAVIOR + "/aggregates/rebuild", 401, token=adult, method="POST", parse=False)
    operations = token(client="bookrush-behavior-operations-s2s", secret="fixture-community-only")
    reader = token(client="fixture-behavior-read", secret="fixture-community-only")
    request(BEHAVIOR + "/events/count", 403, token=reader, parse=False)
    request(BEHAVIOR + "/events/count", token=operations)
    write(BEHAVIOR + "/aggregates/rebuild", operations)
    request("http://behavior-service:8097/api/internal/v1/recommendation/trending", token=reader)


SESSION = GATEWAY + "/api/v1/session"

def bootstrap(value, state, reason=None, allowed=False):
    result = request(SESSION, token=value)
    assert set(result) <= {"username", "accessState", "reasonCode", "capabilities", "validUntil"}
    assert result["accessState"] == state and result.get("reasonCode") == reason
    caps = set(result["capabilities"])
    assert caps == (set() if state == "SUSPENDED" else {"SESSION_VIEW", "INVITE_SELF"} | ({"PRODUCT_USE"} if allowed else set()))
    assert datetime.fromisoformat(result["validUntil"]) > datetime.now(timezone.utc)
    return result

def refresh_bootstrap(username, client, expected):
    endpoint = KEYCLOAK + "bookrush/protocol/openid-connect/token"
    def request_token(fields):
        with urlopen(endpoint, urlencode({"client_id": client, **fields}).encode(), timeout=10) as response:
            return json.load(response)
    original = request_token({"grant_type": "password", "username": username,
                              "password": "fixture-reader-only", "scope": "openid"})
    refreshed = request_token({"grant_type": "refresh_token", "refresh_token": original["refresh_token"]})
    assert claims(original["access_token"])["sub"] == claims(refreshed["access_token"])["sub"]
    bootstrap(refreshed["access_token"], expected, allowed=True)

def set_fixture_enabled(value, enabled):
    admin_form = urlencode({"grant_type": "password", "client_id": "admin-cli",
        "username": "fixture-admin", "password": "fixture-admin-only"})
    with urlopen(KEYCLOAK + "master/protocol/openid-connect/token", admin_form.encode(), timeout=10) as response:
        admin = json.load(response)["access_token"]
    url = "http://keycloak:8080/admin/realms/bookrush/users/" + claims(value)["sub"]
    request(url, 204, token=admin, method="PUT", payload={"enabled": enabled}, parse=False)

def commercial():
    holder, other = token("holder"), token("other")
    billing = token(client="bookrush-billing-s2s", secret="fixture-billing-only")
    owner = {"issuer": claims(holder)["iss"], "subject": claims(holder)["sub"]}
    if READER_E2E:
        adult_session = token("other", client="bookrush-web")
        juvenile_session = token("invitee-0", client="bookrush-mobile")
        request(SESSION, 401, parse=False)
        request(SESSION, 401, token="invalid", parse=False)
        request(SESSION, 401, token=billing, parse=False)
        bootstrap(adult_session, "ADULT_ELIGIBLE", allowed=True)
        for client in ("bookrush-web", "bookrush-mobile"):
            refresh_bootstrap("other", client, "ADULT_ELIGIBLE")
        bootstrap(juvenile_session, "MINOR_FAMILY_INACTIVE", "ACCOUNT_RESTRICTED")
        bootstrap(token("invitee-1", client="bookrush-web"), "AGE_PROFILE_INCOMPLETE", "ACCOUNT_RESTRICTED")
        temporarily_suspended = token("invitee-3", client="bookrush-web")
        set_fixture_enabled(temporarily_suspended, False)
        bootstrap(temporarily_suspended, "SUSPENDED", "ACCOUNT_SUSPENDED")
        set_fixture_enabled(temporarily_suspended, True)
    request(GATEWAY + SELF, 401, parse=False)
    request(GATEWAY + SELF, 401, token=billing, parse=False)
    request(GATEWAY + SELF, 401, token=token("other", "fixture-no-scope"), parse=False)
    assert request(GATEWAY + SELF, token=holder)["state"] == "FREE"
    request(GATEWAY + FAMILY, 403, method="POST", token=holder, parse=False)
    request(GATEWAY + FAMILY + "/me", 403, token=token("other", "fixture-invite-only"), parse=False)
    request(GATEWAY + CHECKOUT, method="OPTIONS", headers={
        "Origin": "http://browser.fixture", "Access-Control-Request-Method": "POST",
        "Access-Control-Request-Headers": "Authorization, Idempotency-Key",
    }, parse=False)
    key = str(uuid4())
    checkout = write(GATEWAY + CHECKOUT, holder, key=key, payload={"planId": "premium-family"})
    assert checkout["simulation"] and not checkout["realPayment"] and checkout["state"] == "APPLIED"
    assert checkout == write(GATEWAY + CHECKOUT, holder, key=key)
    rights = request(GATEWAY + SELF + "/entitlements", token=holder)
    assert rights["publishBooks"] and rights["premiumFamily"] and rights["additionalSeats"] == 5
    assert request(GATEWAY + SELF, token=other)["state"] == "FREE"
    household = write(GATEWAY + FAMILY, holder, status=201)["id"]
    endpoint = GATEWAY + f"{FAMILY}/{household}/invitations"
    codes = [write(GATEWAY + FAMILY + "/invite-address", token(user))["code"] for user in USERS[2:]]
    for code in codes[:5]:
        write(endpoint, holder, payload={"recipientCode": code}, status=201)
    write(endpoint, holder, payload={"recipientCode": codes[5]}, status=409)
    assert request(GATEWAY + FAMILY + "/me", token=holder)["occupiedOrReserved"] == 5
    assert len(request(GATEWAY + FAMILY + "/invitations/me", token=token(USERS[2]))) == 1
    assert request(GATEWAY + FAMILY + "/invitations/me", token=other) == []
    invitee = token(USERS[2])
    invitation = request(GATEWAY + FAMILY + "/invitations/me", token=invitee)[0]
    accept = GATEWAY + FAMILY + "/invitations/" + invitation["id"] + "/accept"
    request(accept, 404, method="POST", token=other, parse=False)
    if READER_E2E:
        assert request(GATEWAY + FAMILY + "/invitations/me", token=juvenile_session)[0]["id"] == invitation["id"]
    member = write(accept, juvenile_session if READER_E2E else invitee)
    assert write(accept, invitee) == member and request(GATEWAY + FAMILY + "/me", token=holder)["occupiedOrReserved"] == 5
    view = request(GATEWAY + FAMILY + "/me", token=invitee)
    assert view["role"] == "MEMBER" and view["members"] == [] and view["id"] == member["householdId"]
    assert request(GATEWAY + FAMILY + "/me", token=holder)["role"] == "OWNER"
    supervision = GATEWAY + FAMILY + "/" + household + "/members/" + member["membershipId"] + "/supervision"
    request(supervision, 404, method="POST", token=other, headers={"Idempotency-Key": str(uuid4())}, parse=False)
    if READER_E2E:
        bootstrap(juvenile_session, "MINOR_AWAITING_AUTHORIZATION", "SUPERVISION_REQUIRED")
    lookup = "/api/internal/v1/family/membership/evaluate"
    technical = token(client="bookrush-product-access-s2s", secret="fixture-membership-only")
    query = {"issuer": claims(invitee)["iss"], "subject": claims(invitee)["sub"]}
    facts = write("http://family-service:8103" + lookup, technical, payload=query)
    assert facts["state"] == "ACTIVE" and not facts["delegationEnabled"]
    assert "canPublish" not in facts and "age" not in facts
    request(GATEWAY + lookup, (401, 404) if CONTENT_E2E or COMMUNITY_E2E else 404, method="POST", token=technical, parse=False)
    request(endpoint, 404, token=other, parse=False)
    write(endpoint, other, payload={"recipientCode": codes[5]}, status=404)
    product_lookup = "/api/internal/v1/family/product-access/evaluate"
    scope_lookup = "/api/internal/v1/family/supervision/evaluate"
    internal = "http://family-service:8103"
    guardian = {"issuer": claims(other)["iss"], "subject": claims(other)["sub"]}
    canonical = internal + "/api/internal/v1/product-access/decisions"
    consumer = "http://decision-consumer:8104"
    reader = token(client="fixture-decision-reader", secret="fixture-decision-only")
    unlisted = token(client="fixture-decision-unlisted", secret="fixture-decision-only")
    decision_request = {
        "actor": query, "authentication": {"clientId": "fixture-product", "audience": "bookrush-family",
                                            "scopes": ["family.invites"]},
        "action": "PRODUCT_USE", "resource": {"type": "BOOK"}, "correlationId": "canonical-e2e",
    }
    assert write(canonical, reader, payload=decision_request)["reason"] == "SUPERVISION_REQUIRED"
    write(canonical, unlisted, status=403, payload=decision_request)
    write(canonical, technical, status=403, payload=decision_request)
    write(canonical, reader, status=403, payload={**decision_request, "action": "PUBLISH_WRITE",
                                                   "resource": {"type": "SUBMISSION", "owner": query}})
    assert write(canonical, reader, payload=decision_request)["correlationId"] == "canonical-e2e"
    request(GATEWAY + "/api/internal/v1/product-access/decisions", status=(401, 404) if CONTENT_E2E or COMMUNITY_E2E else 404, token=reader,
            method="POST", payload=decision_request, parse=False)
    request(consumer + "/read", 403, token=invitee, parse=False)
    request(consumer + "/read", 200, token=other, parse=False)
    request(consumer + "/session", 200, token=invitee, parse=False)
    if COMMUNITY_E2E:
        community_denied(invitee)
        community_free_adult(other, invitee)
    if CONTENT_E2E:
        content_denied(invitee, 403)
        content_allowed(other)
        content_operator_and_old_link()
        content_denied(None, 401)
        content_denied(technical, 401)
        content_denied(token("other", "fixture-no-scope"), 401)
    if READER_E2E:
        reader_restricted(invitee)
        reader_free_adult(other, holder)
        request(BFF + "/library", 403, token=token("invitee-1"), parse=False)
    assert not write(internal + product_lookup, technical, payload=query)["allowed"]
    parent_scope = {"guardian": guardian, "minor": query, "scope": "COMMENTS"}
    assert not write(internal + scope_lookup, technical, payload=parent_scope)["allowed"]
    assert not write(internal + scope_lookup, technical, payload={
        **parent_scope, "guardian": owner,
    })["allowed"]
    grant = write(GATEWAY + FAMILY + "/supervision", other, status=201,
                  payload={"minor": query, "scopes": ["PRODUCT_ACCESS", "COMMENTS"]})
    assert grant["state"] == "REQUESTED"
    admin = token(client="bookrush-supervision-admin-s2s", secret="fixture-supervision-admin-only")
    admin_url = internal + "/api/internal/v1/family/supervision/admin/" + grant["id"]
    pair = {"guardian": guardian, "minor": query}
    start = datetime.now(timezone.utc) + timedelta(seconds=4)
    approved = write(admin_url, admin, payload={**pair, "action": "APPROVE", "expectedVersion": 0,
        "scopes": ["PRODUCT_ACCESS", "COMMENTS"], "effectiveFrom": start.isoformat(),
        "effectiveUntil": (start + timedelta(days=1)).isoformat(), "verificationRef": str(uuid4())})
    assert approved["state"] == "ACTIVE"
    grant_version = approved["version"]
    time.sleep(max(0, (start - datetime.now(timezone.utc)).total_seconds()))
    if READER_E2E:
        bootstrap(juvenile_session, "MINOR_AUTHORIZED", allowed=True)
    assert write(internal + product_lookup, technical, payload=query)["allowed"]
    assert write(internal + scope_lookup, technical, payload=parent_scope)["allowed"]
    decision = write(canonical, reader, payload=decision_request)
    assert decision["allowed"] and decision["factVersions"]["supervision"] == "1"
    request(consumer + "/read", 200, token=invitee, parse=False)
    if COMMUNITY_E2E:
        request(PROFILE, token=invitee, method="PUT", payload={"displayName": "Fixture hidden minor"})
        request(PROFILE + "/public?subject=" + query["subject"], 404, token=other, parse=False)
        assert request(PROFILE + "/search?q=fixture", token=other)["totalItems"] == 1
        request(SOCIAL + f"/books/{EPUB}/likes", token=invitee)
        write(BEHAVIOR + "/events", invitee, status=202, payload=event_batch("community-fixture-minor"))
    if CONTENT_E2E:
        content_allowed(invitee)
    if READER_E2E:
        assert request(BFF + "/library", token=invitee) == []
        assert request(BFF + "/recent", token=invitee) == []
        assert request(READER + "/plant", token=invitee)["todaySeconds"] == 0
        reading_book = str(uuid4())
        opening = str(uuid4())
        write(BFF + f"/books/{reading_book}/opened", invitee, key=opening)
        opened_events = request(EVENTS)["count"]
        write(BFF + f"/books/{reading_book}/opened", invitee, key=opening)
        assert request(EVENTS)["count"] == opened_events
        assert request(BFF + "/recent", token=invitee)[0]["book_id"] == reading_book
        assert request(BFF + "/books/seen?bookIds=" + reading_book, token=invitee)["items"][0]["bookId"] == reading_book
        minor_session = write(BFF + "/sessions", invitee, payload={"bookId": reading_book})["id"]
    request(consumer + "/comments/" + query["subject"], 200, token=other, parse=False)
    if READER_E2E or CONTENT_E2E or COMMUNITY_E2E:
        reduced = write(admin_url, admin, payload={**pair, "action": "REDUCE", "expectedVersion": grant_version,
                                                 "scopes": ["COMMENTS"]})
        if READER_E2E:
            bootstrap(juvenile_session, "MINOR_AWAITING_AUTHORIZATION", "SUPERVISION_REQUIRED")
            request(BFF + "/library", 403, token=invitee, parse=False)
        if CONTENT_E2E:
            content_denied(invitee, 403)
        if COMMUNITY_E2E:
            community_denied(invitee)
        restart = datetime.now(timezone.utc) + timedelta(seconds=3)
        restored = write(admin_url, admin, payload={**pair, "action": "APPROVE", "expectedVersion": reduced["version"],
            "scopes": ["PRODUCT_ACCESS", "COMMENTS"], "effectiveFrom": restart.isoformat(),
            "effectiveUntil": (restart + timedelta(days=1)).isoformat(), "verificationRef": str(uuid4())})
        grant_version = restored["version"]
        time.sleep(max(0, (restart - datetime.now(timezone.utc)).total_seconds()))
        if READER_E2E:
            request(BFF + "/library", 200, token=invitee, parse=False)
        if CONTENT_E2E:
            content_allowed(invitee)
        if COMMUNITY_E2E:
            request(PROFILE, token=invitee)
    if COMMENTS_E2E:
        path = GATEWAY + comments_path(grant["id"])
        assert request(path, token=token("other", client="bookrush-web"))["items"] == []
        request(path, 404, token=token("holder", client="bookrush-web"), parse=False)
    write(SUBSCRIPTION + SIMULATE, billing, payload={"owner": owner, "scenario": "REFUND"})
    if READER_E2E:
        bootstrap(juvenile_session, "MINOR_FAMILY_INACTIVE", "ACCOUNT_RESTRICTED")
        print("Bootstrap: adulto, menor sem vínculo, convite, autorização, revogação, estorno, cadastro e suspensão")
    assert not write(internal + product_lookup, technical, payload=query)["allowed"]
    if CONTENT_E2E:
        content_denied(invitee, 403)
        print("Conteúdo: manifest, capítulo, EPUB, links autenticados, HEAD/Range, storage e revogação")
    request(consumer + "/read", 403, token=invitee, parse=False)
    if READER_E2E:
        request(BFF + "/library", 403, token=invitee, parse=False)
        request(BFF + f"/books/{reading_book}/opened", 403, token=invitee, method="POST", parse=False)
        before = request(EVENTS)["count"]
        write(BFF + f"/sessions/{minor_session}/end", invitee, status=403, payload={"activeSeconds": 60})
        assert request(EVENTS)["count"] == before
        print("Reader state: 17 gates, JWT preservado, dados legados, isolamento, eventos e estorno pelo BFF")
    request(consumer + "/comments/" + query["subject"], 200, token=other, parse=False)
    assert write(internal + scope_lookup, technical, payload=parent_scope)["allowed"]
    write(admin_url, admin, payload={**pair, "action": "REVOKE", "expectedVersion": grant_version})
    assert not write(internal + scope_lookup, technical, payload=parent_scope)["allowed"]
    request(consumer + "/comments/" + query["subject"], 403, token=other, parse=False)
    print("Avaliador v1: consumidor Spring real, negação, concessão, estorno e revogação com os mesmos JWTs")
    request(GATEWAY + scope_lookup, (401, 404) if CONTENT_E2E or COMMUNITY_E2E else 404, method="POST", token=technical, parse=False)
    if COMMUNITY_E2E:
        community_denied(invitee)
        print("Community: 23 rotas, realms, escopos, perfil adulto, privacidade e revogação com JWT existente")
    print("Supervisão: Keycloak atual, guardião fora da família, estorno comercial e revogação legal OK")
    assert request(GATEWAY + SELF, token=holder)["state"] == "CANCELED"
    write(endpoint, holder, payload={"recipientCode": codes[5]}, status=403)
    assert write("http://family-service:8103" + lookup, technical, payload=query).get("holderPremiumUntil") is None
    request(GATEWAY + SIMULATE, (401, 404) if CONTENT_E2E or COMMUNITY_E2E else 404, token=billing, method="POST", parse=False)
    subjects = [claims(value)["sub"] for value in (holder, other, billing)]
    for service, port in [("subscription-service", 8102), ("family-service", 8103)]:
        metrics = request(f"http://{service}:{port}/actuator/prometheus", parse=False).decode()
        assert "jvm_memory_used_bytes" in metrics
        for line in metrics.splitlines():
            if line.startswith("bookrush_"):
                assert not re.search(r"\b(email|username|subject|issuer|user_id)=", line)
                assert not any(subject in line for subject in subjects)
                assert not any(user in line for user in USERS)
    STATE.write_text(json.dumps({"holder": holder, "invitee": invitee, "household": household, "spare": codes[5]}))
    print("Gateway: FREE, checkout replay, entitlements, cinco reservas, isolamento e estorno com JWT antigo OK")


def production():
    state = json.loads(STATE.read_text())
    holder = state["holder"]
    base = "http://subscription-production:8102"
    assert request(base + SELF, token=holder)["state"] == "CANCELED"
    denied = (403, 404)
    checkout_status = request(base + CHECKOUT, denied, token=holder, method="POST",
                              headers={"Idempotency-Key": str(uuid4())})
    writer = token(client="bookrush-billing-s2s", secret="fixture-billing-only")
    simulate_status = write(base + SIMULATE, writer, status=denied, payload={
        "owner": {"issuer": claims(holder)["iss"], "subject": claims(holder)["sub"]},
        "scenario": "DECLINE",
    })
    assert request(base + SELF, token=holder)["state"] == "CANCELED"
    print(f"Perfil production com opt-in forçado: checkout {checkout_status}, simulação {simulate_status}")


def outage():
    state = json.loads(STATE.read_text())
    endpoint = GATEWAY + f"{FAMILY}/{state['household']}/invitations"
    result = write(endpoint, state["holder"], payload={"recipientCode": state["spare"]}, status=503)
    assert result["code"] == "DECISION_UNAVAILABLE"
    assert request("http://decision-consumer:8104/read", 503, token=state["invitee"])["code"] == "DECISION_UNAVAILABLE"
    if COMMUNITY_E2E:
        community_denied(state["invitee"], 503)
    if CONTENT_E2E:
        request(GATEWAY + f"/api/v1/reader/books/{EPUB}/publication.json", 503, token=state["invitee"], parse=False)
    if READER_E2E:
        request(BFF + "/library", 503, token=state["invitee"], parse=False)
        request(BFF + f"/books/{uuid4()}/opened", 503, token=state["invitee"], method="POST", parse=False)
    assert request(GATEWAY + FAMILY + "/me", token=state["holder"])["occupiedOrReserved"] == 5
    metrics = request("http://family-service:8103/actuator/prometheus", parse=False).decode()
    failure_counter = [line for line in metrics.splitlines()
                       if line.startswith("bookrush_family_subscription_failures_total")]
    assert failure_counter
    for line in failure_counter:
        assert set(re.findall(r"\b([a-z_]+)=", line)) <= {"environment", "service"}
    print("Gateway com subscription parado: 503, nenhuma nova reserva e contador sem labels de identidade")


def evaluator_down():
    adult = token("other")
    if CONTENT_E2E:
        content_denied(adult, 503)
    if COMMUNITY_E2E:
        community_denied(adult, 503)
        print("Community: 503 via acesso direto e gateway com o avaliador parado")


def reading_prepare():
    minor = {key: claims(token("invitee-0"))[claim] for key, claim in [("issuer", "iss"), ("subject", "sub")]}
    start = datetime.now(timezone.utc) + timedelta(seconds=5)
    admin = token(client="bookrush-supervision-admin-s2s", secret="fixture-supervision-admin-only")
    result = {}
    for name, scope in [("other", "BOOKS_ACCESSED"), ("holder", "READING_ACTIVITY")]:
        human = token(name, client="bookrush-web")
        identity = claims(human)
        pair = {"minor": minor, "guardian": {"issuer": identity["iss"], "subject": identity["sub"]}}
        receipt = write(GATEWAY + FAMILY + "/supervision", human, status=201,
                        payload={"minor": minor, "scopes": [scope]})
        path = "http://family-service:8103/api/internal/v1/family/supervision/admin/" + receipt["id"]
        write(path, admin, payload={**pair, "action": "APPROVE", "expectedVersion": 0, "scopes": [scope],
            "effectiveFrom": start.isoformat(), "effectiveUntil": (start + timedelta(days=1)).isoformat(),
            "verificationRef": str(uuid4())})
        result[name] = {"id": receipt["id"], "human": human, "pair": pair, "path": path}
    result["start"] = start.isoformat()
    STATE.with_suffix(".reading").write_text(json.dumps(result))
    time.sleep(max(0, (start - datetime.now(timezone.utc)).total_seconds()) + 2)
    for name, scope in [("other", "BOOKS_ACCESSED"), ("holder", "READING_ACTIVITY")]:
        who = result[name]
        url = GATEWAY + f"/api/v1/reader/supervision/{who['id']}/history?scope={scope}"
        assert request(url, token=who["human"])["items"] == []
    print(start.isoformat())


def supervised_reading():
    state = json.loads(STATE.with_suffix(".reading").read_text())
    guardian, holder = state["other"], state["holder"]
    route = "/api/v1/reader/supervision/" + guardian["id"] + "/history"
    book_query = route + "?scope=BOOKS_ACCESSED&limit=1"
    request(GATEWAY + book_query, 401, parse=False)
    origin = "http://localhost:18080"
    with urlopen(Request(GATEWAY + book_query, method="OPTIONS", headers={
        "Origin": origin, "Access-Control-Request-Method": "GET",
        "Access-Control-Request-Headers": "authorization",
    }), timeout=10) as preflight:
        assert preflight.headers.get("Access-Control-Allow-Origin") == origin
    request(GATEWAY + book_query, 404, token=holder["human"], parse=False)
    request(GATEWAY + route + "?scope=READING_ACTIVITY", 403, token=guardian["human"], parse=False)
    request(GATEWAY + route.replace(guardian["id"], str(uuid4())) + "?scope=BOOKS_ACCESSED",
            404, token=guardian["human"], parse=False)
    first = request(GATEWAY + book_query, token=guardian["human"])
    assert len(first["items"]) == 1 and first["nextCursor"]
    second = request(GATEWAY + book_query + "&cursor=" + first["nextCursor"], token=guardian["human"])
    assert len(second["items"]) == 1 and not second.get("nextCursor")
    assert first["items"][0]["bookId"] != second["items"][0]["bookId"]
    assert all(item["accessedAt"] >= state["start"][:19] for item in first["items"] + second["items"])
    request(GATEWAY + book_query + "&cursor=" + str(uuid4()), 400, token=guardian["human"], parse=False)
    activity = GATEWAY + route.replace(guardian["id"], holder["id"]) + "?scope=READING_ACTIVITY"
    activities = request(activity, token=holder["human"])["items"]
    assert len(activities) == 1 and activities[0]["minutes"] == 1 and activities[0]["sessions"] == 1
    request(activity.replace("READING_ACTIVITY", "BOOKS_ACCESSED"), 403, token=holder["human"], parse=False)
    direct = "http://reader-state-service:8094" + book_query.replace("/api/v1", "/api/internal/v1")
    request(direct, 401, token=guardian["human"], headers={"X-BookRush-Workload": "invalid"}, parse=False)
    service = token(client="bookrush-reader-bff-supervision-s2s", secret="fixture-reading-only")
    assert request(direct, token=guardian["human"], headers={"X-BookRush-Workload": service})["items"]
    request(GATEWAY + book_query.replace("/api/v1", "/api/internal/v1"),
            (401, 404) if COMMUNITY_E2E or CONTENT_E2E else 404, token=guardian["human"], parse=False)
    admin = token(client="bookrush-supervision-admin-s2s", secret="fixture-supervision-admin-only")
    write(guardian["path"], admin, payload={**guardian["pair"], "action": "REVOKE", "expectedVersion": 1})
    request(GATEWAY + book_query + "&cursor=" + first["nextCursor"], 403, token=guardian["human"], parse=False)
    assert request(activity, token=holder["human"])["items"]
    minor_token = token("invitee-0", client="bookrush-mobile")
    subprocess.run([sys.executable, "/tmp/reconcile-age-profile.py", "correct"], check=True, text=True,
                   input=json.dumps({**holder["pair"]["minor"], "birthdate": "2000-01-01",
                                     "expectedBirthdate": "2012-01-01"}) + "\n")
    bootstrap(minor_token, "ADULT_ELIGIBLE", allowed=True)
    request(activity, 403, token=holder["human"], parse=False)
    subprocess.run([sys.executable, "/tmp/reconcile-age-profile.py", "correct"], check=True, text=True,
                   input=json.dumps({**holder["pair"]["minor"], "birthdate": "2012-01-01",
                                     "expectedBirthdate": "2000-01-01"}) + "\n")
    print("Histórico supervisionado: escopos independentes, limite temporal, cursor, estorno e revogação com JWT antigo")


def reading_unavailable():
    state = json.loads(STATE.with_suffix(".reading").read_text())["holder"]
    request(GATEWAY + "/api/v1/reader/supervision/" + state["id"] + "/history?scope=READING_ACTIVITY",
            503, token=state["human"], parse=False)


def bootstrap_unavailable():
    request(SESSION, 503, token=token("other", client="bookrush-web"), parse=False)


def comments_path(member):
    return f"/api/v1/reader/supervision/{member}/comments"


def comments_prepare():
    guardian = token("other", client="bookrush-web")
    guardian_claims = claims(guardian)
    minor = claims(token("invitee-0"))
    pair = {"guardian": {"issuer": guardian_claims["iss"], "subject": guardian_claims["sub"]},
            "minor": {"issuer": minor["iss"], "subject": minor["sub"]}}
    receipt = write(GATEWAY + FAMILY + "/supervision", guardian, status=201,
                    payload={"minor": pair["minor"], "scopes": ["BOOKS_ACCESSED", "COMMENTS"]})
    url = GATEWAY + comments_path(receipt["id"])
    admin = token(client="bookrush-supervision-admin-s2s", secret="fixture-supervision-admin-only")
    admin_url = "http://family-service:8103/api/internal/v1/family/supervision/admin/" + receipt["id"]

    def approve(scopes, version):
        start = datetime.now(timezone.utc) + timedelta(seconds=3)
        write(admin_url, admin, payload={**pair, "action": "APPROVE", "expectedVersion": version, "scopes": scopes,
              "effectiveFrom": start.isoformat(), "effectiveUntil": (start + timedelta(days=1)).isoformat(),
              "verificationRef": str(uuid4())})
        time.sleep(max(0, (start - datetime.now(timezone.utc)).total_seconds()) + 2)
        return start

    approve(["BOOKS_ACCESSED"], 0)
    request(url, 403, token=guardian, parse=False)
    start = approve(["COMMENTS"], 1)
    assert request(url, token=guardian)["items"] == []
    STATE.with_suffix(".comments").write_text(json.dumps(
        {"id": receipt["id"], "guardian": guardian, "pair": pair, "admin_url": admin_url}))
    print(start.isoformat())


def supervised_comments():
    state = json.loads(STATE.with_suffix(".comments").read_text())
    guardian = state["guardian"]
    path = comments_path(state["id"])
    url = GATEWAY + path
    operator = token(client="fixture-social-operator", secret="fixture-community-only")
    workload = token(client="bookrush-reader-bff-supervision-s2s", secret="fixture-reading-only",
                     scope="supervision.comments.read")
    request(url, 401, parse=False)
    request(url, 401, token=operator, parse=False)
    request(url, 401, token=token("other"), parse=False)
    request(url, 404, token=token("holder", client="bookrush-web"), parse=False)
    request(url + "?limit=51", 400, token=guardian, parse=False)
    assert request(url + "?limit=50", token=guardian,
                   headers={"X-Correlation-ID": "comments-audit-fixture"})["scope"] == "COMMENTS"
    page = request(url + "?limit=50", token=guardian)
    assert len(page["items"]) == 50 and page["nextCursor"]
    assert [item["body"] for item in page["items"]] == [f"visible-{i}" for i in range(51, 1, -1)]
    assert all(set(item) == {"id", "bookId", "body", "createdAt"} for item in page["items"])
    assert request(url + "?limit=50&cursor=" + page["nextCursor"], token=guardian)["items"][0]["body"] == "visible-1"
    request(url + "?cursor=" + str(uuid4()), 400, token=guardian, parse=False)
    direct = "http://social-service:8096" + path.replace("/api/v1/reader", "/api/internal/v1/social")
    request(direct, 401, token=guardian, parse=False)
    request(direct, 401, token=guardian, headers={"X-BookRush-Workload": "invalid"}, parse=False)
    request(direct, 401, token=guardian, headers={"X-BookRush-Workload": operator}, parse=False)
    history_only = token(client="bookrush-reader-bff-supervision-s2s", secret="fixture-reading-only")
    request(direct, 401, token=guardian, headers={"X-BookRush-Workload": history_only}, parse=False)
    assert len(request(direct, token=guardian, headers={"X-BookRush-Workload": workload})["items"]) == 25
    request(GATEWAY + path.replace("/api/v1/reader", "/api/internal/v1/social"), (401, 404), token=guardian, parse=False)
    remaining = request(url + "?limit=50&cursor=" + page["nextCursor"], token=guardian)["items"][0]
    request(SOCIAL + "/moderation/comments/" + remaining["id"], token=operator,
            method="PUT", payload={"visibility": "HIDDEN", "reasonCode": "FIXTURE"})
    assert request(url + "?limit=50&cursor=" + page["nextCursor"], token=guardian)["items"] == []
    baseline = state["pair"]["minor"]
    for birthdate, expected in [("2000-01-01", "2012-01-01"), ("2012-01-01", "2000-01-01")]:
        subprocess.run([sys.executable, "/tmp/reconcile-age-profile.py", "correct"], check=True, text=True,
                       input=json.dumps({**baseline, "birthdate": birthdate, "expectedBirthdate": expected}) + "\n")
        if birthdate == "2000-01-01":
            request(url, 403, token=guardian, parse=False)
    admin = token(client="bookrush-supervision-admin-s2s", secret="fixture-supervision-admin-only")
    write(state["admin_url"], admin, payload={**state["pair"], "action": "REVOKE", "expectedVersion": 2})
    request(url + "?cursor=" + page["nextCursor"], 403, token=guardian, parse=False)
    print("Comentários supervisionados: filtros, 50 itens, moderação entre páginas e revogação com o mesmo JWT")


def comments_unavailable():
    state = json.loads(STATE.with_suffix(".comments").read_text())
    request(GATEWAY + comments_path(state["id"]), 503, token=state["guardian"], parse=False)



if __name__ == "__main__":
    {"realms": realms, "bootstrap-unavailable": bootstrap_unavailable, "comments-prepare": comments_prepare, "supervised-comments": supervised_comments,
     "comments-unavailable": comments_unavailable, "reading-prepare": reading_prepare, "supervised-reading": supervised_reading,
     "reading-unavailable": reading_unavailable, "content-fixture": content_fixture,
     "content-evaluator-down": evaluator_down, "event-sink": event_sink, "wait": wait, "identity": identity, "commercial": commercial,
     "subject": lambda user: print(claims(token(user))["sub"]), "production": production, "outage": outage, "dates": dates}[sys.argv[1]](*sys.argv[2:])
