# Book Content Service

Serves reader-facing content metadata and keeps content ownership separate from the catalog and analytics schemas. The service resolves catalog metadata through the catalog HTTP contract and does not copy catalog tables.

The Docker smoke test starts this service against disposable PostgreSQL and
checks actuator readiness. Its YAML keeps `bookrush.security` and
`bookrush.catalog-url` in one mapping so a fresh container cannot fail during
configuration parsing.

Reader clients use `/api/v1/content/books/{bookId}/assets` and the
`download-url` child endpoint. The service delegates asset authorization and
short-lived URL generation to catalog-service, so it never exposes permanent
object-store URLs or copies catalog tables.

`GET /api/v1/content/books/{bookId}/publication.json` emits a minimal Readium
Web Publication Manifest when an approved public EPUB exists. The manifest
contains only a short-lived download capability; it is not a permanent object
storage URL. Full chapter navigation still depends on the EPUB artifact and a
Readium navigator in the client.

`GET /api/v1/content/books/{bookId}/chapters` exposes the persisted chapter
projection (code-point offsets, hierarchy and text-version lineage) from the
catalog owner. It does not expose database credentials or bypass asset
authorization.
