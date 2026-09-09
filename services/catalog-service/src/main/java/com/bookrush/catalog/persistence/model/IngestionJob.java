package com.bookrush.catalog.persistence.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_job", schema = "catalog")
public class IngestionJob {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "source_id", nullable = false)
  private UUID sourceId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id", insertable = false, updatable = false)
  private Source source;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private IngestionJobStatus status = IngestionJobStatus.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_type", nullable = false, length = 24)
  private IngestionJobTriggerType triggerType;

  @Column(name = "items_discovered", nullable = false)
  private Integer itemsDiscovered = 0;

  @Column(name = "items_processed", nullable = false)
  private Integer itemsProcessed = 0;

  @Column(name = "items_succeeded", nullable = false)
  private Integer itemsSucceeded = 0;

  @Column(name = "items_failed", nullable = false)
  private Integer itemsFailed = 0;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public IngestionJob() {}

  public UUID getId() { return id; }
  public void setId(UUID value) { this.id = value; }
  public UUID getSourceId() { return sourceId; }
  public void setSourceId(UUID value) { this.sourceId = value; }
  public Source getSource() { return source; }
  public Instant getStartedAt() { return startedAt; }
  public void setStartedAt(Instant value) { this.startedAt = value; }
  public Instant getFinishedAt() { return finishedAt; }
  public void setFinishedAt(Instant value) { this.finishedAt = value; }
  public IngestionJobStatus getStatus() { return status; }
  public void setStatus(IngestionJobStatus value) { this.status = value; }
  public IngestionJobTriggerType getTriggerType() { return triggerType; }
  public void setTriggerType(IngestionJobTriggerType value) { this.triggerType = value; }
  public Integer getItemsDiscovered() { return itemsDiscovered; }
  public void setItemsDiscovered(Integer value) { this.itemsDiscovered = value; }
  public Integer getItemsProcessed() { return itemsProcessed; }
  public void setItemsProcessed(Integer value) { this.itemsProcessed = value; }
  public Integer getItemsSucceeded() { return itemsSucceeded; }
  public void setItemsSucceeded(Integer value) { this.itemsSucceeded = value; }
  public Integer getItemsFailed() { return itemsFailed; }
  public void setItemsFailed(Integer value) { this.itemsFailed = value; }
  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String value) { this.errorMessage = value; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  @PreUpdate
  void touch() { updatedAt = Instant.now(); }
}
