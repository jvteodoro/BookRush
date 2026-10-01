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

`GET /api/v1/content/books/{bookId}/publication.json` emits a Readium Web
Publication Manifest whose reading order is the persisted chapter projection.
Each chapter is served as HTML by
`/api/v1/content/books/{bookId}/chapters/{chapterId}/content`; the service
resolves the exact normalized text version and slices Unicode code-point
offsets. Storage URLs remain short-lived capabilities and are never returned
as permanent links.

The web client mounts Readium's `WebPubNavigator`. Books without a projected
text version return an empty reading order and the UI reports that reader-ready
content is unavailable; it never substitutes sample paragraphs.

`GET /api/v1/content/books/{bookId}/chapters` exposes the persisted chapter
projection (code-point offsets, hierarchy and text-version lineage) from the
catalog owner. It does not expose database credentials or bypass asset
authorization.

The chapter proxy calls catalog with `X-Content-Service-Token`. Configure
`CATALOG_READER_INTERNAL_TOKEN` and `CATALOG_SERVICE_TOKEN` from the same
protected environment secret. The catalog validates this token before issuing
a URL for a processing text asset; the browser cannot call that route directly.
