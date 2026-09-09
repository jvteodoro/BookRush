package com.bookrush.catalog.persistence.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "license", schema = "catalog")
public class License {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "code", nullable = false, length = 64)
  private String code;

  @Column(name = "name", nullable = false, columnDefinition = "text")
  private String name;

  @Column(name = "url", columnDefinition = "text")
  private String url;

  @Column(name = "commercial_use_allowed")
  private Boolean commercialUseAllowed;

  @Column(name = "modification_allowed")
  private Boolean modificationAllowed;

  @Column(name = "redistribution_allowed")
  private Boolean redistributionAllowed;

  @Column(name = "attribution_required")
  private Boolean attributionRequired;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public License() {}

  public UUID getId() { return id; }
  public void setId(UUID value) { this.id = value; }
  public String getCode() { return code; }
  public void setCode(String value) { this.code = value; }
  public String getName() { return name; }
  public void setName(String value) { this.name = value; }
  public String getUrl() { return url; }
  public void setUrl(String value) { this.url = value; }
  public Boolean getCommercialUseAllowed() { return commercialUseAllowed; }
  public void setCommercialUseAllowed(Boolean value) { this.commercialUseAllowed = value; }
  public Boolean getModificationAllowed() { return modificationAllowed; }
  public void setModificationAllowed(Boolean value) { this.modificationAllowed = value; }
  public Boolean getRedistributionAllowed() { return redistributionAllowed; }
  public void setRedistributionAllowed(Boolean value) { this.redistributionAllowed = value; }
  public Boolean getAttributionRequired() { return attributionRequired; }
  public void setAttributionRequired(Boolean value) { this.attributionRequired = value; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  @PreUpdate
  void touch() { updatedAt = Instant.now(); }
}
