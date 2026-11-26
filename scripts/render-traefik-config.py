#!/usr/bin/env python3
"""Render the small, explicit conditional subset used by Traefik routes."""
from __future__ import annotations

import os
import re
import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: render-traefik-config.py FILE", file=sys.stderr)
        return 2
    source = Path(sys.argv[1]).read_text(encoding="utf-8")
    protected = os.environ.get("BOOKRUSH_PROTECTED_DISTRIBUTION", "false").lower() == "true"
    closed = os.environ.get("BOOKRUSH_STORAGE_EDGE_CLOSED", "false").lower() == "true"
    close_storage = protected or closed
    pattern = re.compile(
        r"^\{\{ if or \(eq \(env \"BOOKRUSH_PROTECTED_DISTRIBUTION\"\) \"true\"\) "
        r"\(eq \(env \"BOOKRUSH_STORAGE_EDGE_CLOSED\"\) \"true\"\) \}\}\n"
        r"(?P<indent>[ \t]+)service: noop@internal\n"
        r"\{\{ else \}\}\n"
        r"[ \t]+service: (?P<public_service>[A-Za-z0-9_-]+)\n"
        r"\{\{ end \}\}",
        re.MULTILINE,
    )
    rendered, count = pattern.subn(
        lambda match: match.group("indent") + ("service: noop@internal" if close_storage else "service: " + match.group("public_service")),
        source,
    )
    asset_pattern = re.compile(
        r"^\{\{ if eq \(env \"BOOKRUSH_PROTECTED_DISTRIBUTION\"\) \"true\" \}\}\n"
        r"(?P<asset>    catalog-asset-inspection:\n(?:      .*\n|\n)*?)"
        r"\{\{ end \}\}",
        re.MULTILINE,
    )
    asset_replacement = lambda match: match.group("asset") if protected else ""
    rendered, asset_count = asset_pattern.subn(asset_replacement, rendered)
    if count != 3 or asset_count != 1 or "{{" in rendered or "}}" in rendered:
        print("Traefik config contém bloco condicional desconhecido ou incompleto", file=sys.stderr)
        return 1
    sys.stdout.write(rendered)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
