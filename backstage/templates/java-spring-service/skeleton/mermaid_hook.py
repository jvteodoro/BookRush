"""Render Mermaid locally at build time; no external diagram service or authored SVG."""
import hashlib
import html
import os
import re
import subprocess
import tempfile
from pathlib import Path

def on_page_markdown(markdown, page, config, files):
    def render(match):
        source = match.group(1)
        digest = hashlib.sha256(source.encode()).hexdigest()
        cache = Path(tempfile.gettempdir()) / 'bookrush-mermaid'
        cache.mkdir(exist_ok=True)
        output = cache / (digest + '.svg')
        if not output.exists():
            input_file = cache / (digest + '.mmd')
            input_file.write_text(source)
            chrome = cache / 'puppeteer.json'
            import json
            options = {'args': ['--no-sandbox', '--disable-dev-shm-usage']}
            if os.environ.get('CHROME_BIN'):
                options['executablePath'] = os.environ['CHROME_BIN']
            chrome.write_text(json.dumps(options))
            subprocess.run(['mmdc', '-i', str(input_file), '-o', str(output), '-p', str(chrome), '-b', 'transparent'], check=True)
        # Inline SVG survives TechDocs sanitization without an external script.
        return '\n<div class="mermaid-diagram">' + output.read_text() + '</div>\n'
    return re.sub(r'```mermaid\s*\n(.*?)```', render, markdown, flags=re.S)
