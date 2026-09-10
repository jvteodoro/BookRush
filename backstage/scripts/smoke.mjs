import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import YAML from 'yaml';
const base=process.env.PORTAL_URL ?? 'http://127.0.0.1:17007';
async function json(url, options={}) { const r=await fetch(base+url,options); if(!r.ok) throw new Error(`${url}: ${r.status} ${(await r.text()).slice(0,500)}`);return r.json(); }
const login=await json('/api/auth/guest/refresh',{headers:{'X-Requested-With':'XMLHttpRequest'}});
const token=login.backstageIdentity?.token;assert(token,'Guest token missing');
const headers={Authorization:`Bearer ${token}`};
let entities=[];
for(let i=0;i<40;i++) {
 entities=await json('/api/catalog/entities',{headers});
 if(entities.some(e=>e.kind==='Template')&&entities.filter(e=>e.kind==='API').length===2&&entities.filter(e=>e.kind==='Component').length===7)break;
 await new Promise(r=>setTimeout(r,2000));
}
assert.equal(entities.filter(e=>e.kind==='API').length,2,'API catalog');
assert.equal(entities.filter(e=>e.kind==='Component').length,7,'Components');
for(const name of ['platform-handbook','developer-portal','catalog-service','book-ingestion-service','bookrush-frontend','jenkins','keycloak']) {
 const r=await fetch(base+`/api/techdocs/sync/default/component/${name}`,{headers});
 assert(r.ok,`TechDocs sync ${name}: ${r.status}`);
 const events=await r.text();assert(!events.includes('"type":"error"'),events);
 const page=await fetch(base+`/api/techdocs/static/docs/default/component/${name}/index.html`,{headers});assert(page.ok,`TechDocs page ${name}: ${page.status}`);
 await page.text();
}
for (const [name, pagePath] of [['platform-handbook','database/schema/'],['catalog-service','architecture/overview/'],['book-ingestion-service','architecture/overview/']]) {
 const diagram=await fetch(base+`/api/techdocs/static/docs/default/component/${name}/${pagePath}`,{headers});
 assert(diagram.ok,`TechDocs diagram page ${name}: ${diagram.status}`);
 assert((await diagram.text()).includes('<svg'),`Rendered Mermaid SVG missing from ${name}`);
}
const actions=await json('/api/scaffolder/v2/actions',{headers});assert(actions.some(a=>a.id==='fetch:template'));assert(actions.some(a=>a.id==='publish:github:pull-request'));
const template=YAML.parse(fs.readFileSync('templates/java-spring-service/template.yaml','utf8'));
const directoryContents=[];
function walk(dir){for(const entry of fs.readdirSync(dir,{withFileTypes:true})){const file=path.join(dir,entry.name);if(entry.isDirectory())walk(file);else directoryContents.push({path:path.relative('templates/java-spring-service',file),base64Content:fs.readFileSync(file).toString('base64')});}}
walk('templates/java-spring-service/skeleton');
// Dry-run the real fetch action. Publishing a PR is deliberately not executed in tests.
template.spec.steps=template.spec.steps.filter(s=>s.id==='fetch');template.spec.output={};
const result=await json('/api/scaffolder/v2/dry-run',{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:JSON.stringify({template,values:{name:'smoke-service',description:'Smoke service from template',owner:'group:default/book-platform',system:'book-catalog',lifecycle:'experimental',apiType:'rest',database:'none',messaging:'',repoUrl:'github.com?owner=example&repo=example'},directoryContents})});
assert(result.directoryContents?.some(f=>f.path==='pom.xml'),'Scaffolder produced pom.xml');
console.log(`Runtime passed: ${entities.length} entities, seven TechDocs sites with rendered diagrams, real Scaffolder dry-run`);
