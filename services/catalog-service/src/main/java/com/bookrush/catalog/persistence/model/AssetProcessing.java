package com.bookrush.catalog.persistence.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "asset_processing", schema = "catalog")
public class AssetProcessing {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "input_asset_version_id", nullable = false)
  private UUID inputAssetVersionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "input_asset_version_id", insertable = false, updatable = false)
  private BookAssetVersion inputAssetVersion;

  @Column(name = "output_asset_version_id")
  private UUID outputAssetVersionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "output_asset_version_id", insertable = false, updatable = false)
  private BookAssetVersion outputAssetVersion;

  @Column(name = "processing_type", nullable = false, length = 64)
  private String processingType;

  @Column(name = "processor", nullable = false, columnDefinition = "text")
  private String processor;

  @Column(name = "processor_version", nullable = false, columnDefinition = "text")
  private String processorVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private AssetProcessingStatus status = AssetProcessingStatus.PENDING;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> metadata = new java.util.HashMap<>();

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public AssetProcessing() {}

  public UUID getId() { return id; }
  public void setId(UUID value) { this.id = value; }
  public UUID getInputAssetVersionId() { return inputAssetVersionId; }
  public void setInputAssetVersionId(UUID value) { this.inputAssetVersionId = value; }
  public BookAssetVersion getInputAssetVersion() { return inputAssetVersion; }
  public UUID getOutputAssetVersionId() { return outputAssetVersionId; }
  public void setOutputAssetVersionId(UUID value) { this.outputAssetVersionId = value; }
  public BookAssetVersion getOutputAssetVersion() { return outputAssetVersion; }
  public String getProcessingType() { return processingType; }
  public void setProcessingType(String value) { this.processingType = value; }
  public String getProcessor() { return processor; }
  public void setProcessor(String value) { this.processor = value; }
  public String getProcessorVersion() { return processorVersion; }
  public void setProcessorVersion(String value) { this.processorVersion = value; }
  public AssetProcessingStatus getStatus() { return status; }
  public void setStatus(AssetProcessingStatus value) { this.status = value; }
  public Instant getStartedAt() { return startedAt; }
  public void setStartedAt(Instant value) { this.startedAt = value; }
  public Instant getFinishedAt() { return finishedAt; }
  public void setFinishedAt(Instant value) { this.finishedAt = value; }
  public Map<String, Object> getMetadata() { return metadata; }
  public void setMetadata(Map<String, Object> value) { this.metadata = value; }
  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String value) { this.errorMessage = value; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  @PreUpdate
  void touch() { updatedAt = Instant.now(); }
}
