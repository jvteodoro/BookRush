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

    def test_community_registries_cover_routes_and_security_boundaries(self):
        import yaml

        for service, expected_count in {"social-service": 15, "reader-profile-service": 6, "behavior-service": 2}.items():
            base = ROOT / "services" / service
            registry = next(base.glob("src/main/**/ServiceProductAccess.java")).read_text()
            product = {(r["method"], r["internal"]) for r in ROUTES
                       if r["service"] == service and r["action"] == "PRODUCT_USE"}
            protected = set(re.findall(r'\.protect\("([A-Z]+)", "([^"]+)",', registry))
            self.assertEqual(product, protected)
            self.assertEqual(expected_count, len(protected))
            paths = yaml.safe_load((base / "api/openapi.yaml").read_text())["paths"]
            for rule in (r for r in ROUTES if r["service"] == service):
                op = paths[rule["internal"]][rule["method"].lower()]
                self.assertIn("401", op["responses"])
                if rule["action"] == "PRODUCT_USE":
                    self.assertTrue({"403", "503"}.issubset(op["responses"]))
                else:
                    self.assertIn(rule["v1_identity"], {"WORKLOAD", "OPERATOR"})
                    self.assertIn(f'existingSecurity("{rule["method"]}", "{rule["internal"]}")', registry)

    def test_distribution_policies_cover_all_owner_routes_and_head(self):
        import yaml

        for service, class_name in (("catalog-service", "CatalogProductAccess"),
                                    ("book-content-service", "ContentProductAccess")):
            base = ROOT / "services" / service
            expected = {r["internal"] for r in ROUTES if r["service"] == service and r["action"] == "PRODUCT_USE"}
            registry = next(base.glob(f"src/main/**/{class_name}.java")).read_text()
            actual = set(re.findall(r'\.protect\("GET", "([^"]+)",', registry))
            self.assertEqual(expected, actual)
            paths = yaml.safe_load((base / "api/openapi.yaml").read_text())["paths"]
            for route in expected:
                item = paths[route.replace("{*resource}", "{resource}")]
                for method in ("get", "head"):
                    self.assertTrue({"401", "403", "503"} <= item[method]["responses"].keys())

    def test_reader_state_gate_matches_inventory_and_openapi(self):
        import yaml

        registry = (ROOT / "services/reader-state-service/src/main/java/com/bookrush/readerstateservice/"
                    "ReaderStateProductAccess.java").read_text()
        expected = {(r["method"], r["internal"]) for r in ROUTES
                    if r["service"] == "reader-state-service" and r["action"] == "PRODUCT_USE"}
        actual = set(re.findall(r'\.protect\("([A-Z]+)", "([^"]+)", policy\)', registry))
        self.assertEqual(expected, actual)
        self.assertEqual(17, len(actual))
        spec = yaml.safe_load((ROOT / "services/reader-state-service/api/openapi.yaml").read_text())
        for method, path in expected:
            with self.subTest(method=method, path=path):
                self.assertTrue({"401", "403", "503"}.issubset(spec["paths"][path][method.lower()]["responses"]))

    def test_supervised_reading_inventory_and_payload_are_minimal(self):
        import yaml

        public = "/api/v1/reader/supervision/{memberId}/history"
        internal = "/api/internal/v1/reader/supervision/{memberId}/history"
        for service, path in (("reader-bff-service", public), ("reader-state-service", internal)):
            row = next(row for row in ROUTES if row["service"] == service and row["internal"] == path)
            self.assertEqual("SUPERVISE_HISTORY", row["action"])
            self.assertEqual(public if path == public else "—", row["public"])
            spec = yaml.safe_load((ROOT / f"services/{service}/api/openapi.yaml").read_text())
            operation = spec["paths"][path]["get"]
            self.assertEqual({"200", "400", "401", "403", "404", "503"}, set(operation["responses"]))
            self.assertEqual({"memberId", "scope", "limit", "cursor"},
                             {parameter["name"] for parameter in operation["parameters"]})
            page = spec["components"]["schemas"]["SupervisedReadingPage"]["properties"]
            self.assertEqual({"scope", "items", "nextCursor"}, set(page))
            self.assertEqual(50, page["items"]["maxItems"])
            columns = [set(item["properties"]) for item in page["items"]["items"]["oneOf"]]
            self.assertEqual([{"bookId", "accessedAt"}, {"date", "minutes", "sessions"}], columns)
            if path == internal:
                self.assertEqual([{"bearerAuth": [], "supervisionWorkload": []}], operation["security"])

    def test_supervised_comments_contract_projects_only_visible_comment_fields(self):
        import yaml

        routes = {
            "reader-bff-service": "/api/v1/reader/supervision/{memberId}/comments",
            "social-service": "/api/internal/v1/social/supervision/{memberId}/comments",
        }
        for service, path in routes.items():
            row = next(row for row in ROUTES if row["service"] == service and row["internal"] == path)
            self.assertEqual("SUPERVISE_COMMENTS", row["action"])
            self.assertEqual(path if service == "reader-bff-service" else "—", row["public"])
            spec = yaml.safe_load((ROOT / f"services/{service}/api/openapi.yaml").read_text())
            self.assertEqual({"200", "400", "401", "403", "404", "503"}, set(spec["paths"][path]["get"]["responses"]))
            items = spec["components"]["schemas"]["SupervisedCommentsPage"]["properties"]["items"]
            self.assertEqual(50, items["maxItems"])
            self.assertEqual({"id", "bookId", "body", "createdAt"}, set(items["items"]["properties"]))
            self.assertFalse(items["items"]["additionalProperties"])

    def test_session_bootstrap_exposes_only_own_minimal_facts(self):
        import yaml

        row = next(row for row in ROUTES if row["internal"] == "/api/v1/session")
        self.assertEqual((row["action"], row["restricted"]), ("SESSION_VIEW", "yes"))
        self.assertEqual(row["public"], "/api/v1/session")
        spec = yaml.safe_load((ROOT / "services/reader-bff-service/api/openapi.yaml").read_text())
        operation = spec["paths"][row["public"]]["get"]
        self.assertEqual({"200", "401", "403", "503"}, set(operation["responses"]))
        result = spec["components"]["schemas"]["ReaderSession"]
        self.assertFalse(result["additionalProperties"])
        self.assertEqual({"username", "accessState", "reasonCode", "capabilities", "validUntil"},
                         set(result["properties"]))
        self.assertEqual({"SESSION_VIEW", "PRODUCT_USE", "INVITE_SELF"},
                         set(result["properties"]["capabilities"]["items"]["enum"]))

    def test_publisher_openapi_covers_every_owner_operation(self):
        import yaml

        spec = yaml.safe_load((ROOT / "services/publisher-service/api/openapi.yaml").read_text())
        owned = [row for row in ROUTES if row["service"] == "publisher-service"
                 and row["action"] in {"PUBLISH_READ", "PUBLISH_WRITE", "PUBLISH_WITHDRAW"}]
        self.assertEqual(12, len(owned))
        for row in owned:
            operation = spec["paths"][row["internal"]][row["method"].lower()]
            responses = {"200", "401", "403"}
            if "{" in row["internal"]:
                responses.add("404")
            self.assertTrue(responses <= set(operation["responses"]))
            self.assertEqual([{"publisherBearer": []}], operation["security"])

    def test_family_openapi_publishes_canonical_workload_contract(self):
        import yaml

        family = yaml.safe_load((ROOT / "services/family-service/api/openapi.yaml").read_text())
        path = "/api/internal/v1/product-access/decisions"
        canonical = SPEC["paths"][path]["post"]
        implemented = family["paths"][path]["post"]
        self.assertEqual(canonical["operationId"], implemented["operationId"])
        self.assertEqual(set(canonical["responses"]), set(implemented["responses"]))
        self.assertEqual([{"productDecisionBearer": []}], implemented["security"])
        schema_ref = "#/components/schemas/ProductAccess"
        self.assertEqual(implemented["requestBody"]["content"]["application/json"]["schema"]["$ref"],
                         schema_ref + "Request")
        for status, response in implemented["responses"].items():
            media, schema = ("application/json", "Decision") if status == "200" else (
                "application/problem+json", "Problem")
            self.assertEqual(response["content"][media]["schema"]["$ref"], schema_ref + schema)
        schemas = family["components"]["schemas"]
        for key in ("Identity", "Authentication", "Resource", "Request", "Problem"):
            expected = json.dumps(SCHEMAS[key]).replace("#/components/schemas/", schema_ref)
            self.assertEqual(schemas["ProductAccess" + key], json.loads(expected))
        decision = schemas["ProductAccessDecision"]
        self.assertEqual(decision["required"], SCHEMAS["Decision"]["required"])
        self.assertEqual(decision["properties"], {**SCHEMAS["Decision"]["properties"],
                                                 "policyVersion": {"type": "string", "enum": ["1"]}})
        self.assertEqual(decision["oneOf"], [
            {"properties": {"allowed": {"type": "boolean", "enum": [True]}}, "not": {"required": ["reason"]}},
            {"properties": {"allowed": {"type": "boolean", "enum": [False]}}, "required": ["reason"]},
        ])

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
