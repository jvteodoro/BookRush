#!/usr/bin/env bash
set -Eeuo pipefail
set +x
python3 - "$@" <<'PY'
import datetime
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request


def skip(message):
    print(message, file=sys.stderr)
    raise SystemExit(0)


if len(sys.argv) < 3 or len(sys.argv) > 7:
    raise SystemExit("usage: publish-release-event.sh EVENT STATUS [SERVICE IMAGE_REF DIGEST DURATION_MS]")
event_type, status = sys.argv[1:3]
service_name, image_ref, image_digest, duration_ms = (sys.argv[3:] + [""] * 4)[:4]
duration_ms = duration_ms or os.environ.get("RELEASE_BUILD_DURATION_MS", "")
secret = os.environ.get("JENKINS_RELEASE_REPORTING_CLIENT_SECRET", "")
if not secret:
    skip("Release reporting skipped: Jenkins S2S secret is not configured.")

build_number = os.environ.get("BUILD_NUMBER", "")
if not re.fullmatch(r"[1-9][0-9]*", build_number):
    skip("Release reporting skipped: Jenkins build number is invalid.")
git_commit = os.environ.get("GIT_COMMIT", "")
if not re.fullmatch(r"(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})", git_commit):
    git_commit = ""
if not re.fullmatch(r"[0-9]+", duration_ms):
    duration_ms = ""

token_url = "https://keycloak-bookrush.jteodoro.tec.br/realms/bookrush-platform/protocol/openid-connect/token"
report_url = "http://admin-service:8080/api/admin/v1/reporting/internal/release-events"
form = urllib.parse.urlencode(
    {
        "grant_type": "client_credentials",
        "client_id": "bookrush-jenkins-release-s2s",
        "client_secret": secret,
    }
).encode()
token_request = urllib.request.Request(token_url, data=form, method="POST")
token_request.add_header("Content-Type", "application/x-www-form-urlencoded")
try:
    with urllib.request.urlopen(token_request, timeout=8) as response:
        access_token = json.load(response).get("access_token")
except urllib.error.HTTPError as error:
    raise SystemExit(f"Release reporting token request failed with HTTP {error.code}.")
except (urllib.error.URLError, TimeoutError, ValueError):
    raise SystemExit("Release reporting token request failed.")
if not access_token:
    raise SystemExit("Release reporting token response did not include an access token.")

event_id = f"{build_number}:{event_type}:{status}:{service_name or 'pipeline'}"
now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
payload = {
    "eventId": event_id,
    "jobName": os.environ.get("JOB_NAME", "bookrush-deploy"),
    "buildNumber": int(build_number),
    "buildUrl": os.environ.get("BUILD_URL") or None,
    "branch": os.environ.get("SELECTED_BRANCH") or os.environ.get("BRANCH") or None,
    "gitCommit": git_commit or None,
    "eventType": event_type,
    "status": status,
    "serviceName": service_name or None,
    "imageRef": image_ref or None,
    "imageDigest": image_digest or None,
    "durationMs": int(duration_ms) if duration_ms else None,
    "occurredAt": now,
}
request = urllib.request.Request(
    report_url,
    data=json.dumps(payload, separators=(",", ":")).encode(),
    method="POST",
    headers={"Authorization": f"Bearer {access_token}", "Content-Type": "application/json"},
)
try:
    with urllib.request.urlopen(request, timeout=8):
        pass
except urllib.error.HTTPError as error:
    raise SystemExit(f"Release event submission failed with HTTP {error.code}.")
except (urllib.error.URLError, TimeoutError):
    raise SystemExit("Release event submission failed.")
print(f"Release event recorded: {event_type} {status} {service_name or 'pipeline'}.", file=sys.stderr)
PY
