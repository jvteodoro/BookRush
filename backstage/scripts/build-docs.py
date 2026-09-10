"""Validate local Markdown links and required pages, then build each TechDocs site."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from urllib.parse import unquote, urlsplit
root = Path(__file__).resolve().parents[2]
mkdocs = shutil.which('mkdocs') or str(root / 'backstage/.venv/bin/mkdocs')
if not Path(mkdocs).is_file() and shutil.which(mkdocs) is None:
    raise SystemExit('mkdocs is required; install backstage/requirements-docs.txt or put it on PATH')
sites = ['.', 'backstage', 'frontend', 'services/catalog-service', 'services/book-ingestion-service', 'backstage/catalog/jenkins-docs', 'backstage/catalog/keycloak-docs']
required = ['index.md','architecture/overview.md','architecture/components.md','architecture/data-flow.md','architecture/deployment.md','domain/domain-model.md','domain/entities.md','domain/business-rules.md','database/schema.md','database/migrations.md','database/indexes.md','development/local-development.md','development/testing.md','development/contributing.md','operations/observability.md','operations/runbook.md','operations/troubleshooting.md','adr/README.md']
for site in sites:
    docs = root / site / 'docs'
    if site.startswith('services/'):
        for f in required:
            assert (docs / f).is_file(), f'{site}: missing {f}'
    for f in docs.rglob('*.md'):
        content = re.sub(r'```.*?```', '', f.read_text(), flags=re.S)
        for target in re.findall(r'\[[^\]]*\]\(([^)\s]+)(?:\s+[^)]*)?\)', content):
            url = urlsplit(target.strip('<>'))
            if url.scheme or not url.path or url.path.startswith('/'):
                continue
            dest = (f.parent / unquote(url.path)).resolve()
            if not dest.exists():
                raise SystemExit(f'{f.relative_to(root)}: broken link {target}')
    with tempfile.TemporaryDirectory(prefix='bookrush-techdocs-') as tmp:
        subprocess.run([mkdocs,'build','--strict','-f',str(root/site/'mkdocs.yml'),'-d',tmp],check=True)
print('All required documents, local links and TechDocs builds passed')
