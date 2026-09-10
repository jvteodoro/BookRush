package com.bookrush.ingestion.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Durable command boundary for ingestion jobs. Processors consume its queued tasks. */
@Service
public class IngestionJobService {
  private static final String ENDPOINT = "POST /api/admin/v1/ingestion/run";
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final IngestionDispatcher dispatcher;
  public IngestionJobService(JdbcTemplate jdbc, ObjectMapper mapper, IngestionDispatcher dispatcher) { this.jdbc = jdbc; this.mapper = mapper; this.dispatcher = dispatcher; }

  @Transactional
  public Submission submit(IngestionRunRequest request, String idempotencyKey, String principal) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("Idempotency-Key is required");
    var actor = principal == null || principal.isBlank() ? "anonymous" : principal.trim();
    var parameters = canonicalParameters(request);
    var requestHash = sha256(parameters);
    var replay = findCommand(actor, idempotencyKey);
    if (replay != null) {
      if (!requestHash.equals(replay.requestHash())) throw new IdempotencyConflictException("Idempotency-Key was already used with a different request");
      return new Submission(replay.jobId(), replay.body(), true);
    }
    var sourceId = sourceId(request.source());
    var existing = jdbc.query("SELECT id, status FROM catalog.ingestion_job WHERE source_id=? AND parameter_fingerprint=?", (rs, row) -> new ExistingJob(rs.getObject("id", UUID.class), rs.getString("status")), sourceId, requestHash).stream().findFirst().orElse(null);
    var job = existing == null ? createJob(sourceId, request, parameters, requestHash, actor) : existing;
    var body = Map.<String, Object>of("jobId", job.id(), "status", job.status(), "dryRun", request.dryRun(), "location", "/api/admin/v1/ingestion/jobs/" + job.id());
    jdbc.update("INSERT INTO catalog.ingestion_command_result (id, principal, endpoint, idempotency_key, request_hash, response_status, response_body) VALUES (?, ?, ?, ?, ?, 202, ?) ON CONFLICT (principal, endpoint, idempotency_key) DO NOTHING", UUID.randomUUID(), actor, ENDPOINT, idempotencyKey, requestHash, jsonb(body));
    var stored = findCommand(actor, idempotencyKey);
    if (stored == null) throw new IllegalStateException("idempotency result was not persisted");
    if (!requestHash.equals(stored.requestHash())) throw new IdempotencyConflictException("Idempotency-Key was already used with a different request");
    if (!stored.jobId().equals(job.id())) return new Submission(stored.jobId(), stored.body(), true);
    if (!stored.body().containsKey("replayed")) {
      var dispatchedJob = job.id();
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { dispatcher.dispatch(dispatchedJob); }
      });
    }
    return new Submission(job.id(), body, false);
  }

  public Map<String, Object> getJob(UUID id) {
    var rows = jdbc.queryForList("SELECT j.id, s.code AS source, j.status, j.trigger_type, j.parameters::text AS parameters, j.requested_by, j.items_discovered, j.items_processed, j.items_succeeded, j.items_failed, j.error_message, j.cancel_requested_at, j.started_at, j.finished_at, j.created_at, j.updated_at FROM catalog.ingestion_job j JOIN catalog.source s ON s.id=j.source_id WHERE j.id=?", id);
    if (rows.isEmpty()) throw new JobNotFoundException(id);
    var result = new LinkedHashMap<String, Object>(rows.getFirst());
    if (result.get("parameters") instanceof String json) {
      try { result.put("parameters", mapper.readTree(json)); } catch (JsonProcessingException e) { throw new IllegalStateException("invalid job parameters", e); }
    }
    return result;
  }

  public List<Map<String, Object>> getItems(UUID jobId, int page, int size) {
    if (page < 0 || size < 1 || size > 500) throw new IllegalArgumentException("invalid page or size");
    getJob(jobId);
    return jdbc.queryForList("SELECT i.id, i.external_identifier, i.status, i.attempt_number, i.book_id, i.edition_id, i.error_code, i.error_message, i.started_at, i.finished_at, t.status AS task_status, t.attempt_count, t.next_attempt_at, t.reason_code FROM catalog.ingestion_item i LEFT JOIN catalog.ingestion_task t ON t.ingestion_item_id=i.id WHERE i.ingestion_job_id=? ORDER BY i.created_at, i.id LIMIT ? OFFSET ?", jobId, size, page * size);
  }

  @Transactional
  public Map<String, Object> cancel(UUID id, String principal) {
    getJob(id);
    jdbc.update("UPDATE catalog.ingestion_job SET status='CANCEL_REQUESTED', cancel_requested_at=clock_timestamp(), updated_at=clock_timestamp() WHERE id=? AND status IN ('PENDING','RUNNING','PAUSED')", id);
    jdbc.update("UPDATE catalog.ingestion_task SET status='CANCELLED', reason_code='JOB_CANCELLED', finished_at=clock_timestamp(), lease_until=NULL, updated_at=clock_timestamp() WHERE ingestion_job_id=? AND status IN ('PENDING','RETRY_WAIT')", id);
    jdbc.update("UPDATE catalog.ingestion_item SET status='CANCELLED', finished_at=clock_timestamp(), updated_at=clock_timestamp() WHERE ingestion_job_id=? AND status IN ('PENDING','RUNNING')", id);
    audit(id, principal, "INGESTION_CANCEL_REQUESTED");
    return getJob(id);
  }

  @Transactional
  public Map<String, Object> resume(UUID id, String principal) {
    var job = getJob(id);
    var status = String.valueOf(job.get("status"));
    if (!List.of("CANCELLED", "CANCEL_REQUESTED", "PAUSED", "FAILED", "COMPLETED_WITH_ERRORS").contains(status)) throw new IllegalStateException("job cannot be resumed from status " + status);
    jdbc.update("UPDATE catalog.ingestion_job SET status='PENDING', cancel_requested_at=NULL, error_message=NULL, finished_at=NULL, updated_at=clock_timestamp() WHERE id=?", id);
    jdbc.update("UPDATE catalog.ingestion_task SET status='PENDING', reason_code=NULL, next_attempt_at=clock_timestamp(), lease_until=NULL, claimed_by=NULL, finished_at=NULL, updated_at=clock_timestamp() WHERE ingestion_job_id=? AND status IN ('CANCELLED','FAILED','RETRY_WAIT')", id);
    jdbc.update("UPDATE catalog.ingestion_item SET status='PENDING', error_code=NULL, error_message=NULL, finished_at=NULL, updated_at=clock_timestamp() WHERE ingestion_job_id=? AND status IN ('CANCELLED','FAILED')", id);
    audit(id, principal, "INGESTION_RESUMED");
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() { dispatcher.dispatch(id); }
    });
    return getJob(id);
  }

  public Map<String, Object> latestJob() {
    var rows = jdbc.queryForList("SELECT id FROM catalog.ingestion_job ORDER BY created_at DESC, id DESC LIMIT 1");
    return rows.isEmpty() ? Map.of("status", "IDLE") : getJob((UUID) rows.getFirst().get("id"));
  }

  private ExistingJob createJob(UUID sourceId, IngestionRunRequest request, String parameters, String hash, String actor) {
    var id = UUID.randomUUID();
    jdbc.update("INSERT INTO catalog.ingestion_job (id, source_id, status, trigger_type, parameters, parameter_fingerprint, requested_by) VALUES (?, ?, 'PENDING', 'MANUAL', ?, ?, ?)", id, sourceId, jsonb(parameters), hash, actor);
    if (request.externalIds().isEmpty()) throw new IllegalArgumentException("externalIds is required until catalog acquisition is enabled");
    for (var externalId : request.externalIds()) {
      var itemId = UUID.randomUUID();
      var logicalId = UUID.randomUUID();
      var itemKey = request.source().toLowerCase() + ":" + externalId;
      jdbc.update("INSERT INTO catalog.ingestion_logical_item(id, ingestion_job_id, source_id, item_key, external_identifier) VALUES (?, ?, ?, ?, ?)", logicalId, id, sourceId, itemKey, externalId);
      jdbc.update("INSERT INTO catalog.ingestion_item(id, ingestion_job_id, source_id, external_identifier, item_key, logical_item_id) VALUES (?, ?, ?, ?, ?, ?)", itemId, id, sourceId, externalId, itemKey, logicalId);
      jdbc.update("INSERT INTO catalog.ingestion_task(id, ingestion_job_id, ingestion_item_id, operation_key, task_type) VALUES (?, ?, ?, ?, 'GUTENBERG_IMPORT')", UUID.randomUUID(), id, itemId, "gutenberg:" + externalId + ":" + hash);
    }
    jdbc.update("UPDATE catalog.ingestion_job SET items_discovered=?, updated_at=clock_timestamp() WHERE id=?", request.externalIds().size(), id);
    return new ExistingJob(id, "PENDING");
  }
  private UUID sourceId(String code) { return jdbc.queryForObject("SELECT id FROM catalog.source WHERE code=? AND is_active", UUID.class, code); }
  private String canonicalParameters(IngestionRunRequest request) {
    var values = new LinkedHashMap<String, Object>();
    values.put("source", request.source()); values.put("externalIds", request.externalIds()); values.put("languages", request.languages()); values.put("maxItems", request.maxItems()); values.put("dryRun", request.dryRun()); values.put("processAssets", request.processAssets());
    try { return mapper.writeValueAsString(values); } catch (JsonProcessingException e) { throw new IllegalStateException("cannot serialize ingestion parameters", e); }
  }
  private SqlParameterValue jsonb(Object value) {
    try { return new SqlParameterValue(java.sql.Types.OTHER, value instanceof String s ? s : mapper.writeValueAsString(value)); } catch (Exception e) { throw new IllegalStateException("cannot serialize JSONB", e); }
  }
  private String sha256(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
  }
  private ExistingCommand findCommand(String principal, String key) {
    var rows = jdbc.query("SELECT request_hash, response_body::text AS response_body FROM catalog.ingestion_command_result WHERE principal=? AND endpoint=? AND idempotency_key=?", (rs, row) -> new ExistingCommand(rs.getString("request_hash"), readBody(rs.getString("response_body"))), principal, ENDPOINT, key);
    return rows.isEmpty() ? null : rows.getFirst();
  }
  @SuppressWarnings("unchecked") private Map<String, Object> readBody(String value) { try { return mapper.readValue(value, Map.class); } catch (Exception e) { throw new IllegalStateException("invalid command result stored in database", e); } }
  private void audit(UUID id, String principal, String action) { jdbc.update("INSERT INTO catalog.audit_event(id, actor, action, entity_type, entity_id) VALUES (?, ?, ?, 'INGESTION_JOB', ?)", UUID.randomUUID(), principal == null ? "anonymous" : principal, action, id); }
  public record Submission(UUID jobId, Map<String, Object> body, boolean replay) {}
  private record ExistingJob(UUID id, String status) {}
  private record ExistingCommand(String requestHash, Map<String, Object> body) { UUID jobId() { return UUID.fromString(String.valueOf(body.get("jobId"))); } }
  public static class IdempotencyConflictException extends RuntimeException { public IdempotencyConflictException(String message) { super(message); } }
  public static class JobNotFoundException extends RuntimeException { public JobNotFoundException(UUID id) { super("ingestion job not found: " + id); } }
}
