// Derived local catalog: resolve Git placeholders without relying on HTTP access.
import fs from 'node:fs';
import path from 'node:path';
import YAML from 'yaml';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const result = []; const seen = new Set();
function load(file) {
  if(seen.has(file)) return; seen.add(file);
  for(const doc of YAML.parseAllDocuments(fs.readFileSync(file,'utf8'))) {
    const e=doc.toJSON();if(!e)continue;
    if(e.kind==='Location') {for(const target of e.spec.targets)load(path.resolve(path.dirname(file),target)); continue;}
    if(e.kind==='API' && e.spec.definition?.$text) e.spec.definition=fs.readFileSync(path.resolve(path.dirname(file),e.spec.definition.$text),'utf8');
    e.metadata.annotations={...e.metadata.annotations,'backstage.io/source-location':`file:${file}`};
    result.push(YAML.stringify(e));
  }
}
load(path.join(root,'catalog-info.yaml'));
fs.mkdirSync(path.join(root,'backstage/.local'),{recursive:true});
fs.writeFileSync(path.join(root,'backstage/.local/catalog.yaml'),result.join('---\n'));
console.log(`Prepared ${result.length} local entities from Git sources`);
