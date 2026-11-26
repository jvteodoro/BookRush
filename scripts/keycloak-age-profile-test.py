"""Discardable Keycloak age tests. No real accounts, token output or DOB output."""
import base64
import hashlib
from html.parser import HTMLParser
from http.cookiejar import CookieJar
import json
from pathlib import Path
import secrets
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlencode, urlparse
from urllib.request import HTTPCookieProcessor, HTTPRedirectHandler, Request, build_opener, urlopen

BASE = "http://keycloak:8080"
ISSUER = BASE + "/realms/bookrush"
OIDC = ISSUER + "/protocol/openid-connect"
ADMIN = BASE + "/admin/realms/bookrush/"
PASSWORD = "synthetic-password-only"
STATE = Path("/tmp/age-fixture-state.json")


def fixture(target):
    source = json.loads(Path("infrastructure/keycloak/bookrush-realm.json").read_text())
    clients = [c for c in source["clients"] if c["clientId"] in ("bookrush-web", "bookrush-mobile")]
    Path(target).write_text(json.dumps({
        "realm": "bookrush", "enabled": True, "registrationAllowed": True, "sslRequired": "none",
        "clients": clients,
        "users": [{
            "username": "legacy", "enabled": True, "email": "legacy@example.invalid",
            "emailVerified": True, "firstName": "Synthetic", "lastName": "Legacy",
            "clientRoles": {"account": ["manage-account", "view-profile"]},
            "credentials": [{"type": "password", "value": PASSWORD, "temporary": False}],
        }],
    }))


class StopRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if urlparse(newurl).netloc == urlparse(BASE).netloc:
            return super().redirect_request(req, fp, code, msg, headers, newurl)
        return None


class Form(HTMLParser):
    def __init__(self, form_id, data):
        super().__init__()
        self.form_id = form_id
        self.action = None
        self._inside = False
        self.fields = set()
        self.feed(data.decode())

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "form":
            self._inside = attrs.get("id") == self.form_id
            if self._inside:
                self.action = attrs.get("action")
        elif self._inside and tag == "input":
            self.fields.add(attrs.get("name"))

    def handle_endtag(self, tag):
        if tag == "form":
            self._inside = False


def req(url, expected=200, *, obj=None, form=None, verb=None, token=None, opener=urlopen):
    headers = {}
    data = None
    if token:
        headers["Authorization"] = "Bearer " + token
    if obj is not None:
        data = json.dumps(obj).encode()
        headers["Content-Type"] = "application/json"
    if form is not None:
        data = urlencode(form).encode()
    request = Request(url, data=data, headers=headers, method=verb)
    try:
        response = opener(request, timeout=10)
    except HTTPError as error:
        response = error
    with response:
        if response.status != expected:
            location = urlparse(response.headers.get("Location", ""))
            oauth_error = parse_qs(location.query).get("error", [])
            raise AssertionError(f"{request.get_method()} {urlparse(url).path}: "
                                 f"{response.status} != {expected}; redirect {location.path}, OAuth error {oauth_error}")
        return response.headers, response.read()


def admin_token():
    _, data = req(BASE + "/realms/master/protocol/openid-connect/token", form={
        "grant_type": "password", "client_id": "admin-cli", "username": "fixture-admin",
        "password": "fixture-only-password"})
    return json.loads(data)["access_token"]


def admin(path, token):
    return json.loads(req(ADMIN + path, token=token)[1])


def decode(token):
    value = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(value + "=" * (-len(value) % 4)))


def browser(username, client="bookrush-web", *, signup=False, birthdate=None, valid=True):
    opener = build_opener(HTTPCookieProcessor(CookieJar()), StopRedirect()).open
    redirect = "bookrush://callback" if client == "bookrush-mobile" else "http://localhost:18080/oidc/callback"
    verifier = secrets.token_urlsafe(48)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
    base = OIDC + ("/registrations" if signup else "/auth")
    _, page = req(base + "?" + urlencode({
        "client_id": client, "redirect_uri": redirect, "response_type": "code",
        "scope": "openid profile", "code_challenge": challenge, "code_challenge_method": "S256",
        "state": "fixture-state",
    }), opener=opener)
    form = Form("kc-register-form" if signup else "kc-form-login", page)
    assert form.action, "expected Keycloak form"
    fields = {"username": username, "password": PASSWORD}
    if signup:
        assert "birthdate" in form.fields
        fields.update(email=f"{username}@example.invalid", firstName="Synthetic", lastName="Reader")
        fields["password-confirm"] = PASSWORD
        if birthdate is not None:
            fields["birthdate"] = birthdate
    headers, page = req(form.action, 302 if valid else 200, form=fields, opener=opener)
    if not valid:
        rendered = Form("kc-register-form", page)
        assert rendered.action and "birthdate" in rendered.fields
        return None
    location = headers["Location"]
    assert location.startswith(redirect + "?"), "registration/login must return to the configured callback"
    query = parse_qs(urlparse(location).query)
    assert query["state"] == ["fixture-state"]
    _, data = req(OIDC + "/token", form={
        "grant_type": "authorization_code", "client_id": client, "redirect_uri": redirect,
        "code": query["code"][0], "code_verifier": verifier,
    })
    return json.loads(data)


def token_contract(tokens, client):
    access = decode(tokens["access_token"])
    id_token = decode(tokens["id_token"])
    assert access["iss"] == ISSUER and access["azp"] == client
    assert id_token["iss"] == ISSUER and id_token["sub"] == access["sub"]
    assert "profile" in access["scope"].split()
    assert "birthdate" not in access and "birthdate" not in id_token
    userinfo = json.loads(req(OIDC + "/userinfo", token=tokens["access_token"])[1])
    assert userinfo["sub"] == access["sub"] and "birthdate" not in userinfo
    return access


def before():
    for _ in range(90):
        try:
            req(ISSUER + "/.well-known/openid-configuration")
            break
        except (URLError, AssertionError, TimeoutError):
            time.sleep(1)
    else:
        raise AssertionError("Keycloak readiness timeout")
    operator = admin_token()
    profile = admin("users/profile", operator)
    assert not any(a["name"] == "birthdate" for a in profile["attributes"])
    web = browser("legacy")
    mobile = browser("legacy", "bookrush-mobile")
    a = token_contract(web, "bookrush-web")
    b = token_contract(mobile, "bookrush-mobile")
    assert a["sub"] == b["sub"] and "account" in a["aud"]
    STATE.write_text(json.dumps({
        "subject": a["sub"], "refresh": web["refresh_token"], "web": a, "mobile": b,
        "profile": profile,
    }))
    print("Legado autentica antes da ativação com PKCE web e mobile")


def first():
    state = json.loads(STATE.read_text())
    operator = admin_token()
    profile = admin("users/profile", operator)
    assert len([a for a in profile["attributes"] if a["name"] == "birthdate"]) == 1
    assert {**profile, "attributes": [a for a in profile["attributes"] if a["name"] != "birthdate"]} == state["profile"]
    assert "birthdate" not in admin("users/" + state["subject"], operator).get("attributes", {})
    for username, value in [
        ("missing", None), ("bad-format", "20/12/2008"), ("bad-date", "2008-02-30"), ("future", "2100-01-01"),
    ]:
        browser(username, signup=True, birthdate=value, valid=False)
        assert admin("users?" + urlencode({"username": username, "exact": "true"}), operator) == []
    for username, value, client in [
        ("adult", "1999-10-09", "bookrush-web"), ("minor", "2016-02-29", "bookrush-mobile"),
    ]:
        access = token_contract(browser(username, client, signup=True, birthdate=value), client)
        assert admin("users/" + access["sub"], operator)["attributes"]["birthdate"] == [value]
    old = admin("users/" + state["subject"], operator)
    attributes = {**old.get("attributes", {}), "birthdate": ["2008-11-24"]}
    correction = {k: old[k] for k in ("username", "email", "firstName", "lastName")}
    correction["attributes"] = attributes
    batch = json.dumps({"issuer": ISSUER, "subject": state["subject"], "birthdate": "2008-11-24"}) + "\n"
    command = [sys.executable, "/tmp/reconcile-age-profile.py"]
    for result in ("1 updated, 0 unchanged", "0 updated, 1 unchanged"):
        output = subprocess.run([*command, "fill"], text=True, input=batch, capture_output=True, check=True).stdout
        assert result in output
    assert admin("users/" + state["subject"], operator)["attributes"]["birthdate"] == ["2008-11-24"]
    access = browser("legacy")["access_token"]
    req(ISSUER + "/account", 204, obj=correction, token=access)
    correction["attributes"]["birthdate"] = ["1999-01-01"]
    req(ISSUER + "/account", 400, obj=correction, token=access)
    assert admin("users/" + state["subject"], operator)["attributes"]["birthdate"] == ["2008-11-24"]
    for previous, replacement in [("2008-11-24", "2008-11-25"), ("2008-11-25", "2008-11-24")]:
        row = json.dumps({"issuer": ISSUER, "subject": state["subject"], "birthdate": replacement,
                          "expectedBirthdate": previous}) + "\n"
        subprocess.run([*command, "correct"], text=True, input=row, capture_output=True, check=True)
        assert admin("users/" + state["subject"], operator)["attributes"]["birthdate"] == [replacement]
    events = admin("admin-events?operationTypes=UPDATE&resourceTypes=USER", operator)
    assert any(event.get("resourcePath") == "users/" + state["subject"] for event in events)
    assert all(not event.get("representation") for event in events)
    state["first_profile"] = profile
    STATE.write_text(json.dumps(state))
    print("Cadastro adulto/menor, inválidos, preenchimento idempotente, auditoria e bloqueio de autoedição OK")


def after():
    state = json.loads(STATE.read_text())
    operator = admin_token()
    assert admin("users/profile", operator) == state["first_profile"]
    _, data = req(OIDC + "/token", form={
        "grant_type": "refresh_token", "client_id": "bookrush-web", "refresh_token": state["refresh"],
    })
    web = token_contract(json.loads(data), "bookrush-web")
    mobile = token_contract(browser("legacy", "bookrush-mobile"), "bookrush-mobile")
    for old, new in [(state["web"], web), (state["mobile"], mobile)]:
        for key in ("iss", "sub", "aud", "scope"):
            assert old.get(key) == new.get(key), key
    print("Reconcile 2x: perfil idempotente, credencial, refresh antigo, sub, issuer, aud e scopes preservados")


if __name__ == "__main__":
    {"fixture": fixture, "before": before, "first": first, "after": after}[sys.argv[1]](*sys.argv[2:])
