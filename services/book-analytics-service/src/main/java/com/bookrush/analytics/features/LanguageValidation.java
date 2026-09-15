package com.bookrush.analytics.features;

import java.util.*;

/** Language validation boundary. The concrete fastText adapter is supplied only when lid.176.bin is prepared. */
public final class LanguageValidation {
  private LanguageValidation() {}
  public enum Status { VALID, UNSUPPORTED, MODEL_UNAVAILABLE, INVALID_INPUT }
  public record Result(String language,double confidence,Status status,String warning) {}
  public interface Validator { Result detect(String text); }
  public static Validator unavailable() { return text -> new Result(null,0,Status.MODEL_UNAVAILABLE,"fastText lid.176.bin is not prepared"); }
  public static Result acceptStructural(String declaredLanguage) {
    if (declaredLanguage==null||declaredLanguage.isBlank()) return new Result(null,0,Status.INVALID_INPUT,"language is required");
    return Set.of("en","pt").contains(declaredLanguage.toLowerCase(Locale.ROOT)) ? new Result(declaredLanguage.toLowerCase(Locale.ROOT),1,Status.VALID,null) : new Result(declaredLanguage.toLowerCase(Locale.ROOT),1,Status.UNSUPPORTED,"linguistic models are limited to en and pt");
  }
}
