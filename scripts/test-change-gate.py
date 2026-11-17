#!/usr/bin/env python3
"""Run the actual change gate against disposable Git histories, with network tools forbidden."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ChangeGateTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory(prefix='bookrush-change-gate-')
        self.addCleanup(self.folder.cleanup)
        self.repo = Path(self.folder.name)
        (self.repo / 'scripts').mkdir()
        shutil.copyfile(ROOT / 'scripts/validate-change.sh', self.repo / 'scripts/validate-change.sh')
        (self.repo / 'scripts/validate-pipelines.sh').write_text('echo "Pipeline contracts executed"\n')
        (self.repo / 'scripts/validate-analytics-specs.py').write_text('print("Analytics validator executed")\n')
        (self.repo / 'docs').mkdir()
        (self.repo / 'docs/guide.md').write_text('Initial documentation\n')
        self.git = shutil.which('git')
        self.git_run('init', '-q')
        self.git_run('config', 'user.name', 'Change gate fixture')
        self.git_run('config', 'user.email', 'fixture@example.test')
        self.commit()
        self.base = self.git_run('rev-parse', 'HEAD').stdout.strip()
        self.bin = self.repo / 'bin'
        self.bin.mkdir()
        self.env = dict(os.environ, PATH=f'{self.bin}:{os.environ["PATH"]}')
        self.forbidden = self.repo / 'forbidden-tool-used'
        for tool in ('docker', 'apt-get', 'pip', 'pip3', 'curl'):
            self.executable(tool, f'printf "%s" {tool} > "{self.forbidden}"\nexit 99\n')
        # Model optional dependencies separately from the Python interpreter.
        self.executable('python3', f'''if [[ "$1" == -c && "$2" == 'import yaml, jsonschema' ]]; then
  exit "${{FIXTURE_IMPORT_STATUS:-0}}"
fi
exec "{sys.executable}" "$@"
''')

    def executable(self, name, source):
        path = self.bin / name
        path.write_text('#!/bin/bash\n' + source)
        path.chmod(0o755)

    def git_run(self, *args):
        return subprocess.run([self.git, *args], cwd=self.repo, check=True, capture_output=True, text=True)

    def commit(self):
        self.git_run('add', 'scripts', 'docs')
        self.git_run('commit', '-qm', 'fixture change')

    def change(self, relative, content):
        file = self.repo / relative
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content)
        self.git_run('add', relative)
        self.git_run('commit', '-qm', 'fixture change')

    def gate(self, *args):
        result = subprocess.run(['bash', 'scripts/validate-change.sh', '--ci', '--offline', '--base', self.base, *args],
                                cwd=self.repo, env=self.env, capture_output=True, text=True, timeout=10)
        self.assertFalse(self.forbidden.exists(), 'offline gate invoked a network/container tool')
        return result

    def test_documentation_gate_needs_no_optional_packages_or_container(self):
        self.env['FIXTURE_IMPORT_STATUS'] = '1'
        self.change('docs/guide.md', 'Updated documentation\n')
        result = self.gate()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('Pipeline contracts executed', result.stdout)
        self.assertIn('Change gate: OK', result.stdout)

    def test_missing_analytics_dependencies_fail_fast_without_installing(self):
        self.env['FIXTURE_IMPORT_STATUS'] = '1'
        self.change('services/book-analytics-service/config/fixture.yaml', 'version: 1\n')
        result = self.gate()
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn('python3-yaml', result.stderr)
        self.assertNotIn('Analytics validator executed', result.stdout)

    def test_analytics_validator_still_runs_when_dependencies_are_ready(self):
        self.change('services/book-analytics-service/config/fixture.yaml', 'version: 1\n')
        result = self.gate()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('Analytics validator executed', result.stdout)
        self.assertIn('Pipeline contracts executed', result.stdout)

    def test_failed_git_diff_cannot_be_misreported_as_no_changes(self):
        self.change('docs/guide.md', 'Updated documentation\n')
        self.executable('git', f'if [[ "$1" == diff ]]; then exit 77; fi\nexec "{self.git}" "$@"\n')
        result = self.gate()
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn('diff Git', result.stderr)
        self.assertNotIn('nenhum arquivo', result.stdout)

    def test_missing_service_documentation_still_rejects_the_change(self):
        self.change('services/fixture/src/main/App.java', 'class App {}\n')
        result = self.gate()
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn('sem atualização', result.stderr)

    def test_missing_python_fails_before_any_container_fallback(self):
        minimal = self.repo / 'minimal-bin'
        minimal.mkdir()
        for name in ('bash', 'dirname', 'git'):
            (minimal / name).symlink_to(shutil.which(name))
        self.env['PATH'] = str(minimal)
        result = self.gate()
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn('python3 ausente', result.stderr)

    def test_analytics_validation_failure_is_not_suppressed(self):
        self.change('services/book-analytics-service/config/fixture.yaml', 'version: 1\n')
        (self.repo / 'scripts/validate-analytics-specs.py').write_text('raise SystemExit(23)\n')
        result = self.gate()
        self.assertEqual(result.returncode, 23, result.stdout + result.stderr)
        self.assertNotIn('Change gate: OK', result.stdout)

    def test_empty_diff_still_executes_pipeline_contracts(self):
        result = self.gate()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('nenhum arquivo', result.stdout)
        self.assertIn('Pipeline contracts executed', result.stdout)


if __name__ == '__main__':
    unittest.main()
