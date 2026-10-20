package com.bookrush.recommendationservice.fatigue;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FatigueConfiguration.Properties.class)
public class FatigueConfiguration {
  @Bean
  ExposureFatigue exposureFatigue(Properties properties) {
    return new ExposureFatigue(new ExposureFatigue.Config(properties.version(), properties.halfLife(),
        properties.perExposurePenalty(), properties.maxPenalty()));
  }

  @ConfigurationProperties(prefix = "feed.fatigue")
  public record Properties(String version, Duration halfLife, double perExposurePenalty,
      double maxPenalty) {
    public Properties {
      version = version == null || version.isBlank() ? "fatigue-v1" : version;
      halfLife = halfLife == null ? Duration.ofDays(7) : halfLife;
      perExposurePenalty = perExposurePenalty < 0 ? .15 : perExposurePenalty;
      maxPenalty = maxPenalty < 0 ? .8 : Math.min(maxPenalty, 1);
    }
  }
}
