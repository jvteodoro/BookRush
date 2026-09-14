package com.bookrush.platform.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bookrush.security")
public class BookrushSecurityProperties {
  private boolean enabled;
  private String issuer;
  private String jwkSetUri;
  private String audience;
  private Duration clockSkew = Duration.ofSeconds(30);

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public String getIssuer() { return issuer; }
  public void setIssuer(String issuer) { this.issuer = issuer; }
  public String getJwkSetUri() { return jwkSetUri; }
  public void setJwkSetUri(String jwkSetUri) { this.jwkSetUri = jwkSetUri; }
  public String getAudience() { return audience; }
  public void setAudience(String audience) { this.audience = audience; }
  public Duration getClockSkew() { return clockSkew; }
  public void setClockSkew(Duration clockSkew) { this.clockSkew = clockSkew; }
}
