package com.bookrush.publisherservice.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bookrush.platform.core.TechnicalIdentity;
import com.bookrush.publisherservice.BehaviorEventPublisher;
import com.bookrush.publisherservice.PublisherIngestionClient;
import com.bookrush.publisherservice.PublisherMetricsClient;
import com.bookrush.publisherservice.storage.StorageConfiguration.StorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class PublisherOwnershipPostgresTest {
  @Test
  void preservesLegacyAndIsolatesAuthorOperationsAtTransactionalBoundaries() throws Exception {
    var source = source("TEST_DATABASE_URL");
    var db = new JdbcTemplate(source);
    var manager = new DataSourceTransactionManager(source);
    var tx = new TransactionTemplate(manager);
    Flyway.configure().dataSource(source).schemas("publisher").target("7").load().migrate();
    UUID proven = UUID.randomUUID(), quarantined = UUID.randomUUID(), differentRealm = UUID.randomUUID();
    var owner = PublisherTestIdentity.author("same-subject");
    var outsider = PublisherTestIdentity.author("outsider");
    var editor = PublisherTestIdentity.operator("same-subject");
    for (UUID id : new UUID[] {proven, quarantined, differentRealm}) {
      db.update("INSERT INTO publisher.submission(id,subject_key,title) VALUES (?,?,'legacy')", id, owner.getName());
    }
    db.update("INSERT INTO publisher.membership(subject_key,status) VALUES (?,'ACTIVE')", owner.getName());
    var original = db.queryForMap("SELECT subject_key,created_at,updated_at FROM publisher.submission WHERE id=?", proven);
    var migrate = Flyway.configure().dataSource(source).schemas("publisher").load();
    assertEquals(1, migrate.migrate().migrationsExecuted);
    assertEquals(0, migrate.migrate().migrationsExecuted);
    migrate.validate();

    evidence(db, "submission", proven.toString(), owner.getName(), PublisherTestIdentity.PRODUCT);
    evidence(db, "submission", differentRealm.toString(), owner.getName(), PublisherTestIdentity.EDITORIAL);
    evidence(db, "membership", owner.getName(), owner.getName(), PublisherTestIdentity.PRODUCT);
    assertEquals(3, db.queryForObject("SELECT publisher.reconcile_legacy_identity()", Integer.class));
    assertEquals(0, db.queryForObject("SELECT publisher.reconcile_legacy_identity()", Integer.class));
    assertEquals(original, db.queryForMap("SELECT subject_key,created_at,updated_at FROM publisher.submission WHERE id=?", proven));

    var behavior = mock(BehaviorEventPublisher.class);
    var ingestion = mock(PublisherIngestionClient.class);
    var metrics = mock(PublisherMetricsClient.class);
    var identities = PublisherTestIdentity.identities();
    var ownership = new PublisherOwnership(db);
    var controller = new PublisherController(db, ingestion, behavior, identities, ownership, manager, new ObjectMapper());
    var reports = new PublisherMetricsController(db, metrics, identities);
    var s3 = mock(S3Client.class);
    var signer = mock(S3Presigner.class);
    var uploads = new PublisherUploadController(db, signer, s3, new StorageProperties(), identities, ownership);
    var callback = new PublisherIngestionLinkController(db, "fixture-only");
    var metadata = new PublisherController.Metadata("synopsis", "pt", null, null, null);
    var rights = new PublisherController.Rights("DISPLAY", null, "reference", true);

    assertEquals(proven, ((Map<?, ?>) controller.get(proven, owner)).get("id"));
    denied(HttpStatus.NOT_FOUND, () -> controller.get(quarantined, owner));
    denied(HttpStatus.NOT_FOUND, () -> controller.get(differentRealm, owner));
    denied(HttpStatus.NOT_FOUND, () -> ownership.resource(proven,
        new TechnicalIdentity(PublisherTestIdentity.EDITORIAL, owner.getName())));
    denied(HttpStatus.FORBIDDEN, () -> controller.get(proven, editor));
    denied(HttpStatus.FORBIDDEN, () -> controller.create(new PublisherController.Submission("attempt", null, null, null),
        editor, null));
    denied(HttpStatus.FORBIDDEN, () -> controller.review(owner));
    assertEquals("ACTIVE", ((Map<?, ?>) controller.membership(owner)).get("status"));
    assertEquals("PENDING", ((Map<?, ?>) controller.membership(outsider)).get("status"));
    assertEquals(1, count(db, "membership"));
    UUID id = (UUID) ((Map<?, ?>) tx.execute(status -> controller.create(
        new PublisherController.Submission("authorized", null, null, null), owner, null))).get("id");
    verify(behavior).publish(eq("PUBLISHER_SUBMISSION_CREATED"), isNull(), anyMap(), isNull());
    clearInvocations(behavior);
    assertEquals(id, ((Map<?, ?>) controller.get(id, owner)).get("id"));
    assertEquals(2, ((java.util.List<?>) controller.list(owner)).size());

    denied(HttpStatus.NOT_FOUND, () -> controller.get(id, outsider));
    denied(HttpStatus.NOT_FOUND, () -> tx.execute(status -> controller.metadata(id, metadata, outsider)));
    denied(HttpStatus.NOT_FOUND, () -> tx.execute(status -> controller.rights(id, rights, outsider)));
    denied(HttpStatus.NOT_FOUND, () -> controller.submit(id, outsider, null));
    denied(HttpStatus.NOT_FOUND, () -> tx.execute(status -> controller.withdraw(id, outsider, null)));
    denied(HttpStatus.NOT_FOUND, () -> tx.execute(status -> uploads.request(id, Map.of(), outsider)));
    denied(HttpStatus.NOT_FOUND, () -> tx.execute(status -> uploads.finalizeUpload(id, UUID.randomUUID(), Map.of(), outsider)));
    denied(HttpStatus.NOT_FOUND, () -> controller.reviewHistory(id, outsider));
    denied(HttpStatus.NOT_FOUND, () -> reports.metrics(id, outsider, 30));
    verifyNoInteractions(behavior, ingestion, metrics, s3, signer);
    for (String table : new String[] {"submission_metadata", "rights_attestation", "staged_upload", "published_work",
        "publication_review"}) assertEquals(0, count(db, table));
    assertEquals("DRAFT", status(db, id));

    tx.execute(status -> controller.metadata(id, metadata, owner));
    var legacyRight = UUID.randomUUID();
    db.update("""
        INSERT INTO publisher.rights_attestation(id,submission_id,right_type,affirmed_by_subject,evidence_reference)
        VALUES (?,?,'DISPLAY','legacy-affirmer','original-reference')
        """, legacyRight, id);
    tx.execute(status -> controller.rights(id, rights, owner));
    tx.execute(status -> controller.rights(id, rights, owner));
    assertEquals(1, count(db, "rights_attestation_history"));
    assertEquals("legacy-affirmer", db.queryForObject("SELECT affirmed_by_subject FROM publisher.rights_attestation_history", String.class));
    assertEquals("original-reference", db.queryForObject("SELECT evidence_reference FROM publisher.rights_attestation_history", String.class));
    assertEquals(PublisherTestIdentity.PRODUCT, db.queryForObject("SELECT holder_issuer FROM publisher.rights_attestation", String.class));
    var presigned = mock(PresignedPutObjectRequest.class);
    when(presigned.url()).thenReturn(URI.create("https://storage.fixture/object").toURL());
    when(presigned.expiration()).thenReturn(Instant.now().plusSeconds(600));
    when(signer.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);
    UUID upload = (UUID) ((Map<?, ?>) tx.execute(status -> uploads.request(id, Map.of("filename", "book.epub"), owner))).get("uploadId");
    var foreignDraft = UUID.randomUUID();
    db.update("INSERT INTO publisher.submission(id,subject_key,identity_issuer,identity_subject,title) VALUES (?,?,?,?,'second')",
        foreignDraft, owner.getName(), PublisherTestIdentity.PRODUCT, owner.getName());
    denied(HttpStatus.NOT_FOUND, () -> tx.execute(status -> uploads.finalizeUpload(foreignDraft, upload, Map.of(), owner)));
    denied(HttpStatus.NOT_FOUND, () -> tx.execute(status -> uploads.finalizeUpload(id, upload, Map.of(), outsider)));
    verifyNoInteractions(s3);
    when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(123L).build());
    var first = tx.execute(status -> uploads.finalizeUpload(id, upload, Map.of("sha256", "hash"), owner));
    assertEquals(first, tx.execute(status -> uploads.finalizeUpload(id, upload, Map.of("sha256", "hash"), owner)));
    verify(s3, times(1)).headObject(any(HeadObjectRequest.class));

    UUID book = UUID.randomUUID();
    UUID job = UUID.randomUUID();
    try (var executor = Executors.newSingleThreadExecutor()) {
      doAnswer(call -> {
        var done = executor.submit(() -> tx.execute(status -> callback.link(id, "fixture-only",
            new PublisherIngestionLinkController.LinkRequest(book, job, "ADMIN_UPLOAD", id.toString()))));
        done.get(5, TimeUnit.SECONDS);
        return null;
      }).when(ingestion).process(eq(id), anyString(), anyString(), anyString(), anyString(), eq("hash"));
      assertEquals("PROCESSING", ((Map<?, ?>) controller.submit(id, owner, null)).get("status"));
      controller.submit(id, owner, null);
      verify(ingestion, times(1)).process(eq(id), anyString(), anyString(), anyString(), anyString(), eq("hash"));
      denied(HttpStatus.CONFLICT, () -> tx.execute(status -> callback.link(id, "fixture-only",
          new PublisherIngestionLinkController.LinkRequest(UUID.randomUUID(), null, null, null))));
      assertEquals("bookrush-ingestion-legacy", db.queryForObject("SELECT linked_by_workload FROM publisher.submission WHERE id=?", String.class, id));

      var started = new CountDownLatch(1);
      try (var lock = source.getConnection()) {
        lock.setAutoCommit(false);
        try (var statement = lock.prepareStatement("UPDATE publisher.submission SET status='SUBMITTED' WHERE id=?")) {
          statement.setObject(1, foreignDraft);
          statement.executeUpdate();
        }
        var pending = executor.submit(() -> {
          started.countDown();
          denied(HttpStatus.CONFLICT, () -> tx.execute(status -> controller.metadata(foreignDraft, metadata, owner)));
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertThrows(TimeoutException.class, () -> pending.get(150, TimeUnit.MILLISECONDS));
        lock.commit();
        pending.get(5, TimeUnit.SECONDS);
        assertEquals(0, db.queryForObject("SELECT count(*) FROM publisher.submission_metadata WHERE submission_id=?",
            Integer.class, foreignDraft));
      }
    }

    tx.execute(status -> controller.review(id, new PublisherController.Review("APPROVED", null, null), editor));
    assertEquals(PublisherTestIdentity.EDITORIAL, db.queryForObject("SELECT identity_issuer FROM publisher.publication_review", String.class));
    assertEquals(PublisherTestIdentity.PRODUCT, db.queryForObject("SELECT identity_issuer FROM publisher.published_work", String.class));
    assertEquals(1, ((java.util.List<?>) controller.reviewHistory(id, owner)).size());
    when(metrics.metrics(book, 30)).thenReturn(java.util.List.of());
    assertEquals("AVAILABLE", ((Map<?, ?>) reports.metrics(id, owner, 30)).get("status"));
    denied(HttpStatus.NOT_FOUND, () -> reports.metrics(id, outsider, 30));
    verify(metrics, times(1)).metrics(book, 30);
    denied(HttpStatus.NOT_FOUND, () -> tx.execute(status -> controller.withdraw(id, outsider, null)));
    assertEquals("PUBLISHED", status(db, id));
    tx.execute(status -> controller.withdraw(id, owner, null));
    tx.execute(status -> controller.withdraw(id, owner, null));
    assertEquals("WITHDRAWN", status(db, id));
    verify(behavior, times(1)).publish(eq("PUBLISHER_WORK_WITHDRAWN"), isNull(), anyMap(), isNull());
    assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> db.update(
        "UPDATE publisher.submission SET identity_issuer=? WHERE id=?", PublisherTestIdentity.EDITORIAL, id));
    UUID legacyUpload = UUID.randomUUID(), legacyReview = UUID.randomUUID(), legacyWork = UUID.randomUUID();
    db.update("INSERT INTO publisher.staged_upload(id,submission_id,subject_key,object_key,content_type) VALUES (?,?,?,?,?)",
        legacyUpload, proven, owner.getName(), "legacy/staging", "application/epub+zip");
    db.update("INSERT INTO publisher.publication_review(id,submission_id,status,reviewer_subject_key) VALUES (?,?,'APPROVED',?)",
        legacyReview, proven, editor.getName());
    db.update("INSERT INTO publisher.published_work(id,submission_id,book_id,publisher_subject_key) VALUES (?,?,?,?)",
        legacyWork, proven, UUID.randomUUID(), owner.getName());
    db.update("INSERT INTO publisher.rights_attestation(id,submission_id,right_type,affirmed_by_subject) VALUES (?,?,'PROCESSING',?)",
        UUID.randomUUID(), proven, owner.getName());
    evidence(db, "staged_upload", legacyUpload.toString(), owner.getName(), PublisherTestIdentity.PRODUCT);
    evidence(db, "publication_review", legacyReview.toString(), editor.getName(), PublisherTestIdentity.EDITORIAL);
    evidence(db, "published_work", legacyWork.toString(), owner.getName(), PublisherTestIdentity.PRODUCT);
    assertEquals(3, db.queryForObject("SELECT publisher.reconcile_legacy_identity()", Integer.class));
    assertEquals(0, db.queryForObject("SELECT publisher.reconcile_legacy_identity()", Integer.class));
    assertEquals(PublisherTestIdentity.EDITORIAL, db.queryForObject(
        "SELECT identity_issuer FROM publisher.publication_review WHERE id=?", String.class, legacyReview));
    assertEquals("legacy/staging", db.queryForObject(
        "SELECT object_key FROM publisher.staged_upload WHERE id=?", String.class, legacyUpload));
    assertNull(db.queryForObject("SELECT identity_issuer FROM publisher.rights_attestation WHERE submission_id=?",
        String.class, proven));
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_CLEAN_URL", matches = ".+")
  void installsOnEmptyPostgres() {
    var source = source("TEST_DATABASE_CLEAN_URL");
    var flyway = Flyway.configure().dataSource(source).schemas("publisher").load();
    assertEquals(8, flyway.migrate().migrationsExecuted);
    flyway.validate();
    assertEquals(0, flyway.migrate().migrationsExecuted);
  }

  private static DriverManagerDataSource source(String name) {
    return new DriverManagerDataSource(System.getenv(name), "bookrush_test", "test-only-not-for-production");
  }

  private static void evidence(JdbcTemplate db, String entity, String key, String subject, String realm) {
    db.update("""
        INSERT INTO publisher.identity_evidence(entity,record_key,legacy_subject,verified_issuer,verified_subject,evidence_reference)
        VALUES (?,?,?,?,?,'signed-audit-fixture')
        """, entity, key, subject, realm, subject);
  }

  private static int count(JdbcTemplate db, String table) {
    return db.queryForObject("SELECT count(*) FROM publisher." + table, Integer.class);
  }

  private static String status(JdbcTemplate db, UUID id) {
    return db.queryForObject("SELECT status FROM publisher.submission WHERE id=?", String.class, id);
  }

  private static void denied(HttpStatus status, org.junit.jupiter.api.function.Executable operation) {
    assertEquals(status, assertThrows(ResponseStatusException.class, operation).getStatusCode());
  }
}
