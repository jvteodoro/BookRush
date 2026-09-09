package com.bookrush.catalog.persistence.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "edition", schema = "catalog")
public class Edition {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "book_id", nullable = false)
  private UUID bookId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "book_id", insertable = false, updatable = false)
  private Book book;

  @Column(name = "title", columnDefinition = "text")
  private String title;

  @Column(name = "subtitle", columnDefinition = "text")
  private String subtitle;

  @Column(name = "language", length = 3)
  private String language;

  @Column(name = "publisher", columnDefinition = "text")
  private String publisher;

  @Column(name = "publication_year")
  private Short publicationYear;

  @Column(name = "edition_name", columnDefinition = "text")
  private String editionName;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Column(name = "license_id")
  private UUID licenseId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "license_id", insertable = false, updatable = false)
  private License license;

  public Edition() {}

  public UUID getId() { return id; }
  public void setId(UUID value) { this.id = value; }
  public UUID getBookId() { return bookId; }
  public void setBookId(UUID value) { this.bookId = value; }
  public Book getBook() { return book; }
  public String getTitle() { return title; }
  public void setTitle(String value) { this.title = value; }
  public String getSubtitle() { return subtitle; }
  public void setSubtitle(String value) { this.subtitle = value; }
  public String getLanguage() { return language; }
  public void setLanguage(String value) { this.language = value; }
  public String getPublisher() { return publisher; }
  public void setPublisher(String value) { this.publisher = value; }
  public Short getPublicationYear() { return publicationYear; }
  public void setPublicationYear(Short value) { this.publicationYear = value; }
  public String getEditionName() { return editionName; }
  public void setEditionName(String value) { this.editionName = value; }
  public String getDescription() { return description; }
  public void setDescription(String value) { this.description = value; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public UUID getLicenseId() { return licenseId; }
  public void setLicenseId(UUID value) { this.licenseId = value; }
  public License getLicense() { return license; }

  @PreUpdate
  void touch() { updatedAt = Instant.now(); }
}
