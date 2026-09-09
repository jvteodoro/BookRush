package com.bookrush.catalog.persistence.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "book_asset", schema = "catalog")
public class BookAsset {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "book_id", nullable = false)
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
  @Column(name = "asset_type", nullable = false, length = 24)
  private BookAssetAssetType assetType;

  @Enumerated(EnumType.STRING)
  @Column(name = "asset_role", nullable = false, length = 24)
  private BookAssetAssetRole assetRole;

  @Column(name = "source_id", nullable = false)
  private UUID sourceId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id", insertable = false, updatable = false)
  private Source source;

  @Column(name = "license_id")
  private UUID licenseId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "license_id", insertable = false, updatable = false)
  private License license;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private BookAssetStatus status = BookAssetStatus.ACTIVE;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public BookAsset() {}

  public UUID getId() { return id; }
  public void setId(UUID value) { this.id = value; }
  public UUID getBookId() { return bookId; }
  public void setBookId(UUID value) { this.bookId = value; }
  public Book getBook() { return book; }
  public UUID getEditionId() { return editionId; }
  public void setEditionId(UUID value) { this.editionId = value; }
  public Edition getEdition() { return edition; }
  public BookAssetAssetType getAssetType() { return assetType; }
  public void setAssetType(BookAssetAssetType value) { this.assetType = value; }
  public BookAssetAssetRole getAssetRole() { return assetRole; }
  public void setAssetRole(BookAssetAssetRole value) { this.assetRole = value; }
  public UUID getSourceId() { return sourceId; }
  public void setSourceId(UUID value) { this.sourceId = value; }
  public Source getSource() { return source; }
  public UUID getLicenseId() { return licenseId; }
  public void setLicenseId(UUID value) { this.licenseId = value; }
  public License getLicense() { return license; }
  public BookAssetStatus getStatus() { return status; }
  public void setStatus(BookAssetStatus value) { this.status = value; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  @PreUpdate
  void touch() { updatedAt = Instant.now(); }
}
