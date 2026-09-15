package com.bookrush.analytics.models;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/** Resolves pre-fetched model artifacts without downloading during application startup. */
public final class ModelArtifactManager {
  public record Artifact(String id, Path path, String sha256) {}
  private final Path cache; private final boolean offline;
  public ModelArtifactManager(Path cache, boolean offline) { this.cache=cache; this.offline=offline; }
  public Artifact require(String id, String fileName, String expectedSha256) {
    if (id==null || id.isBlank() || fileName==null || fileName.isBlank()) throw new IllegalArgumentException("model id and file name are required");
    Path path=cache.resolve(fileName).normalize();
    if (!path.startsWith(cache.toAbsolutePath().normalize())) throw new IllegalArgumentException("artifact path escapes cache");
    if (!Files.isRegularFile(path)) throw new IllegalStateException((offline ? "ANALYTICS_OFFLINE=true and artifact is missing: " : "artifact is missing; fetch explicitly before startup: ") + id);
    String actual=sha256(path);
    if (expectedSha256!=null && !expectedSha256.isBlank() && !actual.equalsIgnoreCase(expectedSha256)) throw new IllegalStateException("checksum mismatch for model artifact " + id);
    return new Artifact(id,path,actual);
  }
  private static String sha256(Path p) {
    try (InputStream in=Files.newInputStream(p)) { var digest=MessageDigest.getInstance("SHA-256"); byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1) digest.update(b,0,n); return HexFormat.of().formatHex(digest.digest()); }
    catch (IOException e) { throw new UncheckedIOException("cannot read model artifact",e); }
    catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}
