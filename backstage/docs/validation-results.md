# Validation results

The portal validation is reproducible from the repository. The checks run
without production data or external Git credentials:

```bash
yarn validate:catalog
yarn validate:template
python3 scripts/build-docs.py
yarn tsc --noEmit
yarn lint:all
yarn test --watchAll=false --passWithNoTests
```

The catalog and template checks read authored YAML and template skeletons from
Git. The documentation check builds every MkDocs site in strict mode and
renders Mermaid diagrams with the local hook. OpenAPI and AsyncAPI documents
are parsed during catalog validation. Publishing TechDocs to an external
S3-compatible bucket is a separate operation and requires the credentials in
[configuration](configuration.md).

The runtime smoke test additionally checks local guest authentication, catalog
entities, five TechDocs sites, registered Scaffolder actions and a real
Scaffolder dry-run. It deliberately does not create a GitHub pull request.
