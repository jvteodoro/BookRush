#!/usr/bin/env python3
"""Publishing deployment config: no secrets in stdout, private temp file, no protected-file edits."""
import contextlib
import io
from pathlib import Path
import runpy
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
prepare=runpy.run_path(str(ROOT/'infrastructure/jenkins/prepare-publishing-env.py'))['prepare']

class PublishingConfigTest(unittest.TestCase):
    def fixture(self, content, environment):
        with tempfile.TemporaryDirectory() as directory:
            source=Path(directory)/'protected'; target=Path(directory)/'runtime'
            source.write_text(content)
            output=io.StringIO()
            with contextlib.redirect_stdout(output): prepare(source,target,environment)
            self.assertEqual(source.read_text(),content)
            self.assertEqual(target.stat().st_mode & 0o777,0o600)
            self.assertEqual(output.getvalue(),'')
            return dict(line.split('=',1) for line in target.read_text().splitlines())
    def test_defaults_enable_storage_and_generate_private_shared_credential(self):
        values=self.fixture('',{})
        self.assertEqual(values['PUBLISHER_STORAGE_ENABLED'],'true')
        self.assertRegex(values['PUBLISHER_INGESTION_CALLBACK_TOKEN'],r'^[0-9a-f]{64}$')
    def test_configured_credential_and_explicit_opt_out_are_preserved(self):
        values=self.fixture('PUBLISHER_STORAGE_ENABLED=false\nPUBLISHER_INGESTION_CALLBACK_TOKEN=fixture-token\n',{})
        self.assertEqual(values['PUBLISHER_STORAGE_ENABLED'],'false')
        self.assertEqual(values['PUBLISHER_INGESTION_CALLBACK_TOKEN'],'fixture-token')
    def test_environment_override_is_respected(self):
        self.assertEqual(self.fixture('PUBLISHER_INGESTION_CALLBACK_TOKEN=old\n',{'PUBLISHER_INGESTION_CALLBACK_TOKEN':'fixture-new'})['PUBLISHER_INGESTION_CALLBACK_TOKEN'],'fixture-new')
    def test_invalid_flag_does_not_silently_disable_publication(self):
        with self.assertRaises(ValueError): self.fixture('',{'PUBLISHER_STORAGE_ENABLED':'invalid'})

if __name__=='__main__':unittest.main()
