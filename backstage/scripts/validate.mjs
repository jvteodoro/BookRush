import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';
import { entityEnvelopeSchemaValidator, SchemaValidEntityPolicy, stringifyEntityRef, parseEntityRef } from '@backstage/catalog-model';
import SwaggerParser from '@apidevtools/swagger-parser';
import { Parser } from '@asyncapi/parser';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const seen = new Set(); const entities = new Map();
const kindPolicy = new SchemaValidEntityPolicy();
async function load(file) {
  file = path.resolve(file); if (seen.has(file)) return; seen.add(file);
  for (const doc of YAML.parseAllDocuments(fs.readFileSync(file, 'utf8'))) {
    if (doc.errors.length) throw new Error(`${file}: ${doc.errors}`);
    const e = doc.toJSON(); if (!e) continue;
    entityEnvelopeSchemaValidator()(e);
    if (e.kind !== 'Template') await kindPolicy.enforce(e.kind === 'API' ? {...e, spec: {...e.spec, definition: 'validated separately'}} : e);
    const ref = stringifyEntityRef(e); if (entities.has(ref)) throw new Error(`Duplicate ${ref}`);
    entities.set(ref, { e, file });
    if (e.kind === 'Location') for (const target of e.spec.targets ?? [e.spec.target]) {
      if (/^https?:/.test(target)) throw new Error('Validate remote locations in their own repository');
      await load(path.resolve(path.dirname(file), target));
    }
    if (e.kind === 'API') {
      const def = e.spec.definition;
      if (!def?.$text) throw new Error(`${ref}: definition must reference a versioned file`);
      const apiFile = path.resolve(path.dirname(file), def.$text);
      if (e.spec.type === 'openapi') await SwaggerParser.validate(apiFile);
      if (e.spec.type === 'asyncapi') {
        const { diagnostics } = await new Parser().parse(fs.readFileSync(apiFile,'utf8'));
        if (diagnostics.some(d => d.severity === 0)) throw new Error(JSON.stringify(diagnostics));
      }
    }
    const td = e.metadata.annotations?.['backstage.io/techdocs-ref'];
    if (td?.startsWith('dir:')) {
      const dir = path.resolve(path.dirname(file), td.slice(4));
      for (const f of ['mkdocs.yml','docs/index.md']) if (!fs.existsSync(path.join(dir,f))) throw new Error(`${ref}: missing ${f}`);
    }
  }
}
await load(path.join(root,'catalog-info.yaml'));
for (const [ref,{e}] of entities) {
  const s=e.spec; const relations=[['owner','Group'],['system','System'],['domain','Domain'],['providesApis','API'],['consumesApis','API'],['dependsOn','Component']];
  for(const [field,defaultKind] of relations) for(const target of [s[field] ?? []].flat()) {
    const full=stringifyEntityRef(parseEntityRef(target,{defaultKind,defaultNamespace:'default'}));
    if(!entities.has(full)) throw new Error(`${ref}: dangling ${field} ${full}`);
  }
}
console.log(`Validated ${entities.size} entities, relationships, TechDocs roots and API contracts`);
