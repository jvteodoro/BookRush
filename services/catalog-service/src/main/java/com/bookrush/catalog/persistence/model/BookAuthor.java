package com.bookrush.catalog.persistence.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "book_author", schema = "catalog")
@IdClass(BookAuthor.Key.class)
public class BookAuthor {
  @Id
  @Column(name = "book_id", nullable = false)
  private UUID bookId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "book_id", insertable = false, updatable = false)
  private Book book;

  @Id
  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_id", insertable = false, updatable = false)
  private Author author;

  @Enumerated(EnumType.STRING)
  @Id
  @Column(name = "role", nullable = false, length = 24)
  private BookAuthorRole role;

  @Column(name = "position", nullable = false)
  private Integer position;

  public BookAuthor() {}

  public UUID getBookId() { return bookId; }
  public void setBookId(UUID value) { this.bookId = value; }
  public Book getBook() { return book; }
  public UUID getAuthorId() { return authorId; }
  public void setAuthorId(UUID value) { this.authorId = value; }
  public Author getAuthor() { return author; }
  public BookAuthorRole getRole() { return role; }
  public void setRole(BookAuthorRole value) { this.role = value; }
  public Integer getPosition() { return position; }
  public void setPosition(Integer value) { this.position = value; }

  public static class Key implements java.io.Serializable {
    public UUID bookId;
    public UUID authorId;
    public BookAuthorRole role;
    public Key() {}
    @Override public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof Key k)) return false;
      return java.util.Objects.equals(bookId, k.bookId) && java.util.Objects.equals(authorId, k.authorId) && java.util.Objects.equals(role, k.role);
    }
    @Override public int hashCode() { return java.util.Objects.hash(bookId, authorId, role); }
  }
}
