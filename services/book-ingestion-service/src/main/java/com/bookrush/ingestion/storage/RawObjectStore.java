package com.bookrush.ingestion.storage;

import java.io.InputStream;
import java.util.Optional;

/** Durable private storage for source snapshots before a catalog identity exists. */
public interface RawObjectStore {
  void put(String objectKey, InputStream content, long contentLength, String sha256);
  Optional<RawObjectMetadata> head(String objectKey);
  InputStream get(String objectKey);

  record RawObjectMetadata(String bucket, String objectKey, long contentLength, String etag) {}
}
