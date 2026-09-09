package com.bookrush.catalog.persistence;

import com.bookrush.catalog.persistence.model.*;
import com.bookrush.catalog.persistence.repository.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Sql("/catalog-fixture.sql")
class JpaCatalogIT {
  @Autowired EntityManager em;
  @Autowired BookRepository books;
  @Autowired BookAssetRepository assets;
  @Autowired BookAssetVersionRepository versions;
  private UUID id(String prefix, int n) {
    return UUID.fromString(prefix+"0000000-0000-4000-8000-00000000000"+n);
  }

  @Test void repositoriesResolveIdentityAndPageVersionsWithoutFailedLatest() {
    Book work = books.findByExternalIdentifier("GUTENBERG","GUTENBERG_ID","1342").orElseThrow();
    assertEquals("Pride and Prejudice", work.getCanonicalTitle());
    assertEquals(4, assets.findByBookIdOrderByIdAsc(work.getId(), PageRequest.of(0,2)).getTotalElements());
    em.clear();
    BookAssetVersion latest = versions.findLatestAvailable(id("4",2)).orElseThrow();
    assertEquals(1, latest.getVersionNumber());
    assertEquals(StorageProvider.S3, latest.getStorageProvider());
    assertFalse(Hibernate.isInitialized(latest.getBookAsset()));
    assertEquals(id("3",1), latest.getBookAsset().getBookId());
    assertEquals(2, versions.findByBookAssetIdOrderByVersionNumberDesc(id("4",2), PageRequest.of(0,10)).getTotalElements());
    assertEquals(4, versions.findBySha256OrderByIdAsc("d".repeat(64), PageRequest.of(0,10)).getTotalElements());
    em.find(BookAsset.class, id("4",2)).setStatus(BookAssetStatus.INACTIVE);
    em.flush();
    assertTrue(versions.findLatestAvailable(id("4",2)).isEmpty());
  }

  @Test void roundTripsUuidEnumsTimestampsJsonAndCompositeCredit() {
    Book work = new Book();
    work.setCanonicalTitle("JPA work");
    books.saveAndFlush(work);
    UUID workId = work.getId();
    Author author = new Author();
    author.setName("Unknown dates");
    em.persist(author);
    BookAuthor credit = new BookAuthor();
    credit.setBookId(workId);
    credit.setAuthorId(author.getId());
    credit.setRole(BookAuthorRole.AUTHOR);
    credit.setPosition(1);
    em.persist(credit);
    SourceRecord record = new SourceRecord();
    record.setSourceId(id("1",1));
    record.setExternalId("jpa-test");
    record.setRawMetadata(Map.of("title","JPA work","number",1));
    record.setRetrievedAt(Instant.now());
    record.setContentHash("f".repeat(64));
    em.persist(record);
    em.flush();
    em.clear();
    assertEquals(BookStatus.REVIEW_REQUIRED, books.findById(workId).orElseThrow().getStatus());
    assertNotNull(books.findById(workId).orElseThrow().getCreatedAt());
    assertEquals("JPA work", em.find(SourceRecord.class, record.getId()).getRawMetadata().get("title"));
    BookAuthor.Key key = new BookAuthor.Key();
    key.bookId=workId; key.authorId=author.getId(); key.role=BookAuthorRole.AUTHOR;
    assertEquals(1, em.find(BookAuthor.class, key).getPosition());
  }

  @Test void mapsAllFourteenTablesIncludingOperationalAssociations() {
    assertEquals(14, em.getMetamodel().getEntities().size());
    assertEquals("en", em.find(Edition.class,id("3",3)).getLanguage());
    assertEquals("Jane Austen", em.find(Author.class,id("3",2)).getName());
    assertEquals("PUBLIC_DOMAIN", em.find(License.class,id("2",1)).getCode());
    assertEquals("GUTENBERG", em.find(Source.class,id("1",1)).getCode());
    assertEquals(ExternalIdentifierIdentifierType.GUTENBERG_ID, em.find(ExternalIdentifier.class,id("3",4)).getIdentifierType());
    assertEquals(IngestionJobStatus.COMPLETED_WITH_ERRORS, em.find(IngestionJob.class,id("3",6)).getStatus());
    assertEquals("1342", em.find(IngestionItem.class,id("3",7)).getSourceRecord().getExternalId());
    assertEquals(AssetProcessingStatus.SUCCEEDED, em.find(AssetProcessing.class,id("6",1)).getStatus());
  }
}
