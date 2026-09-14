package com.bookrush.analytics.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;

/** Verifies the exact normalized text bytes before analytics touches offsets. */
public final class TextAssetReader {
  private TextAssetReader() {}

  public static String readNormalized(InputStream input, long expectedSize, String expectedSha256) throws IOException {
    if (expectedSize < 0 || expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("physical metadata is incomplete or invalid");
    var bytes = input.readAllBytes();
    if (bytes.length != expectedSize) throw new IntegrityException("TEXT_SIZE_MISMATCH");
    var actual = HexFormat.of().formatHex(sha256(bytes));
    if (!actual.equals(expectedSha256)) throw new IntegrityException("TEXT_HASH_MISMATCH");
    var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
    final String text;
    try { text = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString(); }
    catch (Exception e) { throw new IntegrityException("TEXT_NOT_UTF8", e); }
    if (!Normalizer.isNormalized(text, Normalizer.Form.NFC)) throw new IntegrityException("TEXT_NOT_NFC");
    if (text.indexOf('\r') >= 0) throw new IntegrityException("TEXT_NOT_LF");
    return text;
  }

  public static byte[] sha256(byte[] bytes) {
    try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }

  public static int codePointLength(String text) { return text.codePointCount(0, text.length()); }
  public static final class IntegrityException extends IOException {
    public IntegrityException(String code) { super(code); }
    public IntegrityException(String code, Throwable cause) { super(code, cause); }
  }
}
