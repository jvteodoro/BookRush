#!/usr/bin/env python3
"""Exercise deploy image preparation without Docker, credentials or a deployment."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / 'infrastructure/jenkins/deploy-compose.sh'
SERVICES = {
    'RECOMMENDATION_IMAGE': 'recommendation-service',
    'READER_BFF_IMAGE': 'reader-bff-service',
    'READER_PROFILE_IMAGE': 'reader-profile-service',
    'SOCIAL_IMAGE': 'social-service',
}


class DeployImagesTest(unittest.TestCase):
    def prepare(self, explicit=False, cached=False, backend=None, fail_build=False):
        with tempfile.TemporaryDirectory(prefix='bookrush-image-contract-') as directory:
            folder = Path(directory)
            docker = folder / 'docker'
            docker.write_text('''#!/bin/bash
printf '%s\\n' "$*" >> "$CALL_LOG"
if [[ "$1 $2" == 'inspect --format' ]]; then echo healthy; exit 0; fi
if [[ "$1 $2" == 'image inspect' ]]; then [[ "$3" == test/admin* || "$CACHED" == true ]]; exit; fi
if [[ "$1" == build ]]; then [[ "$FAIL_BUILD" != true ]]; exit; fi
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
                       FAIL_BUILD=str(fail_build).lower(),
                       BACKEND_IMAGE=backend or 'registry.example:5000/team/bookrush/catalog-service:42-abcdef0',
                       FRONTEND_IMAGE='registry.example:5000/team/bookrush/frontend:42-abcdef0',
                       INGESTION_IMAGE='test/ingestion:42', ANALYTICS_IMAGE='test/analytics:42',
                       ADMIN_SERVICE_IMAGE='test/admin:42', ADMIN_WEB_IMAGE='test/admin-web:42')
            if explicit:
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
        self.assertEqual(calls.count('build -t'), 4)

    def test_explicit_images_are_preserved_without_builds(self):
        result, calls = self.prepare(explicit=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn('build -t', calls)
        for variable, service in SERVICES.items():
            self.assertIn(f'{variable}=custom/{service}:selected', result.stdout)

    def test_existing_derived_images_are_reused(self):
        result, calls = self.prepare(cached=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls.count('image inspect registry.example'), 4)
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


if __name__ == '__main__':
    unittest.main()
