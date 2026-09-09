package com.bookrush.catalog.storage;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/** Vendor-neutral synchronous object operations; failures expose only a sanitized category. */
public interface ObjectStorage {
  record Location(String bucket, String key) {}
  record Metadata(long size, String contentType, String sha256) {}
  /** Creates a new object, refusing overwrite. File remains owned by the caller. */
  void upload(Location location, Path file, String contentType, String sha256);
  Optional<Metadata> head(Location location);
  /** Caller must close the stream. Missing object raises StorageFailure(NOT_FOUND). */
  InputStream download(Location location);
  /** Idempotent for a missing object. Does not remove database metadata. */
  void delete(Location location);
  /** Bearer capability: never log the returned URI or persist it as object identity. */
  URI downloadUrl(Location location, String filename, Duration ttl);
}
