package com.bookrush.catalog.persistence.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source", schema = "catalog")
public class Source {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "code", nullable = false, length = 64)
  private String code;

  @Column(name = "name", nullable = false, columnDefinition = "text")
  private String name;

  @Column(name = "base_url", columnDefinition = "text")
  private String baseUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 32)
  private SourceSourceType sourceType;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public Source() {}

  public UUID getId() { return id; }
  public void setId(UUID value) { this.id = value; }
  public String getCode() { return code; }
  public void setCode(String value) { this.code = value; }
  public String getName() { return name; }
  public void setName(String value) { this.name = value; }
  public String getBaseUrl() { return baseUrl; }
  public void setBaseUrl(String value) { this.baseUrl = value; }
  public SourceSourceType getSourceType() { return sourceType; }
  public void setSourceType(SourceSourceType value) { this.sourceType = value; }
  public Boolean getIsActive() { return isActive; }
  public void setIsActive(Boolean value) { this.isActive = value; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  @PreUpdate
  void touch() { updatedAt = Instant.now(); }
}
