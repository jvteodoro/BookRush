#!/usr/bin/env python3
"""Request a client token; stdout is captured only by the protected deploy."""
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request


def main():
    base = os.environ['KEYCLOAK_URL'].rstrip('/')
    realm = urllib.parse.quote(os.environ['KEYCLOAK_REALM'], safe='')
    body = urllib.parse.urlencode({
        'grant_type': 'client_credentials',
        'client_id': os.environ['KEYCLOAK_CLIENT_ID'],
        'client_secret': os.environ['KEYCLOAK_CLIENT_SECRET'],
    }).encode()
    request = urllib.request.Request(
        f'{base}/realms/{realm}/protocol/openid-connect/token',
        data=body, headers={'Content-Type': 'application/x-www-form-urlencoded'},
        method='POST',
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            payload = json.load(response)
    except urllib.error.HTTPError as error:
        # Never log the response body: it may contain sensitive input.
        print(f'Smoke de usuários: endpoint de token retornou HTTP {error.code}; confira cliente, segredo e service account no realm de plataforma.', file=sys.stderr)
        return 1
    except urllib.error.URLError:
        print('Smoke de usuários: falha de conexão com o endpoint de token do Keycloak.', file=sys.stderr)
        return 1
    except (ValueError, TimeoutError, OSError):
        print('Smoke de usuários: resposta inválida ou indisponível no endpoint de token.', file=sys.stderr)
        return 1
    token = payload.get('access_token') if isinstance(payload, dict) else None
    if not isinstance(token, str) or not token.strip():
        print('Smoke de usuários: resposta do Keycloak sem access_token válido.', file=sys.stderr)
        return 1
    print(token)
    return 0


if __name__ == '__main__':
    sys.exit(main())
