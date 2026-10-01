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
