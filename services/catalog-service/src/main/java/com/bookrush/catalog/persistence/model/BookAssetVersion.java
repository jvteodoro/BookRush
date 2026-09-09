package com.bookrush.catalog.persistence.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "book_asset_version", schema = "catalog")
public class BookAssetVersion {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "book_asset_id", nullable = false)
  private UUID bookAssetId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "book_asset_id", insertable = false, updatable = false)
  private BookAsset bookAsset;

  @Column(name = "version_number", nullable = false)
  private Integer versionNumber;

  @Column(name = "storage_provider", nullable = false, length = 24)
  @Enumerated(EnumType.STRING)
  private StorageProvider storageProvider;

  @Column(name = "bucket", nullable = false, columnDefinition = "text")
  private String bucket;

  @Column(name = "object_key", nullable = false, columnDefinition = "text")
  private String objectKey;

  @Column(name = "original_filename", columnDefinition = "text")
  private String originalFilename;

  @Column(name = "content_type", columnDefinition = "text")
  private String contentType;

  @Column(name = "size_bytes")
  private Long sizeBytes;

  @Column(name = "sha256", length = 64)
  private String sha256;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private BookAssetVersionStatus status = BookAssetVersionStatus.PENDING_UPLOAD;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Column(name = "ingestion_item_id")
  private UUID ingestionItemId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ingestion_item_id", insertable = false, updatable = false)
  private IngestionItem ingestionItem;

  public BookAssetVersion() {}

  public UUID getId() { return id; }
  public void setId(UUID value) { this.id = value; }
  public UUID getBookAssetId() { return bookAssetId; }
  public void setBookAssetId(UUID value) { this.bookAssetId = value; }
  public BookAsset getBookAsset() { return bookAsset; }
  public Integer getVersionNumber() { return versionNumber; }
  public void setVersionNumber(Integer value) { this.versionNumber = value; }
  public StorageProvider getStorageProvider() { return storageProvider; }
  public void setStorageProvider(StorageProvider value) { this.storageProvider = value; }
  public String getBucket() { return bucket; }
  public void setBucket(String value) { this.bucket = value; }
  public String getObjectKey() { return objectKey; }
  public void setObjectKey(String value) { this.objectKey = value; }
  public String getOriginalFilename() { return originalFilename; }
  public void setOriginalFilename(String value) { this.originalFilename = value; }
  public String getContentType() { return contentType; }
  public void setContentType(String value) { this.contentType = value; }
  public Long getSizeBytes() { return sizeBytes; }
  public void setSizeBytes(Long value) { this.sizeBytes = value; }
  public String getSha256() { return sha256; }
  public void setSha256(String value) { this.sha256 = value; }
  public BookAssetVersionStatus getStatus() { return status; }
  public void setStatus(BookAssetVersionStatus value) { this.status = value; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public UUID getIngestionItemId() { return ingestionItemId; }
  public void setIngestionItemId(UUID value) { this.ingestionItemId = value; }
  public IngestionItem getIngestionItem() { return ingestionItem; }

  @PreUpdate
  void touch() { updatedAt = Instant.now(); }
}
