package com.bookrush.catalog.persistence.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "book", schema = "catalog")
public class Book {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "canonical_title", nullable = false, columnDefinition = "text")
  private String canonicalTitle;

  @Column(name = "original_title", columnDefinition = "text")
  private String originalTitle;

  @Column(name = "original_language", length = 3)
  private String originalLanguage;

  @Column(name = "first_publication_year")
  private Short firstPublicationYear;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private BookStatus status = BookStatus.REVIEW_REQUIRED;

  @Column(name = "merged_into_id")
  private UUID mergedIntoId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "merged_into_id", insertable = false, updatable = false)
  private Book mergedInto;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public Book() {}

  public UUID getId() { return id; }
  public void setId(UUID value) { this.id = value; }
  public String getCanonicalTitle() { return canonicalTitle; }
  public void setCanonicalTitle(String value) { this.canonicalTitle = value; }
  public String getOriginalTitle() { return originalTitle; }
  public void setOriginalTitle(String value) { this.originalTitle = value; }
  public String getOriginalLanguage() { return originalLanguage; }
  public void setOriginalLanguage(String value) { this.originalLanguage = value; }
  public Short getFirstPublicationYear() { return firstPublicationYear; }
  public void setFirstPublicationYear(Short value) { this.firstPublicationYear = value; }
  public String getDescription() { return description; }
  public void setDescription(String value) { this.description = value; }
  public BookStatus getStatus() { return status; }
  public void setStatus(BookStatus value) { this.status = value; }
  public UUID getMergedIntoId() { return mergedIntoId; }
  public void setMergedIntoId(UUID value) { this.mergedIntoId = value; }
  public Book getMergedInto() { return mergedInto; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  @PreUpdate
  void touch() { updatedAt = Instant.now(); }
}
