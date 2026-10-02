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

When S3-compatible storage credentials are configured, each manifest snapshot
is also written to `reader-ready/{bookId}/manifest-{sha256}.json` in the public
artifact bucket. The key is content-addressed and includes the format metadata,
so recomputation is idempotent. If storage is unavailable, the API remains
usable for local development but reports the manifest as an ephemeral response;
production should configure storage and monitor the persistence failure logs.

`GET /api/v1/content/books/{bookId}/chapters` exposes the persisted chapter
projection (code-point offsets, hierarchy and text-version lineage) from the
catalog owner. It does not expose database credentials or bypass asset
authorization.

The chapter proxy calls catalog with `X-Content-Service-Token`. Configure
`CATALOG_READER_INTERNAL_TOKEN` and `CATALOG_SERVICE_TOKEN` from the same
protected environment secret. The catalog validates this token and streams the
exact approved text version to the content service; the browser cannot call
that internal route directly. This avoids making a presigned URL containing a
container-local `localhost` endpoint part of the reader flow.

The manifest write to S3 is content-addressed. The reader response remains
available from the catalog projection while the artifact store is temporarily
unavailable. The SeaweedFS single-node development runtime reserves 256 volume
slots because raw, processing, ML and public bucket layouts share one volume
server; existing volumes are preserved when this limit is increased.
