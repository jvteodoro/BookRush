package com.bookrush.catalog.persistence.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_item", schema = "catalog")
public class IngestionItem {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "ingestion_job_id", nullable = false)
  private UUID ingestionJobId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ingestion_job_id", insertable = false, updatable = false)
  private IngestionJob ingestionJob;

  @Column(name = "source_id", nullable = false)
  private UUID sourceId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id", insertable = false, updatable = false)
  private Source source;

  @Column(name = "external_identifier", nullable = false, columnDefinition = "text")
  private String externalIdentifier;

  @Column(name = "attempt_number", nullable = false)
  private Integer attemptNumber = 1;

  @Column(name = "source_record_id")
  private UUID sourceRecordId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_record_id", insertable = false, updatable = false)
  private SourceRecord sourceRecord;

  @Column(name = "book_id")
  private UUID bookId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "book_id", insertable = false, updatable = false)
  private Book book;

  @Column(name = "edition_id")
  private UUID editionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "edition_id", insertable = false, updatable = false)
  private Edition edition;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private IngestionItemStatus status = IngestionItemStatus.PENDING;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "error_code", columnDefinition = "text")
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public IngestionItem() {}

  public UUID getId() { return id; }
  public void setId(UUID value) { this.id = value; }
  public UUID getIngestionJobId() { return ingestionJobId; }
  public void setIngestionJobId(UUID value) { this.ingestionJobId = value; }
  public IngestionJob getIngestionJob() { return ingestionJob; }
  public UUID getSourceId() { return sourceId; }
  public void setSourceId(UUID value) { this.sourceId = value; }
  public Source getSource() { return source; }
  public String getExternalIdentifier() { return externalIdentifier; }
  public void setExternalIdentifier(String value) { this.externalIdentifier = value; }
  public Integer getAttemptNumber() { return attemptNumber; }
  public void setAttemptNumber(Integer value) { this.attemptNumber = value; }
  public UUID getSourceRecordId() { return sourceRecordId; }
  public void setSourceRecordId(UUID value) { this.sourceRecordId = value; }
  public SourceRecord getSourceRecord() { return sourceRecord; }
  public UUID getBookId() { return bookId; }
  public void setBookId(UUID value) { this.bookId = value; }
  public Book getBook() { return book; }
  public UUID getEditionId() { return editionId; }
  public void setEditionId(UUID value) { this.editionId = value; }
  public Edition getEdition() { return edition; }
  public IngestionItemStatus getStatus() { return status; }
  public void setStatus(IngestionItemStatus value) { this.status = value; }
  public Instant getStartedAt() { return startedAt; }
  public void setStartedAt(Instant value) { this.startedAt = value; }
  public Instant getFinishedAt() { return finishedAt; }
  public void setFinishedAt(Instant value) { this.finishedAt = value; }
  public String getErrorCode() { return errorCode; }
  public void setErrorCode(String value) { this.errorCode = value; }
  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String value) { this.errorMessage = value; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  @PreUpdate
  void touch() { updatedAt = Instant.now(); }
}
