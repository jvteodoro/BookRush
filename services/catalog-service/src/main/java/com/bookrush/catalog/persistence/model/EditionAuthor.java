package com.bookrush.catalog.persistence.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "edition_author", schema = "catalog")
@IdClass(EditionAuthor.Key.class)
public class EditionAuthor {
  @Id
  @Column(name = "edition_id", nullable = false)
  private UUID editionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "edition_id", insertable = false, updatable = false)
  private Edition edition;

  @Id
  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_id", insertable = false, updatable = false)
  private Author author;

  @Enumerated(EnumType.STRING)
  @Id
  @Column(name = "role", nullable = false, length = 24)
  private EditionAuthorRole role;

  @Column(name = "position", nullable = false)
  private Integer position;

  public EditionAuthor() {}

  public UUID getEditionId() { return editionId; }
  public void setEditionId(UUID value) { this.editionId = value; }
  public Edition getEdition() { return edition; }
  public UUID getAuthorId() { return authorId; }
  public void setAuthorId(UUID value) { this.authorId = value; }
  public Author getAuthor() { return author; }
  public EditionAuthorRole getRole() { return role; }
  public void setRole(EditionAuthorRole value) { this.role = value; }
  public Integer getPosition() { return position; }
  public void setPosition(Integer value) { this.position = value; }

  public static class Key implements java.io.Serializable {
    public UUID editionId;
    public UUID authorId;
    public EditionAuthorRole role;
    public Key() {}
    @Override public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof Key k)) return false;
      return java.util.Objects.equals(editionId, k.editionId) && java.util.Objects.equals(authorId, k.authorId) && java.util.Objects.equals(role, k.role);
    }
    @Override public int hashCode() { return java.util.Objects.hash(editionId, authorId, role); }
  }
}
