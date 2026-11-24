#!/usr/bin/env python3
"""Configure both sides of publishing without changing or printing protected credentials."""
import os
from pathlib import Path
import secrets
import sys


def prepare(protected, output, environ=None):
    environ = os.environ if environ is None else environ
    values = {}
    for line in Path(protected).read_text().splitlines():
        key, separator, value = line.partition('=')
        if separator and not key.lstrip().startswith('#'):
            values[key.strip()] = value.strip().strip('"\'')
    enabled = environ.get('PUBLISHER_STORAGE_ENABLED', values.get('PUBLISHER_STORAGE_ENABLED', 'true'))
    if enabled not in ('true', 'false'):
        raise ValueError('PUBLISHER_STORAGE_ENABLED must be true or false')
    token = environ.get('PUBLISHER_INGESTION_CALLBACK_TOKEN') or values.get('PUBLISHER_INGESTION_CALLBACK_TOKEN') or secrets.token_hex(32)
    if any(char in token for char in '\r\n'):
        raise ValueError('Invalid publishing callback credential')
    # The same private temporary env file is used for deploy and rollback.
    path = Path(output)
    path.touch(mode=0o600, exist_ok=True)
    path.chmod(0o600)
    with path.open('a') as stream:
        stream.write(f'PUBLISHER_STORAGE_ENABLED={enabled}\nPUBLISHER_INGESTION_CALLBACK_TOKEN={token}\n')


if __name__ == '__main__':
    prepare(sys.argv[1], sys.argv[2])
