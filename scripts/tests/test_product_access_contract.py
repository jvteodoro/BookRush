"""Executable contract examples. No runtime service or credentials are needed."""
import csv
import json
import os
import re
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker, ValidationError

ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / "docs/productization/product-access-v1"
SPEC = json.loads((CONTRACT / "openapi.json").read_text())
SCHEMAS = SPEC["components"]["schemas"]
with (CONTRACT / "routes.tsv").open() as source:
    ROUTES = list(csv.DictReader(source, delimiter="\t"))

TECHNICAL = {"OPERATOR_OPERATION", "WORKLOAD_OPERATION", "PUBLIC_STATUS"}
RESTRICTED_ACTIONS = {"SESSION_VIEW", "INVITE_SELF"}


def validate(schema, value):
    document = {"$ref": f"#/components/schemas/{schema}", "components": SPEC["components"]}
    Draft202012Validator(document, format_checker=FormatChecker()).validate(value)


def evaluate(route, facts):
    """Reference semantics for the fixture facts resolved by trusted owners."""
    action = route["action"]
    issuer = "https://id.example/realms/" + route["v1_realm"]
    audience = route["v1_audience"]
    if facts.get("missing") or facts.get("issuer", issuer) != issuer or facts.get("audience", audience) != audience:
        return 401, "INVALID_AUTHENTICATION"
    if not facts.get("scope", True):
        return 403, "SCOPE_REQUIRED"
    if action in TECHNICAL:
        return 200, None
    if facts.get("unavailable"):
        return 503, "DECISION_UNAVAILABLE"
    if facts.get("suspended"):
        return 403, "ACCOUNT_SUSPENDED"
    if not facts.get("owner", True):
        return 403, "OWNERSHIP_REQUIRED"
    minor = facts.get("minor", False)
    paid = facts.get("paid") and not facts.get("expired")
    eligible = not minor or all((paid, facts.get("member"), facts.get("access_grant")))
    if action in RESTRICTED_ACTIONS:
        return 200, None
    if action == "PUBLISH_WRITE" and minor:
        return 403, "AGE_INELIGIBLE"
    if not eligible or facts.get("unknown_age"):
        return 403, "ACCOUNT_RESTRICTED"
    if action in {"SUPERVISE_HISTORY", "SUPERVISE_COMMENTS"}:
        if not facts.get("target_minor"):
            return 403, "TARGET_NOT_ELIGIBLE"
        if not facts.get("guardian"):
            return 403, "SUPERVISION_REQUIRED"
    if action in {"PUBLISH_WRITE", "FAMILY_MANAGE"} and not paid:
        return 403, "ENTITLEMENT_REQUIRED"
    if action == "PUBLISH_WRITE" and facts.get("member") and (
        not facts.get("delegate") or facts.get("revoked")
    ):
        return 403, "DELEGATION_REQUIRED"
    return 200, None


def controller_routes(root):
    """Read the literal Spring mapping annotations used in this baseline."""
    mapping = re.compile(r"@(Get|Post|Put|Patch|Delete|Request)Mapping\b(?:\s*\(([^)]*)\))?")
    for file in root.glob("services/*/src/main/**/*.java"):
        source = file.read_text()
        if "@RestController" not in source and "@Controller" not in source:
            continue
        service = file.relative_to(root).parts[1]
        prefixes = [""]
        for match in mapping.finditer(source):
            method, arguments = match.groups()
            arguments = re.split(r"\b(?:produces|consumes)\s*=", arguments or "")[0]
            suffixes = re.findall(r'"((?:/[^"]*)?)"', arguments) or [""]
            if method == "Request":
                if "RequestMethod" in arguments:
                    raise AssertionError(f"Unsupported method-specific RequestMapping in {file}")
                prefixes = suffixes
                continue
            for prefix in prefixes:
                for suffix in suffixes:
                    yield service, method.upper(), prefix + suffix


class ProductAccessContractTest(unittest.TestCase):
    def test_inventory_and_explicit_restricted_exceptions(self):
        keys = [(r["service"], r["method"], r["internal"]) for r in ROUTES]
        self.assertEqual(len(set(keys)), len(keys))
        for row in ROUTES:
            with self.subTest(route=row["internal"], service=row["service"]):
                action = row["action"]
                self.assertIn(action, set(SCHEMAS["Request"]["properties"]["action"]["enum"]) | TECHNICAL)
                self.assertEqual(row["restricted"] == "yes", action in RESTRICTED_ACTIONS | {"PUBLIC_STATUS"})
        historical_source = os.environ.get("BOOKRUSH_CONTRACT_SOURCE")
        baseline = Path(historical_source) if historical_source else ROOT
        actual = set(controller_routes(baseline))
        listed = {(r["service"], r["method"], r["internal"])
                  for r in ROUTES if r["controller"] != "planned" and not (
                      historical_source and r["baseline_identity"] == "AFTER_BASELINE"
                      and not (baseline / "services" / r["service"]).exists()
                  )}
        self.assertEqual(listed, actual)
        restricted = {(r["method"], r["internal"]) for r in ROUTES if r["action"] in RESTRICTED_ACTIONS}
        self.assertEqual(restricted, {
            ("GET", "/api/v1/session"),
            ("GET", "/api/v1/family/invitations/me"),
            ("POST", "/api/v1/family/invitations/{id}/accept"),
            ("POST", "/api/v1/family/invitations/{id}/decline"),
            ("POST", "/api/v1/family/invite-address"),
        })

    def test_policy_vectors(self):
        by_key = {" ".join(r[k] for k in ("service", "method", "internal")): r for r in ROUTES}
        for case in json.loads((CONTRACT / "fixtures.json").read_text()):
            with self.subTest(case=case["name"]):
                route = by_key[case["route"]]
                status, reason = evaluate(route, case["facts"])
                self.assertEqual({"status": status, "reason": reason}, case["expected"])
                if route["action"] in TECHNICAL:
                    continue
                correlation = "contract-test"
                if status in (200, 403) and case["facts"].get("scope", True):
                    decision = dict(allowed=status == 200, policyVersion="1",
                                    correlationId=correlation, validUntil="2026-11-24T00:00:00Z",
                                    factVersions={"account": "4"})
                    if reason:
                        decision["reason"] = reason
                    validate("Decision", decision)
                if status != 200:
                    validate("Problem", dict(status=status, code=reason, correlationId=correlation))

    def test_request_has_no_client_entitlement_or_age_facts(self):
        request = dict(actor={"issuer": "https://id.example/realms/bookrush", "subject": "test"},
                       action="PRODUCT_USE", resource={"type": "BOOK", "id": "book-1"},
                       authentication={"clientId": "web", "audience": "account", "scopes": ["read"]},
                       correlationId="contract-test")
        validate("Request", request)
        for field in ("age", "birthDate", "premium", "family", "guardian"):
            with self.subTest(field=field), self.assertRaises(ValidationError):
                validate("Request", {**request, field: True})

    def test_deny_requires_reason_and_allow_forbids_it(self):
        decision = dict(allowed=False, correlationId="test", policyVersion="1",
                        validUntil="2026-11-24T00:00:00Z", factVersions={})
        with self.assertRaises(ValidationError):
            validate("Decision", decision)
        with self.assertRaises(ValidationError):
            validate("Decision", {**decision, "allowed": True, "reason": "ACCOUNT_RESTRICTED"})


if __name__ == "__main__":
    unittest.main()
