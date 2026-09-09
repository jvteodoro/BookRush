package com.bookrush.ingestion.jobs;

import com.bookrush.ingestion.source.SnapshotDownloader;
import java.net.URI;
import java.nio.file.Path;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Job facade that delegates bounded, resumable transfers and returns the immutable snapshot path. */
@Service
public class AssetDownloadJob {
  private final ObjectProvider<SnapshotDownloader> downloader;
  public AssetDownloadJob(ObjectProvider<SnapshotDownloader> downloader) { this.downloader = downloader; }
  public SnapshotDownloader.SnapshotResult download(URI uri, Path destination, String objectKey) throws Exception {
    var delegate = downloader.getIfAvailable(() -> { throw new IllegalStateException("snapshot downloader is not configured"); });
    return delegate.download(uri, destination, objectKey, null, null);
  }
}
