package com.bookrush.analytics.config;

import com.bookrush.analytics.excerpts.ExcerptCandidateGenerator;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Versioned, explicit pilot configuration for deterministic V2 candidates. */
@ConfigurationProperties(prefix = "analytics.worker.v2")
public record AnalyticsV2GeneratorProperties(
    String version, int minWords, int maxWords, boolean crossChapter, List<Window> windows) {

  public AnalyticsV2GeneratorProperties {
    windows = List.copyOf(windows == null ? List.of() : windows);
  }

  public ExcerptCandidateGenerator.V2Config candidateConfig() {
    return new ExcerptCandidateGenerator.V2Config(
        minWords,
        windows.stream()
            .map(
                window ->
                    new ExcerptCandidateGenerator.WindowProfile(
                        window.targetWords(), window.sentenceStride()))
            .toList(),
        maxWords,
        crossChapter,
        version);
  }

  public record Window(int targetWords, int sentenceStride) {}
}
