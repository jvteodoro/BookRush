package com.bookrush.catalog.storage;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

public final class S3ObjectStorage implements ObjectStorage {
  private static final Logger log=LoggerFactory.getLogger(S3ObjectStorage.class);
  private final S3Client client;
  private final S3Presigner signer;
  private final MeterRegistry metrics;
  public S3ObjectStorage(S3Client client,S3Presigner signer,MeterRegistry metrics) {
    this.client=client; this.signer=signer; this.metrics=metrics;
  }
  private <T> T call(String operation,Location location,Supplier<T> action) {
    long start=System.nanoTime(); String result="ok";
    try { return action.get(); }
    catch(SdkException e) {
      result="error";
      var kind=e instanceof S3Exception s && s.statusCode()==404 ? StorageFailure.Kind.NOT_FOUND :
          e instanceof S3Exception s && (s.statusCode()==409 || s.statusCode()==412) ? StorageFailure.Kind.CONFLICT : StorageFailure.Kind.UNAVAILABLE;
      throw new StorageFailure(kind);
    } finally {
      metrics.timer("bookrush.storage", "operation",operation,"result",result).record(System.nanoTime()-start,java.util.concurrent.TimeUnit.NANOSECONDS);
      // Never log SDK exceptions, request headers or presigned URLs.
      log.info("storage operation={} result={} bucket={} key={} durationMs={}",operation,result,location.bucket(),location.key(),(System.nanoTime()-start)/1_000_000);
    }
  }
  public void upload(Location loc,Path file,String mime,String hash) {
    call("upload",loc,()->client.putObject(r->r.bucket(loc.bucket()).key(loc.key()).contentType(mime)
        .ifNoneMatch("*").metadata(Map.of("sha256",hash))
        .checksumSHA256(java.util.Base64.getEncoder().encodeToString(java.util.HexFormat.of().parseHex(hash))),RequestBody.fromFile(file)));
  }
  public Optional<Metadata> head(Location loc) {
    try {
      return call("head",loc,()->{
        var h=client.headObject(r->r.bucket(loc.bucket()).key(loc.key()));
        return Optional.of(new Metadata(h.contentLength(),h.contentType(),h.metadata().get("sha256")));
      });
    } catch(StorageFailure e) { if(e.kind()==StorageFailure.Kind.NOT_FOUND) return Optional.empty(); throw e; }
  }
  public InputStream download(Location loc) {
    return call("download",loc,()->client.getObject(r->r.bucket(loc.bucket()).key(loc.key())));
  }
  public void delete(Location loc) {
    call("delete",loc,()->client.deleteObject(r->r.bucket(loc.bucket()).key(loc.key())));
  }
  public URI downloadUrl(Location loc,String filename,Duration ttl) {
    if(ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(1))>0) throw new IllegalArgumentException("Invalid TTL");
    String safe=ObjectKeyBuilder.filename(filename);
    return call("presign",loc,()->URI.create(signer.presignGetObject(r->r.signatureDuration(ttl)
        .getObjectRequest(g->g.bucket(loc.bucket()).key(loc.key()).responseContentDisposition("attachment; filename=\""+safe+"\"")))
        .url().toExternalForm()));
  }
}
