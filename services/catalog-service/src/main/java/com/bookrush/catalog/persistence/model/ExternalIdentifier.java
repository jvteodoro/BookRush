package com.bookrush.catalog.persistence.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_identifier", schema = "catalog")
public class ExternalIdentifier {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "source_id", nullable = false)
  private UUID sourceId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id", insertable = false, updatable = false)
  private Source source;

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

  @Column(name = "author_id")
  private UUID authorId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_id", insertable = false, updatable = false)
  private Author author;

  @Enumerated(EnumType.STRING)
  @Column(name = "identifier_type", nullable = false, length = 32)
  private ExternalIdentifierIdentifierType identifierType;

  @Column(name = "identifier_value", nullable = false, columnDefinition = "text")
  private String identifierValue;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public ExternalIdentifier() {}

  public UUID getId() { return id; }
  public void setId(UUID value) { this.id = value; }
  public UUID getSourceId() { return sourceId; }
  public void setSourceId(UUID value) { this.sourceId = value; }
  public Source getSource() { return source; }
  public UUID getBookId() { return bookId; }
  public void setBookId(UUID value) { this.bookId = value; }
  public Book getBook() { return book; }
  public UUID getEditionId() { return editionId; }
  public void setEditionId(UUID value) { this.editionId = value; }
  public Edition getEdition() { return edition; }
  public UUID getAuthorId() { return authorId; }
  public void setAuthorId(UUID value) { this.authorId = value; }
  public Author getAuthor() { return author; }
  public ExternalIdentifierIdentifierType getIdentifierType() { return identifierType; }
  public void setIdentifierType(ExternalIdentifierIdentifierType value) { this.identifierType = value; }
  public String getIdentifierValue() { return identifierValue; }
  public void setIdentifierValue(String value) { this.identifierValue = value; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  @PreUpdate
  void touch() { updatedAt = Instant.now(); }
}
