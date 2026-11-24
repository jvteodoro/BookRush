"""Assertions for the isolated Premium/Family topology. Never print bearer tokens."""
import base64
import json
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
            ("fixture-product", ["subscription.read", "family.manage", "family.invites"]),
            ("fixture-no-scope", []),
            ("fixture-invite-only", ["family.invites"]),
        ]
    ], users=[
        {"username": name, "enabled": True, "firstName": "Fixture", "lastName": "Reader",
         "email": name + "@example.invalid", "emailVerified": True, "credentials": [
            {"type": "password", "value": "fixture-reader-only", "temporary": False}]}
        for name in USERS
    ])
    platform = json.loads((root / "subscription/bookrush-platform.fragment.json").read_text())
    platform.update(realm="bookrush-platform", enabled=True, sslRequired="none")
    for client, secret in zip(platform["clients"], ("fixture-family-only", "fixture-billing-only"), strict=True):
        client["secret"] = secret
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
            raise AssertionError(f"{req.get_method()} {url}: expected {status}, got {response.status}")
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


def token(user=None, client=None, secret=None):
    realm = "bookrush" if user else "bookrush-platform"
    form = dict(client_id=client or "fixture-product")
    if user:
        form.update(grant_type="password", username=user, password="fixture-reader-only")
    else:
        form.update(grant_type="client_credentials", client_secret=secret)
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
    snapshot = {name: claims(value)["sub"] for name, value in [("holder", token("holder"))]}
    if pass_number == "1":
        STATE.write_text(json.dumps(snapshot))
    else:
        assert snapshot == json.loads(STATE.read_text())
    print(f"Keycloak reconcile {pass_number}: identity, issuer, audiences and scopes OK")


def write(url, value, *, payload=None, status=200, key=None):
    return request(url, status, token=value, method="POST", payload=payload,
                   headers={"Idempotency-Key": key or str(uuid4())})


def commercial():
    holder, other = token("holder"), token("other")
    billing = token(client="bookrush-billing-s2s", secret="fixture-billing-only")
    owner = {"issuer": claims(holder)["iss"], "subject": claims(holder)["sub"]}
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
    checkout = write(GATEWAY + CHECKOUT, holder, key=key, payload={
        "owner": {"issuer": owner["issuer"], "subject": claims(other)["sub"]}})
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
    request(endpoint, 404, token=other, parse=False)
    write(endpoint, other, payload={"recipientCode": codes[5]}, status=404)
    write(SUBSCRIPTION + SIMULATE, billing, payload={"owner": owner, "scenario": "REFUND"})
    assert request(GATEWAY + SELF, token=holder)["state"] == "CANCELED"
    write(endpoint, holder, payload={"recipientCode": codes[5]}, status=403)
    request(GATEWAY + SIMULATE, 404, token=billing, method="POST", parse=False)
    subjects = [claims(value)["sub"] for value in (holder, other, billing)]
    for service, port in [("subscription-service", 8102), ("family-service", 8103)]:
        metrics = request(f"http://{service}:{port}/actuator/prometheus", parse=False).decode()
        assert "jvm_memory_used_bytes" in metrics
        for line in metrics.splitlines():
            if line.startswith("bookrush_"):
                assert not re.search(r"\b(email|username|subject|issuer|user_id)=", line)
                assert not any(subject in line for subject in subjects)
                assert not any(user in line for user in USERS)
    STATE.write_text(json.dumps({"holder": holder, "household": household, "spare": codes[5]}))
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
    assert request(GATEWAY + FAMILY + "/me", token=state["holder"])["occupiedOrReserved"] == 5
    metrics = request("http://family-service:8103/actuator/prometheus", parse=False).decode()
    failure_counter = [line for line in metrics.splitlines()
                       if line.startswith("bookrush_family_subscription_failures_total")]
    assert failure_counter
    for line in failure_counter:
        assert set(re.findall(r"\b([a-z_]+)=", line)) <= {"environment", "service"}
    print("Gateway com subscription parado: 503, nenhuma nova reserva e contador sem labels de identidade")


if __name__ == "__main__":
    {"realms": realms, "wait": wait, "identity": identity, "commercial": commercial,
     "production": production, "outage": outage}[sys.argv[1]](*sys.argv[2:])
