package com.bookrush.publisherservice.api;

import com.bookrush.platform.core.TechnicalIdentity;
import com.bookrush.publisherservice.BehaviorEventPublisher;
import com.bookrush.publisherservice.PublisherIngestionClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static com.bookrush.publisherservice.api.PublisherOwnership.OWN;

@RestController
@RequestMapping("/api/v1/publisher")
public class PublisherController {
  private static final String SUBMISSIONS = """
      SELECT id,title,status,catalog_book_id,source_code,source_external_id,ingestion_job_id,linked_at,created_at,updated_at
      FROM publisher.submission WHERE
      """;
  private final JdbcTemplate db;
  private final PublisherIngestionClient ingestion;
  private final BehaviorEventPublisher behavior;
  private final PublisherIdentity identities;
  private final PublisherOwnership ownership;
  private final TransactionTemplate transaction;
  private final ObjectMapper json;

  PublisherController(JdbcTemplate db, PublisherIngestionClient ingestion, BehaviorEventPublisher behavior,
      PublisherIdentity identities, PublisherOwnership ownership, PlatformTransactionManager transactions, ObjectMapper json) {
    this.db = db;
    this.ingestion = ingestion;
    this.behavior = behavior;
    this.identities = identities;
    this.ownership = ownership;
    transaction = new TransactionTemplate(transactions);
    this.json = json;
  }

  public record Submission(@NotBlank @Size(max = 300) String title, UUID catalogBookId, String sourceCode,
                           String sourceExternalId) {}
  public record Metadata(String synopsis, String language, String isbn, String publisherName, List<String> keywords) {}
  public record Rights(@NotBlank String rightType, String territory, String evidenceReference, boolean claimsOwnRights) {}
  public record Review(@NotBlank String status, String reasonCode, String notes) {}
  private record Upload(String objectKey, String contentType, String sha256) {}
  private record Dispatch(String status, String previousStatus, String title, Upload upload, boolean changed) {}

  @PostMapping("/submissions")
  @Transactional
  public Object create(@Valid @RequestBody Submission input, Principal principal, HttpServletRequest request) {
    var actor = identities.author(principal);
    requireNotBlocked(actor);
    UUID id = UUID.randomUUID();
    db.update("""
        INSERT INTO publisher.submission
          (id,subject_key,identity_issuer,identity_subject,title,catalog_book_id,source_code,source_external_id)
        VALUES (?,?,?,?,?,?,?,?)
        """, id, actor.subject(), actor.issuer(), actor.subject(), input.title(), input.catalogBookId(),
        input.sourceCode(), input.sourceExternalId());
    eventAfterCommit("PUBLISHER_SUBMISSION_CREATED", input.catalogBookId(), Map.of("submissionId", id), request);
    return Map.of("id", id, "title", input.title(), "status", "DRAFT");
  }

  @GetMapping("/submissions")
  public Object list(Principal principal) {
    var actor = identities.author(principal);
    return db.queryForList(SUBMISSIONS + OWN + " ORDER BY created_at DESC,id", actor.issuer(), actor.subject());
  }

  @GetMapping("/submissions/{id}")
  public Object get(@PathVariable UUID id, Principal principal) {
    var actor = identities.author(principal);
    var results = db.queryForList(SUBMISSIONS + "id=? AND " + OWN, id, actor.issuer(), actor.subject());
    if (results.isEmpty()) throw PublisherOwnership.missing();
    return results.getFirst();
  }

  @GetMapping("/submissions/{id}/reviews")
  @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public Object reviewHistory(@PathVariable UUID id, Principal principal) {
    var actor = identities.author(principal);
    ownership.resource(id, actor);
    return db.queryForList("""
        SELECT r.id,r.status,r.reason_code,r.notes,r.created_at
        FROM publisher.publication_review r JOIN publisher.submission s ON s.id=r.submission_id
        WHERE s.id=? AND s.identity_issuer=? AND s.identity_subject=?
        ORDER BY r.created_at DESC,r.id
        """, id, actor.issuer(), actor.subject());
  }

  @PutMapping("/submissions/{id}/metadata")
  @Transactional
  public Object metadata(@PathVariable UUID id, @RequestBody Metadata input, Principal principal) {
    var actor = identities.author(principal);
    ownership.lock(id, actor).requireEditable();
    requireNotBlocked(actor);
    String keywords = json.valueToTree(input.keywords() == null ? List.of() : input.keywords()).toString();
    db.update("""
        INSERT INTO publisher.submission_metadata(submission_id,synopsis,language,isbn,publisher_name,keywords)
        VALUES (?,?,?,?,?,?::jsonb)
        ON CONFLICT (submission_id) DO UPDATE SET synopsis=excluded.synopsis,language=excluded.language,
          isbn=excluded.isbn,publisher_name=excluded.publisher_name,keywords=excluded.keywords,updated_at=now()
        """, id, input.synopsis(), input.language(), input.isbn(), input.publisherName(), keywords);
    return Map.of("submissionId", id, "updated", true);
  }

  @PostMapping("/submissions/{id}/rights")
  @Transactional
  public Object rights(@PathVariable UUID id, @Valid @RequestBody Rights input, Principal principal) {
    var actor = identities.author(principal);
    ownership.lock(id, actor).requireEditable();
    requireNotBlocked(actor);
    if (input.rightType() == null || !Set.of("DISTRIBUTION", "PROCESSING", "DISPLAY").contains(input.rightType())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported rights type");
    }
    int changed = db.update("""
        INSERT INTO publisher.rights_attestation
          (id,submission_id,right_type,territory,evidence_reference,affirmed_by_subject,identity_issuer,identity_subject,claims_own_rights)
        VALUES (?,?,?,?,?,?,?,?,?)
        ON CONFLICT (submission_id,right_type,territory) DO UPDATE
          SET evidence_reference=excluded.evidence_reference,affirmed_by_subject=excluded.affirmed_by_subject,
              identity_issuer=excluded.identity_issuer,identity_subject=excluded.identity_subject,
              claims_own_rights=excluded.claims_own_rights,
              affirmed_at=CASE WHEN publisher.rights_attestation.identity_issuer=excluded.identity_issuer
                AND publisher.rights_attestation.identity_subject=excluded.identity_subject
                AND publisher.rights_attestation.claims_own_rights=excluded.claims_own_rights
                AND publisher.rights_attestation.evidence_reference IS NOT DISTINCT FROM excluded.evidence_reference
                THEN publisher.rights_attestation.affirmed_at ELSE clock_timestamp() END
          WHERE publisher.rights_attestation.identity_issuer IS NULL
            OR (publisher.rights_attestation.identity_issuer=excluded.identity_issuer
              AND publisher.rights_attestation.identity_subject=excluded.identity_subject)
        """, UUID.randomUUID(), id, input.rightType(), input.territory() == null ? "GLOBAL" : input.territory(),
        input.evidenceReference(), actor.subject(), actor.issuer(), actor.subject(), input.claimsOwnRights());
    if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "existing declaration has another affirmer");
    return Map.of("submissionId", id, "rightType", input.rightType(), "affirmed", true);
  }

  @PostMapping("/submissions/{id}/submit")
  public Object submit(@PathVariable UUID id, Principal principal, HttpServletRequest request) {
    var actor = identities.author(principal);
    var dispatch = transaction.execute(status -> prepareSubmit(id, actor));
    String state = dispatch.status();
    if (dispatch.upload() != null) {
      var upload = dispatch.upload();
      // Ingestion synchronously calls catalog-link, so no publisher database lock
      // can be held during this HTTP call.
      try {
        ingestion.process(id, dispatch.title(), upload.objectKey(), "books-staging", upload.contentType(), upload.sha256());
      } catch (RuntimeException failure) {
        db.update("UPDATE publisher.submission SET status=?,updated_at=now() "
            + "WHERE id=? AND " + OWN + " AND status='SUBMITTED'",
            dispatch.previousStatus(), id, actor.issuer(), actor.subject());
        throw failure;
      }
      db.update("UPDATE publisher.submission SET status='PROCESSING',updated_at=now() "
          + "WHERE id=? AND " + OWN + " AND status='SUBMITTED'", id, actor.issuer(), actor.subject());
      state = db.queryForObject("SELECT status FROM publisher.submission WHERE id=? AND " + OWN, String.class,
          id, actor.issuer(), actor.subject());
    }
    if (dispatch.changed()) {
      behavior.publish("PUBLISHER_SUBMISSION_SUBMITTED", null, Map.of("submissionId", id, "status", state),
          authorization(request));
    }
    return Map.of("id", id, "status", state);
  }

  private Dispatch prepareSubmit(UUID id, TechnicalIdentity actor) {
    var submission = ownership.lock(id, actor);
    if ("PROCESSING".equals(submission.status()) || "SUBMITTED".equals(submission.status())) {
      return new Dispatch(submission.status(), null, submission.title(), null, false);
    }
    submission.requireEditable();
    Upload upload = db.query("""
        SELECT object_key,content_type,sha256 FROM publisher.staged_upload
        WHERE submission_id=? AND identity_issuer=? AND identity_subject=? AND status='FINALIZED'
        ORDER BY finalized_at DESC,id LIMIT 1
        """, rs -> rs.next() ? new Upload(rs.getString(1), rs.getString(2), rs.getString(3)) : null,
        id, actor.issuer(), actor.subject());
    db.update("UPDATE publisher.submission SET status='SUBMITTED',updated_at=now() WHERE id=? AND " + OWN,
        id, actor.issuer(), actor.subject());
    return new Dispatch("SUBMITTED", submission.status(), submission.title(), upload, true);
  }

  @PostMapping("/submissions/{id}/withdraw")
  @Transactional
  public Object withdraw(@PathVariable UUID id, Principal principal, HttpServletRequest request) {
    var actor = identities.author(principal);
    var submission = ownership.lock(id, actor);
    if ("WITHDRAWN".equals(submission.status())) return Map.of("submissionId", id, "status", "WITHDRAWN");
    int updated = db.update("UPDATE publisher.published_work SET status='WITHDRAWN',withdrawn_at=now() "
        + "WHERE submission_id=? AND " + OWN + " AND status='PUBLISHED'", id, actor.issuer(), actor.subject());
    if (updated == 0) throw PublisherOwnership.missing();
    db.update("UPDATE publisher.submission SET status='WITHDRAWN',updated_at=now() WHERE id=? AND " + OWN,
        id, actor.issuer(), actor.subject());
    eventAfterCommit("PUBLISHER_WORK_WITHDRAWN", null, Map.of("submissionId", id), request);
    return Map.of("submissionId", id, "status", "WITHDRAWN");
  }

  @GetMapping("/review")
  public Object review(Principal principal) {
    identities.operator(principal);
    return db.queryForList("""
        SELECT r.id,r.submission_id,s.title,r.status,r.reviewer_subject_key,r.reason_code,r.notes,r.created_at
        FROM publisher.publication_review r JOIN publisher.submission s ON s.id=r.submission_id
        WHERE r.status='PENDING' ORDER BY r.created_at
        """);
  }

  @PostMapping("/submissions/{id}/review")
  @Transactional
  public Object review(@PathVariable UUID id, @Valid @RequestBody Review input, Principal principal) {
    var reviewer = identities.operator(principal);
    String status = input.status();
    if (status == null || !Set.of("APPROVED", "REJECTED", "CHANGES_REQUESTED").contains(status)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported review status");
    }
    var submission = ownership.lockForReview(id);
    if (!Set.of("SUBMITTED", "PROCESSING").contains(submission.status())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "submission cannot be reviewed in this state");
    }
    if ("APPROVED".equals(status)) publish(submission);
    UUID reviewId = UUID.randomUUID();
    db.update("""
        INSERT INTO publisher.publication_review
          (id,submission_id,status,reviewer_subject_key,identity_issuer,identity_subject,reason_code,notes)
        VALUES (?,?,?,?,?,?,?,?)
        """, reviewId, id, status, reviewer.subject(), reviewer.issuer(), reviewer.subject(), input.reasonCode(),
        input.notes());
    db.update("UPDATE publisher.submission SET status=?,updated_at=now() WHERE id=?",
        "APPROVED".equals(status) ? "PUBLISHED" : status, id);
    return Map.of("reviewId", reviewId, "status", status);
  }

  private void publish(PublisherOwnership.Submission submission) {
    if (submission.author() == null || submission.catalogBookId() == null || !submission.catalogLinked()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "known author and catalog link required");
    }
    var owner = submission.author();
    int changed = db.update("""
        INSERT INTO publisher.published_work(id,submission_id,book_id,publisher_subject_key,identity_issuer,identity_subject)
        VALUES (?,?,?,?,?,?)
        ON CONFLICT (submission_id) DO UPDATE SET status='PUBLISHED',withdrawn_at=NULL
          WHERE publisher.published_work.identity_issuer=excluded.identity_issuer
            AND publisher.published_work.identity_subject=excluded.identity_subject
            AND publisher.published_work.book_id=excluded.book_id
        """, UUID.randomUUID(), submission.id(), submission.catalogBookId(), submission.legacySubject(),
        owner.issuer(), owner.subject());
    if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "existing publication has a different provenance");
  }

  @GetMapping("/membership")
  public Object membership(Principal principal) {
    var actor = identities.author(principal);
    var results = db.queryForList("SELECT identity_subject AS subject_key,status,display_name,created_at,updated_at "
        + "FROM publisher.membership WHERE " + OWN, actor.issuer(), actor.subject());
    return results.isEmpty() ? Map.of("subject_key", actor.subject(), "status", "PENDING") : results.getFirst();
  }

  private void requireNotBlocked(TechnicalIdentity actor) {
    if (Boolean.TRUE.equals(db.queryForObject("""
        SELECT EXISTS (SELECT 1 FROM publisher.membership
          WHERE identity_issuer=? AND identity_subject=? AND status IN ('SUSPENDED','REVOKED'))
        """, Boolean.class, actor.issuer(), actor.subject()))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "publisher membership blocked");
    }
  }

  private void eventAfterCommit(String type, UUID book, Map<String, ?> payload, HttpServletRequest request) {
    String authorization = authorization(request);
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() { behavior.publish(type, book, payload, authorization); }
    });
  }

  private static String authorization(HttpServletRequest request) {
    return request == null ? null : request.getHeader("Authorization");
  }
}
