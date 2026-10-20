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

The main application pipeline invokes `scripts/test-portal.sh
--validation-only`. BuildKit executes the complete validation target and keeps
its cache, but does not export and unpack a large image that this job will not
deploy. The dedicated Backstage pipeline invokes the script without that flag
and therefore also creates `bookrush/portal:local`. Its runtime image keeps the
repository read-only for the `node` user and grants write access only to
`/app/techdocs`, avoiding a recursive ownership rewrite of the dependency tree.

The runtime smoke test additionally checks local guest authentication, catalog
entities, five TechDocs sites, registered Scaffolder actions and a real
Scaffolder dry-run. It deliberately does not create a GitHub pull request.
