#!/usr/bin/env python3
"""Exercise deploy image preparation without Docker, credentials or a deployment."""
import os
import contextlib
import io
import runpy
import urllib.error
import urllib.parse
from unittest.mock import patch
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / 'infrastructure/jenkins/deploy-compose.sh'
SERVICES = {
    'RECOMMENDATION_IMAGE': 'recommendation-service',
    'READER_BFF_IMAGE': 'reader-bff-service',
    'READER_STATE_IMAGE': 'reader-state-service',
    'READER_PROFILE_IMAGE': 'reader-profile-service',
    'SOCIAL_IMAGE': 'social-service',
}


class DeployImagesTest(unittest.TestCase):
    def prepare(self, explicit=False, cached=False, backend=None, fail_build=False, gpu=False, missing_image=""):
        with tempfile.TemporaryDirectory(prefix='bookrush-image-contract-') as directory:
            folder = Path(directory)
            docker = folder / 'docker'
            docker.write_text('''#!/bin/bash
printf '%s\\n' "$*" >> "$CALL_LOG"
if [[ "$1 $2" == 'inspect --format' ]]; then echo healthy; exit 0; fi
if [[ "$1 $2" == 'image inspect' ]]; then
  [[ -z "$MISSING_IMAGE" || "$3" != "$MISSING_IMAGE" ]] || exit 1
  if [[ "$CACHED" == true || "$3" == test/* || "$3" == custom/* || "$3" == */catalog-service:* || "$3" == */frontend:* || "$3" == */book-analytics-service:* ]]; then exit 0; fi
  grep -Fxq -- "$3" "$CALL_LOG.built" 2>/dev/null
  exit
fi
if [[ "$1" == build ]]; then
  [[ "$FAIL_BUILD" != true ]] || exit 1
  while (($#)); do
    if [[ "$1" == -t ]]; then printf '%s\\n' "$2" >> "$CALL_LOG.built"; break; fi
    shift
  done
  exit 0
fi
if [[ "$1" == compose ]]; then exit 0; fi
exit 99
''')
            docker.chmod(0o755)
            awk = folder / 'awk'
            awk.write_text('#!/bin/bash\necho https://storage.example.test\n')
            awk.chmod(0o755)
            # Stop before reading previous containers or accessing Keycloak.
            preparation = SCRIPT.read_text().split('\nold_backend=', 1)[0]
            preparation = preparation.replace(
                'script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"',
                'script_dir="$TEST_SCRIPT_DIR"')
            runner = folder / 'prepare.sh'
            runner.write_text(preparation + '\nenv | sort\n')
            env = {key: value for key, value in os.environ.items()
                   if not key.endswith('_IMAGE')}
            env.update(PATH=f'{folder}:{env["PATH"]}', CALL_LOG=str(folder / 'calls'),
                       TEST_SCRIPT_DIR=str(SCRIPT.parent), CACHED=str(cached).lower(),
                       FAIL_BUILD=str(fail_build).lower(), ANALYTICS_GPU=str(gpu).lower(),
                       MISSING_IMAGE=missing_image,
                       BACKEND_IMAGE=backend or 'registry.example:5000/team/bookrush/catalog-service:42-abcdef0',
                       FRONTEND_IMAGE='registry.example:5000/team/bookrush/frontend:42-abcdef0',
                       INGESTION_IMAGE='test/ingestion:42', ANALYTICS_IMAGE='registry.example:5000/team/bookrush/book-analytics-service:42-abcdef0',
                       ADMIN_SERVICE_IMAGE='test/admin:42', ADMIN_WEB_IMAGE='test/admin-web:42')
            if explicit:
                env['ANALYTICS_RUNTIME_IMAGE'] = 'custom/runtime:selected'
                env.update({key: f'custom/{service}:selected' for key, service in SERVICES.items()})
            result = subprocess.run(['bash', str(runner)], env=env, text=True, capture_output=True)
            calls = (folder / 'calls').read_text() if (folder / 'calls').exists() else ''
            return result, calls

    def test_old_pipeline_builds_missing_images_from_selected_checkout(self):
        result, calls = self.prepare()
        self.assertEqual(result.returncode, 0, result.stderr)
        for variable, service in SERVICES.items():
            image = f'registry.example:5000/team/bookrush/{service}:42-abcdef0'
            self.assertIn(f'{variable}={image}', result.stdout)
            self.assertIn(f'build -t {image} -f {ROOT}/services/{service}/Dockerfile {ROOT}', calls)
        self.assertEqual(calls.count('build -t'), 6)

    def test_runtime_cpu_is_built_before_compose_validation(self):
        result, calls = self.prepare()
        self.assertEqual(result.returncode, 0, result.stderr)
        expected = f'build -t registry.example:5000/team/bookrush/book-analytics-runtime:42-abcdef0 {ROOT}/services/book-analytics-runtime'
        self.assertIn(expected, calls)
        self.assertLess(calls.index(expected), calls.index('compose '))
        self.assertNotIn('--build-arg', calls)

    def test_runtime_gpu_uses_cuda_requirements(self):
        result, calls = self.prepare(gpu=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('build --build-arg ANALYTICS_RUNTIME_REQUIREMENTS=requirements.cuda.txt -t ', calls)

    def test_missing_explicit_image_stops_before_compose(self):
        images = ['custom/runtime:selected', 'test/ingestion:42',
                  'registry.example:5000/team/bookrush/catalog-service:42-abcdef0',
                  'registry.example:5000/team/bookrush/frontend:42-abcdef0']
        images.extend(f'custom/{service}:selected' for service in SERVICES.values())
        for image in images:
            with self.subTest(image=image):
                result, calls = self.prepare(explicit=True, missing_image=image)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('Imagem de deploy ausente no daemon:', result.stderr)
                self.assertNotIn('compose ', calls)
                self.assertNotIn('build ', calls)

    def test_explicit_images_are_preserved_without_builds(self):
        result, calls = self.prepare(explicit=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn('build -t', calls)
        for variable, service in SERVICES.items():
            self.assertIn(f'{variable}=custom/{service}:selected', result.stdout)

    def test_existing_derived_images_are_reused(self):
        result, calls = self.prepare(cached=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls.count('image inspect registry.example'), 15)
        self.assertNotIn('build -t', calls)

    def test_unrecognized_backend_fails_before_docker(self):
        result, calls = self.prepare(backend='custom/catalog:42')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Não foi possível derivar RECOMMENDATION_IMAGE', result.stderr)
        self.assertEqual(calls, '')

    def test_build_failure_stops_preparation(self):
        result, calls = self.prepare(fail_build=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(calls.count('build -t'), 1)



class ClientTokenTest(unittest.TestCase):
    def request(self, response=None, error=None):
        output, errors = io.StringIO(), io.StringIO()
        env = {'KEYCLOAK_URL': 'http://keycloak:8080',
               'KEYCLOAK_REALM': 'bookrush-platform',
               'KEYCLOAK_CLIENT_ID': 'bookrush-ingestion-admin',
               'KEYCLOAK_CLIENT_SECRET': 'fixture+secret&=value'}
        with patch.dict(os.environ, env), patch('urllib.request.urlopen') as urlopen:
            if error:
                urlopen.side_effect = error
            else:
                urlopen.return_value.__enter__.return_value = io.BytesIO(response)
            with contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
                with self.assertRaises(SystemExit) as stopped:
                    runpy.run_path(str(ROOT / 'infrastructure/keycloak/request-client-token.py'), run_name='__main__')
            request = urlopen.call_args.args[0]
        self.assertNotIn(env['KEYCLOAK_CLIENT_SECRET'], errors.getvalue())
        self.assertNotIn('fixture-token', errors.getvalue())
        return stopped.exception.code, output.getvalue(), errors.getvalue(), request

    def test_token_request_and_form_encoding(self):
        code, output, errors, request = self.request(b'{"access_token":"fixture-token"}')
        self.assertEqual(code, 0)
        self.assertEqual(output, 'fixture-token\n')
        self.assertEqual(errors, '')
        self.assertEqual(request.full_url, 'http://keycloak:8080/realms/bookrush-platform/protocol/openid-connect/token')
        self.assertEqual(urllib.parse.parse_qs(request.data.decode()), {
            'grant_type': ['client_credentials'], 'client_id': ['bookrush-ingestion-admin'],
            'client_secret': ['fixture+secret&=value']})

    def test_http_rejection_is_reported_without_body(self):
        error = urllib.error.HTTPError('http://keycloak/token', 401, 'fixture-token', {}, io.BytesIO(b'fixture+secret&=value'))
        code, output, errors, _ = self.request(error=error)
        self.assertEqual(code, 1)
        self.assertEqual(output, '')
        self.assertIn('HTTP 401', errors)

    def test_connection_failure_is_reported(self):
        code, output, errors, _ = self.request(error=urllib.error.URLError('fixture-token'))
        self.assertEqual(code, 1)
        self.assertEqual(output, '')
        self.assertIn('falha de conexão', errors)

    def test_invalid_payloads_fail_without_token(self):
        for payload in (b'not-json', b'{}', b'[]', b'{"access_token":123}', b'{"access_token":""}'):
            with self.subTest(payload=payload):
                code, output, errors, _ = self.request(payload)
                self.assertEqual(code, 1)
                self.assertEqual(output, '')
                self.assertTrue(errors)

if __name__ == '__main__':
    unittest.main()
