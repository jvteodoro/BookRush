package com.bookrush.platform.core;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** SHA-256 for bounded streams; it never buffers the complete input. */
public final class Sha256 {
  private static final int BUFFER_SIZE = 8192;

  private Sha256() {}

  public static String digest(InputStream input, long maxBytes) throws IOException {
    Objects.requireNonNull(input, "input");
    if (maxBytes < 0) {
      throw new IllegalArgumentException("maxBytes must be non-negative");
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[BUFFER_SIZE];
      long total = 0;
      try (DigestInputStream stream = new DigestInputStream(input, digest)) {
        int read;
        while ((read = stream.read(buffer)) != -1) {
          total += read;
          if (total > maxBytes) {
            throw new IOException("input exceeds configured limit");
          }
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is required by the JDK", impossible);
    }
  }
}
