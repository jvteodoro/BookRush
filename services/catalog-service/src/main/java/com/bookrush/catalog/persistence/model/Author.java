package com.bookrush.catalog.persistence.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "author", schema = "catalog")
public class Author {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "name", nullable = false, columnDefinition = "text")
  private String name;

  @Column(name = "normalized_name", columnDefinition = "text")
  private String normalizedName;

  @Column(name = "birth_year")
  private Short birthYear;

  @Column(name = "death_year")
  private Short deathYear;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public Author() {}

  public UUID getId() { return id; }
  public void setId(UUID value) { this.id = value; }
  public String getName() { return name; }
  public void setName(String value) { this.name = value; }
  public String getNormalizedName() { return normalizedName; }
  public void setNormalizedName(String value) { this.normalizedName = value; }
  public Short getBirthYear() { return birthYear; }
  public void setBirthYear(Short value) { this.birthYear = value; }
  public Short getDeathYear() { return deathYear; }
  public void setDeathYear(Short value) { this.deathYear = value; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }

  @PreUpdate
  void touch() { updatedAt = Instant.now(); }
}
