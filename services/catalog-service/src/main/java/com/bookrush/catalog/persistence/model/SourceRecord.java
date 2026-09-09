package com.bookrush.catalog.persistence.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "source_record", schema = "catalog")
public class SourceRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "source_id", nullable = false)
  private UUID sourceId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id", insertable = false, updatable = false)
  private Source source;

  @Column(name = "external_id", nullable = false, columnDefinition = "text")
  private String externalId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_metadata", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> rawMetadata;

  @Column(name = "retrieved_at", nullable = false)
  private Instant retrievedAt;

  @Column(name = "content_hash", nullable = false, length = 64)
  private String contentHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public SourceRecord() {}

  public UUID getId() { return id; }
  public void setId(UUID value) { this.id = value; }
  public UUID getSourceId() { return sourceId; }
  public void setSourceId(UUID value) { this.sourceId = value; }
  public Source getSource() { return source; }
  public String getExternalId() { return externalId; }
  public void setExternalId(String value) { this.externalId = value; }
  public Map<String, Object> getRawMetadata() { return rawMetadata; }
  public void setRawMetadata(Map<String, Object> value) { this.rawMetadata = value; }
  public Instant getRetrievedAt() { return retrievedAt; }
  public void setRetrievedAt(Instant value) { this.retrievedAt = value; }
  public String getContentHash() { return contentHash; }
  public void setContentHash(String value) { this.contentHash = value; }
  public Instant getCreatedAt() { return createdAt; }
}
