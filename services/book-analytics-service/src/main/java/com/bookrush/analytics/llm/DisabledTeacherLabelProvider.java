package com.bookrush.analytics.llm;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.HexFormat;
public final class DisabledTeacherLabelProvider implements TeacherLabelProvider {
  @Override public Result label(String text, Prompt prompt) { return Result.disabled(cacheKey(text,prompt)); }
  public static String cacheKey(String text, Prompt prompt) { try { var value=text+'|'+prompt.version()+'|'+prompt.template(); return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch(Exception e){throw new IllegalStateException(e);} }
}
