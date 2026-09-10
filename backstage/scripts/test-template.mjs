import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
import nunjucks from 'nunjucks';
import YAML from 'yaml';
import { entityEnvelopeSchemaValidator } from '@backstage/catalog-model';
const env = new nunjucks.Environment(undefined, { autoescape: false, throwOnUndefined: true, tags: { variableStart: '${{', variableEnd: '}}' } });
const skeleton = path.resolve('templates/java-spring-service/skeleton');
for (const apiType of ['rest','events','both','none']) for (const database of ['none','resource:default/books-postgres']) {
  const values={name:'sample-service',description:'A sample generated service',owner:'group:default/book-platform',system:'book-catalog',lifecycle:'experimental',apiType,database,messaging:'',repoUrl:'github.com?owner=example&repo=example'};
  const output=fs.mkdtempSync(path.join(os.tmpdir(),'bookrush-template-'));
  function render(dir) { for(const item of fs.readdirSync(dir,{withFileTypes:true})) {
    const src=path.join(dir,item.name); if(item.isDirectory()) {render(src);continue;}
    const text=env.renderString(fs.readFileSync(src,'utf8'),{values});
    assert(!text.includes('${{'),`Unresolved variable ${src}`);
    const dest=path.join(output,path.relative(skeleton,src));fs.mkdirSync(path.dirname(dest),{recursive:true});fs.writeFileSync(dest,text);
    if(src.endsWith('catalog-info.yaml')) entityEnvelopeSchemaValidator()(YAML.parse(text));
  }}
  render(skeleton);
  for(const required of ['pom.xml','Dockerfile','catalog-info.yaml','mkdocs.yml','docs/index.md','api/README.md','src/main/java/com/bookrush/service/Application.java']) assert(fs.existsSync(path.join(output,required)));
  fs.rmSync(output,{recursive:true});
}
console.log('Template renders all 8 API/database combinations');
