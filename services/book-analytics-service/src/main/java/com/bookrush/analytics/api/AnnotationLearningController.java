package com.bookrush.analytics.api;

import com.bookrush.analytics.quality.OrdinalAgreement;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Human ground-truth API. Raw labels are append-only; adjudication is separate. */
@RestController
@RequestMapping("/api/admin/v1/annotation")
@Tag(
    name = "Excerpt quality learning",
    description = "Campanhas, anotação cega, adjudicação e datasets")
public class AnnotationLearningController {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private static final List<String> DIMENSIONS =
      List.of("HOOK", "CLARITY", "AUTHENTICITY", "IMPACT", "PACING", "SELF_CONTAINMENT");

  public AnnotationLearningController(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  private String subject(Authentication a) {
    return a == null || a.getName() == null ? "unknown" : a.getName();
  }

  private String hash(Object value) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Map<String, Object> body(Map<String, Object> b) {
    return b == null ? Map.of() : b;
  }

  public record CampaignRequest(
      String code,
      String name,
      String protocolVersion,
      Integer targetAnnotationsPerItem,
      Map<String, Object> languagePolicy,
      Map<String, Object> samplingConfig) {}

  @PostMapping("/campaigns")
  @Transactional
  @Operation(summary = "Criar campanha")
  public ResponseEntity<?> create(@RequestBody CampaignRequest r, Authentication auth) {
    if (r.code() == null || r.code().isBlank() || r.name() == null || r.name().isBlank())
      return ResponseEntity.badRequest().body(Map.of("code", "INVALID_CAMPAIGN"));
    var sampling = body(r.samplingConfig());
    var hash = hash(sampling);
    var id = UUID.randomUUID();
    try {
      db.update(
          "INSERT INTO"
              + " analytics.annotation_campaign(id,code,name,protocol_version,target_annotations_per_item,language_policy,sampling_config,sampling_config_hash,created_by_subject)"
              + " VALUES (?,?,?,?,?,?::jsonb,?::jsonb,?,?)",
          id,
          r.code(),
          r.name(),
          r.protocolVersion() == null ? "annotation-protocol-v1" : r.protocolVersion(),
          r.targetAnnotationsPerItem() == null ? 3 : r.targetAnnotationsPerItem(),
          json.writeValueAsString(body(r.languagePolicy())),
          json.writeValueAsString(sampling),
          hash,
          subject(auth));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("code", "CAMPAIGN_CODE_EXISTS"));
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("id", id, "code", r.code(), "status", "DRAFT", "samplingConfigHash", hash));
  }

  @PostMapping("/campaigns/{id}/sample")
  @Transactional
  @Operation(summary = "Materializar amostra estratificada")
  public ResponseEntity<?> sample(
      @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> config) {
    var campaigns =
        db.queryForList(
            "SELECT target_annotations_per_item,sampling_config::text,sampling_config_hash,language_policy::text"
                + " FROM analytics.annotation_campaign WHERE id=?",
            id);
    if (campaigns.isEmpty()) return ResponseEntity.notFound().build();

    var campaign = campaigns.getFirst();
    int targetAnnotations = ((Number) campaign.get("target_annotations_per_item")).intValue();
    Map<String, Object> sampling = jsonMap(campaign.get("sampling_config"));
    Map<String, Object> languagePolicy = jsonMap(campaign.get("language_policy"));
    int requested = number(config, "items", number(sampling, "items", 100));
    int seed = number(config, "seed", number(sampling, "seed", 1337));

    List<String> languages = new ArrayList<>();
    Object configuredLanguages = languagePolicy.get("languages");
    if (configuredLanguages instanceof Collection<?> values) {
      for (Object value : values) {
        languages.add(String.valueOf(value).toLowerCase(Locale.ROOT));
      }
    }
    if (languages.isEmpty()) languages = List.of("en", "pt");
    int perLanguage = number(languagePolicy, "targetPerLanguage", requested / languages.size());
    int inserted = 0;
    for (String language : languages) {
      int quota = Math.min(perLanguage, requested - inserted);
      if (quota <= 0) break;
      inserted +=
          db.update(
              "INSERT INTO analytics.annotation_campaign_item"
                  + "(id,campaign_id,excerpt_id,sampling_stratum,sampling_reason,priority,target_annotations)"
                  + " SELECT gen_random_uuid(),?,e.id,LOWER(COALESCE(b.original_language,'und'))||':position',"
                  + " ?,ROW_NUMBER() OVER (ORDER BY md5(e.id::text||?))::int,?"
                  + " FROM analytics.excerpt e JOIN catalog.book_asset_version v ON v.id=e.source_asset_version_id"
                  + " JOIN catalog.book_asset a ON a.id=v.book_asset_id JOIN catalog.book b ON b.id=a.book_id"
                  + " WHERE LOWER(COALESCE(b.original_language,'und'))=? AND NOT EXISTS"
                  + " (SELECT 1 FROM analytics.annotation_campaign_item x WHERE x.campaign_id=? AND x.excerpt_id=e.id)"
                  + " ORDER BY md5(e.id::text||?) LIMIT ? ON CONFLICT (campaign_id,excerpt_id) DO NOTHING",
              id,
              "pilot language=" + language + " seed=" + seed,
              seed,
              targetAnnotations,
              language,
              id,
              seed,
              quota);
    }
    return ResponseEntity.ok(
        Map.of(
            "campaignId", id,
            "inserted", inserted,
            "requested", requested,
            "samplingConfigHash", campaign.get("sampling_config_hash"),
            "languages", languages,
            "seed", seed));
  }

  private int number(Map<String, Object> values, String key, int fallback) {
    Object value = values == null ? null : values.get(key);
    return value instanceof Number n ? Math.min(1000, Math.max(1, n.intValue())) : fallback;
  }

  private Map<String, Object> jsonMap(Object value) {
    try {
      if (value == null) return Map.of();
      if (value instanceof String text) {
        return json.readValue(text, new com.fasterxml.jackson.core.type.TypeReference<>() {});
      }
      return json.convertValue(value, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid campaign JSON configuration", e);
    }
  }

  @GetMapping("/campaigns")
  @Operation(summary = "Listar campanhas")
  public Map<String, Object> campaigns() {
    var rows =
        db.queryForList(
            "SELECT"
                + " id,code,name,status,protocol_version,target_annotations_per_item,created_by_subject,created_at,updated_at"
                + " FROM analytics.annotation_campaign ORDER BY created_at DESC");
    return Map.of("items", rows, "total", rows.size());
  }

  @PostMapping("/campaigns/{id}/{action}")
  @Transactional
  @Operation(summary = "Alterar ciclo de vida")
  public ResponseEntity<?> transition(
      @PathVariable UUID id, @PathVariable String action, Authentication auth) {
    String next =
        switch (action) {
          case "start" -> "RUNNING";
          case "pause" -> "PAUSED";
          case "complete" -> "COMPLETED";
          case "cancel" -> "CANCELLED";
          default -> null;
        };
    if (next == null) return ResponseEntity.badRequest().body(Map.of("code", "INVALID_ACTION"));
    int n =
        db.update(
            "UPDATE analytics.annotation_campaign SET status=?,updated_at=now(),version=version+1"
                + " WHERE id=? AND ((status='DRAFT' AND ?='RUNNING') OR (status='RUNNING' AND ? IN"
                + " ('PAUSED','COMPLETED','CANCELLED')) OR (status='PAUSED' AND ? IN"
                + " ('RUNNING','CANCELLED'))) ",
            next,
            id,
            next,
            next,
            next);
    if (n == 0)
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "INVALID_TRANSITION"));
    event(null, null, "CAMPAIGN_" + action.toUpperCase(), auth, Map.of());
    return ResponseEntity.ok(Map.of("id", id, "status", next));
  }

  @PostMapping("/campaigns/{id}/claim-next")
  @Transactional
  @Operation(summary = "Reivindicar próximo item")
  public ResponseEntity<?> claim(
      @PathVariable UUID id, @RequestParam(defaultValue = "900") long ttl, Authentication auth) {
    String who = subject(auth);
    var items =
        db.queryForList(
            "SELECT i.id,i.excerpt_id FROM analytics.annotation_campaign_item i WHERE"
                + " i.campaign_id=? AND i.state IN ('PENDING','IN_PROGRESS') AND NOT EXISTS (SELECT"
                + " 1 FROM analytics.annotation_assignment a WHERE a.campaign_item_id=i.id AND"
                + " a.status='ACTIVE' AND a.expires_at>now() AND a.annotator_subject<>?) AND NOT"
                + " EXISTS (SELECT 1 FROM analytics.annotation a WHERE a.campaign_item_id=i.id AND"
                + " a.annotator_subject=? AND a.primary_locked_at IS NOT NULL) ORDER BY i.priority"
                + " DESC,i.created_at LIMIT 1",
            id,
            who,
            who);
    if (items.isEmpty()) return ResponseEntity.noContent().build();
    var item = items.getFirst();
    UUID itemId = (UUID) item.get("id");
    UUID assignment = UUID.randomUUID();
    db.update(
        "INSERT INTO"
            + " analytics.annotation_assignment(id,campaign_item_id,annotator_subject,expires_at)"
            + " VALUES (?,?,?,now()+(?||' seconds')::interval) ON"
            + " CONFLICT(campaign_item_id,annotator_subject) DO UPDATE SET"
            + " expires_at=excluded.expires_at,status='ACTIVE',version=annotation_assignment.version+1",
        assignment,
        itemId,
        who,
        Math.max(60, Math.min(ttl, 86400)));
    db.update(
        "UPDATE analytics.annotation_campaign_item SET state='IN_PROGRESS' WHERE id=?", itemId);
    return ResponseEntity.ok(
        Map.of(
            "assignmentId",
            assignment,
            "campaignItemId",
            itemId,
            "excerptId",
            item.get("excerpt_id"),
            "expiresInSeconds",
            ttl));
  }

  @GetMapping("/campaigns/{id}/progress")
  @Operation(summary = "Consultar progresso do annotator na campanha")
  public ResponseEntity<?> progress(@PathVariable UUID id, Authentication auth) {
    String who = subject(auth);
    var rows =
        db.queryForList(
            "SELECT count(*) total, count(*) FILTER (WHERE i.state='COMPLETE' OR EXISTS ("
                + "SELECT 1 FROM analytics.annotation a WHERE a.campaign_item_id=i.id AND"
                + " a.annotator_subject=? AND a.status='SUBMITTED')) completed FROM"
                + " analytics.annotation_campaign_item i WHERE i.campaign_id=?",
            who,
            id);
    if (rows.isEmpty()) return ResponseEntity.notFound().build();
    Number total = (Number) rows.getFirst().get("total");
    Number completed = (Number) rows.getFirst().get("completed");
    return ResponseEntity.ok(
        Map.of(
            "campaignId", id,
            "total", total.longValue(),
            "completed", completed.longValue(),
            "remaining", Math.max(0L, total.longValue() - completed.longValue())));
  }

  @GetMapping("/items/{itemId}")
  @Operation(summary = "Obter item em modo cego")
  public ResponseEntity<?> item(@PathVariable UUID itemId, Authentication auth) {
    String who = subject(auth);
    var rows =
        db.queryForList(
            "SELECT i.id,i.campaign_id,i.excerpt_id,i.sampling_stratum,i.state,e.text"
                + " excerpt_text,e.word_count,e.text_sha256,a.id annotation_id,a.status"
                + " annotation_status,a.primary_locked_at FROM analytics.annotation_campaign_item i"
                + " JOIN analytics.excerpt e ON e.id=i.excerpt_id LEFT JOIN analytics.annotation a"
                + " ON a.campaign_item_id=i.id AND a.annotator_subject=? WHERE i.id=?",
            who,
            itemId);
    if (rows.isEmpty()) return ResponseEntity.notFound().build();
    var row = new LinkedHashMap<>(rows.getFirst());
    row.put("blind", true);
    row.remove("candidate_score");
    row.remove("ranker_version");
    row.remove("embedding_model");
    return ResponseEntity.ok(row);
  }

  public record FirstScreen(Integer hook) {}

  @PostMapping("/items/{itemId}/first-screen")
  @Transactional
  @Operation(summary = "Registrar primeira impressão")
  public ResponseEntity<?> first(
      @PathVariable UUID itemId, @RequestBody FirstScreen r, Authentication auth) {
    if (r.hook() == null || r.hook() < 1 || r.hook() > 5)
      return ResponseEntity.badRequest().body(Map.of("code", "INVALID_ORDINAL"));
    UUID aid = ensureAnnotation(itemId, subject(auth));
    db.update(
        "UPDATE analytics.annotation SET first_screen_hook=?,updated_at=now(),version=version+1"
            + " WHERE id=? AND primary_locked_at IS NULL",
        r.hook(),
        aid);
    event(aid, itemId, "FIRST_SCREEN_RATED", auth, Map.of("hook", r.hook()));
    return ResponseEntity.ok(Map.of("annotationId", aid, "recorded", true));
  }

  public record FullRating(
      Map<String, Integer> dimensions, Set<String> failureTags, Integer confidence) {}

  @PostMapping("/items/{itemId}/lock")
  @Transactional
  @Operation(summary = "Bloquear anotação primária sem revelar contexto")
  public ResponseEntity<?> lock(
      @PathVariable UUID itemId, @RequestBody FullRating r, Authentication auth) {
    String who = subject(auth);
    UUID aid = ensureAnnotation(itemId, who);
    if (r.dimensions() == null || !r.dimensions().keySet().containsAll(DIMENSIONS))
      return ResponseEntity.badRequest()
          .body(Map.of("code", "INCOMPLETE_DIMENSIONS", "required", DIMENSIONS));
    for (var e : r.dimensions().entrySet()) {
      if (e.getValue() == null || e.getValue() < 1 || e.getValue() > 5)
        return ResponseEntity.badRequest()
            .body(Map.of("code", "INVALID_ORDINAL", "dimension", e.getKey()));
      db.update(
          "INSERT INTO"
              + " analytics.annotation_dimension_value(annotation_id,dimension_code,stage,ordinal_value)"
              + " VALUES (?,?,'FULL_EXCERPT',?) ON CONFLICT (annotation_id,dimension_code,stage) DO"
              + " UPDATE SET ordinal_value=excluded.ordinal_value",
          aid,
          e.getKey(),
          e.getValue());
    }
    if (r.failureTags() != null)
      for (String tag : r.failureTags())
        db.update(
            "INSERT INTO analytics.annotation_failure_tag(annotation_id,tag) VALUES (?,?) ON"
                + " CONFLICT DO NOTHING",
            aid,
            tag);
    int locked =
        db.update(
            "UPDATE analytics.annotation SET"
                + " confidence=?,status='LOCKED',primary_locked_at=now(),updated_at=now(),version=version+1"
                + " WHERE id=? AND primary_locked_at IS NULL",
            r.confidence(),
            aid);
    if (locked == 0)
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("code", "PRIMARY_ALREADY_LOCKED"));
    event(aid, itemId, "LOCKED", auth, Map.of());
    return ResponseEntity.ok(Map.of("annotationId", aid, "locked", true, "contextAvailable", true));
  }

  @GetMapping("/items/{itemId}/context")
  @Operation(summary = "Revelar contexto após lock")
  public ResponseEntity<?> context(@PathVariable UUID itemId, Authentication auth) {
    String who = subject(auth);
    var locked =
        db.queryForList(
            "SELECT a.id FROM analytics.annotation a WHERE a.campaign_item_id=? AND"
                + " a.annotator_subject=? AND a.primary_locked_at IS NOT NULL",
            itemId,
            who);
    if (locked.isEmpty())
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(Map.of("code", "CONTEXT_LOCK_REQUIRED"));
    UUID aid = (UUID) locked.getFirst().get("id");
    db.update(
        "UPDATE analytics.annotation SET"
            + " context_revealed_at=COALESCE(context_revealed_at,now()),updated_at=now() WHERE"
            + " id=?",
        aid);
    var rows =
        db.queryForList(
            "SELECT"
                + " e.id,e.source_asset_version_id,e.chapter_id,e.text,e.start_codepoint,e.end_codepoint,e.text_sha256,e.word_count,e.sentence_count"
                + " FROM analytics.annotation_campaign_item i JOIN analytics.excerpt e ON"
                + " e.id=i.excerpt_id WHERE i.id=?",
            itemId);
    if (rows.isEmpty()) return ResponseEntity.notFound().build();
    var current = rows.getFirst();
    UUID excerptId = (UUID) rows.getFirst().get("id");
    UUID sourceVersion = (UUID) current.get("source_asset_version_id");
    UUID chapterId = (UUID) current.get("chapter_id");
    Integer start = ((Number) current.get("start_codepoint")).intValue();
    var previous =
        db.queryForList(
            "SELECT e.id,e.text,e.start_codepoint,e.end_codepoint,e.text_sha256,e.word_count,e.sentence_count"
                + " FROM analytics.excerpt e WHERE e.source_asset_version_id=? AND e.chapter_id IS NOT DISTINCT FROM ?"
                + " AND e.start_codepoint<? ORDER BY e.start_codepoint DESC LIMIT 1",
            sourceVersion,
            chapterId,
            start);
    var next =
        db.queryForList(
            "SELECT e.id,e.text,e.start_codepoint,e.end_codepoint,e.text_sha256,e.word_count,e.sentence_count"
                + " FROM analytics.excerpt e WHERE e.source_asset_version_id=? AND e.chapter_id IS NOT DISTINCT FROM ?"
                + " AND e.start_codepoint>? ORDER BY e.start_codepoint LIMIT 1",
            sourceVersion,
            chapterId,
            start);
    var features =
        db.queryForList(
            "SELECT"
                + " fd.code,fd.name,fd.semantic_category,fd.unit,fd.version,ef.numeric_value,ef.text_value,ef.json_value,ef.value_status,ef.confidence,ef.sample_size,ef.warning"
                + " FROM analytics.excerpt_feature ef JOIN analytics.feature_definition fd ON"
                + " fd.id=ef.feature_definition_id WHERE ef.excerpt_id=? ORDER BY"
                + " fd.semantic_category,fd.code,ef.computed_at DESC",
            excerptId);
    var ranks =
        db.queryForList(
            "SELECT"
                + " ranker_code,ranker_version,configuration_hash,components,weights,final_score,exclusion_reason,created_at"
                + " FROM analytics.excerpt_rank WHERE excerpt_id=? ORDER BY created_at DESC",
            excerptId);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("itemId", itemId);
    result.put("context", rows);
    result.put("previousContext", previous.isEmpty() ? null : previous.getFirst());
    result.put("nextContext", next.isEmpty() ? null : next.getFirst());
    result.put("modelFeatures", features);
    result.put("rankers", ranks);
    result.put("primaryReadOnly", true);
    result.put("scoresAvailableAfterLock", true);
    return ResponseEntity.ok(result);
  }

  public record Diagnostic(Boolean contextRequired, Integer severity, String notes) {}

  @PostMapping("/items/{itemId}/submit")
  @Transactional
  @Operation(summary = "Submeter diagnóstico pós-lock")
  public ResponseEntity<?> submit(
      @PathVariable UUID itemId, @RequestBody Diagnostic d, Authentication auth) {
    String who = subject(auth);
    var a =
        db.queryForList(
            "SELECT id FROM analytics.annotation WHERE campaign_item_id=? AND annotator_subject=?"
                + " AND primary_locked_at IS NOT NULL",
            itemId,
            who);
    if (a.isEmpty())
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("code", "PRIMARY_LOCK_REQUIRED"));
    UUID aid = (UUID) a.getFirst().get("id");
    db.update(
        "INSERT INTO"
            + " analytics.annotation_context_diagnostic(annotation_id,context_required,severity,notes)"
            + " VALUES (?,?,?,?) ON CONFLICT(annotation_id) DO UPDATE SET"
            + " context_required=excluded.context_required,severity=excluded.severity,notes=excluded.notes",
        aid,
        d.contextRequired(),
        d.severity(),
        d.notes());
    db.update(
        "UPDATE analytics.annotation SET"
            + " status='SUBMITTED',submitted_at=COALESCE(submitted_at,now()),version=version+1"
            + " WHERE id=?",
        aid);
    db.update(
        "UPDATE analytics.annotation_assignment SET status='COMPLETED' WHERE campaign_item_id=? AND"
            + " annotator_subject=?",
        itemId,
        who);
    db.update(
        "UPDATE analytics.annotation_campaign_item SET state='COMPLETE' WHERE id=? AND (SELECT"
            + " count(*) FROM analytics.annotation WHERE campaign_item_id=? AND"
            + " status='SUBMITTED')>=target_annotations",
        itemId,
        itemId);
    event(aid, itemId, "SUBMITTED", auth, Map.of());
    return ResponseEntity.ok(Map.of("annotationId", aid, "status", "SUBMITTED"));
  }

  @GetMapping("/campaigns/{id}/metrics")
  @Operation(summary = "Métricas operacionais e QA")
  public Map<String, Object> metrics(@PathVariable UUID id) {
    var dist =
        db.queryForList(
            "SELECT dv.dimension_code,avg(dv.ordinal_value) mean,percentile_cont(.5) within"
                + " group(order by dv.ordinal_value) median,percentile_cont(.75) within group(order"
                + " by dv.ordinal_value)-percentile_cont(.25) within group(order by"
                + " dv.ordinal_value) iqr,count(*) count FROM analytics.annotation_dimension_value"
                + " dv JOIN analytics.annotation a ON a.id=dv.annotation_id JOIN"
                + " analytics.annotation_campaign_item i ON i.id=a.campaign_item_id WHERE"
                + " i.campaign_id=? GROUP BY dv.dimension_code ORDER BY dv.dimension_code",
            id);
    var counts =
        db.queryForMap(
            "SELECT count(*) total,count(*) FILTER(WHERE a.status='SUBMITTED')"
                + " submitted,count(DISTINCT a.annotator_subject) annotators,avg(extract(epoch from"
                + " (a.submitted_at-a.created_at))*1000) elapsed_ms FROM analytics.annotation a"
                + " JOIN analytics.annotation_campaign_item i ON i.id=a.campaign_item_id WHERE"
                + " i.campaign_id=?",
            id);
    double alpha = ordinalAgreement(id);
    return Map.of(
        "campaignId",
        id,
        "dimensions",
        dist,
        "counts",
        counts,
        "krippendorffAlphaOrdinal",
        alpha,
        "qaStatus",
        dist.isEmpty() ? "INSUFFICIENT_SAMPLE" : "AVAILABLE");
  }

  @GetMapping("/campaigns/{id}/adjudication-queue")
  @Operation(summary = "Listar discordâncias")
  public Map<String, Object> disagreement(@PathVariable UUID id) {
    Integer configuredThreshold = db.queryForObject(
        "SELECT disagreement_threshold FROM analytics.annotation_campaign WHERE id=?", Integer.class, id);
    int threshold = configuredThreshold == null ? 2 : configuredThreshold;
    var rows =
        db.queryForList(
            "SELECT i.id campaign_item_id,d.dimension_code,min(d.ordinal_value)"
                + " min_value,max(d.ordinal_value) max_value,count(*) ratings FROM"
                + " analytics.annotation_campaign_item i JOIN analytics.annotation a ON"
                + " a.campaign_item_id=i.id JOIN analytics.annotation_dimension_value d ON"
                + " d.annotation_id=a.id WHERE i.campaign_id=? GROUP BY i.id,d.dimension_code"
                + " HAVING max(d.ordinal_value)-min(d.ordinal_value)>=? ORDER BY"
                + " i.id,d.dimension_code",
            id, threshold);
    return Map.of("campaignId", id, "items", rows, "threshold", threshold,
        "thresholdVersion", "quality-v1", "total", rows.size());
  }

  private double ordinalAgreement(UUID campaignId) {
    var values = db.queryForList("SELECT a.campaign_item_id,d.dimension_code,d.ordinal_value "
        + "FROM analytics.annotation a JOIN analytics.annotation_campaign_item i ON i.id=a.campaign_item_id "
        + "JOIN analytics.annotation_dimension_value d ON d.annotation_id=a.id "
        + "WHERE i.campaign_id=? AND a.status='SUBMITTED' AND d.stage='FULL_EXCERPT'", campaignId);
    var ratings = values.stream()
        .map(value -> new OrdinalAgreement.Rating(
            String.valueOf(value.get("campaign_item_id")),
            String.valueOf(value.get("dimension_code")),
            ((Number) value.get("ordinal_value")).intValue()))
        .toList();
    return OrdinalAgreement.agreement(ratings);
  }

  public record Adjudication(String dimensionCode, Integer ordinalValue, String rationale) {}

  @PostMapping("/items/{itemId}/adjudicate")
  @Transactional
  @Operation(summary = "Registrar adjudicação sem alterar labels brutos")
  public ResponseEntity<?> adjudicate(
      @PathVariable UUID itemId, @RequestBody Adjudication a, Authentication auth) {
    if (a.rationale() == null || a.rationale().isBlank())
      return ResponseEntity.badRequest().body(Map.of("code", "RATIONALE_REQUIRED"));
    if (a.ordinalValue() != null && (a.ordinalValue() < 1 || a.ordinalValue() > 5))
      return ResponseEntity.badRequest().body(Map.of("code", "INVALID_ORDINAL"));
    UUID id = UUID.randomUUID();
    db.update(
        "INSERT INTO"
            + " analytics.annotation_adjudication(id,campaign_item_id,reviewer_subject,dimension_code,ordinal_value,rationale)"
            + " VALUES (?,?,?,?,?,?)",
        id,
        itemId,
        subject(auth),
        a.dimensionCode(),
        a.ordinalValue(),
        a.rationale());
    event(
        null, itemId, "ADJUDICATED", auth, Map.of("dimension", String.valueOf(a.dimensionCode())));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("id", id, "rawLabelsPreserved", true));
  }

  @PostMapping("/campaigns/{id}/datasets")
  @Transactional
  @Operation(summary = "Publicar snapshot imutável")
  public ResponseEntity<?> dataset(@PathVariable UUID id, Authentication auth) {
    Integer version =
        db.queryForObject(
            "SELECT COALESCE(max(version_number),0)+1 FROM analytics.annotation_dataset_version"
                + " WHERE campaign_id=?",
            Integer.class,
            id);
    String samplingConfigHash = db.queryForObject(
        "SELECT sampling_config_hash FROM analytics.annotation_campaign WHERE id=?",
        String.class, id);
    var lineage = db.queryForList(
        "SELECT DISTINCT e.source_asset_version_id FROM analytics.annotation_campaign_item i "
            + "JOIN analytics.excerpt e ON e.id=i.excerpt_id WHERE i.campaign_id=? "
            + "ORDER BY e.source_asset_version_id",
        id).stream().map(row -> String.valueOf(row.get("source_asset_version_id"))).toList();
    String lineageHash = hash(lineage);
    String manifest = hash(List.of(id, version, samplingConfigHash, lineageHash, Instant.now().toString()));
    UUID did = UUID.randomUUID();
    db.update(
        "INSERT INTO"
            + " analytics.annotation_dataset_version(id,campaign_id,version_number,manifest_sha256,sampling_config_hash,lineage_sha256,created_by_subject)"
            + " VALUES (?,?,?,?,?,?,?)",
        did,
        id,
        version,
        manifest,
        samplingConfigHash,
        lineageHash,
        subject(auth));
    db.update(
        "INSERT INTO"
            + " analytics.annotation_dataset_item(dataset_version_id,campaign_item_id,raw_labels,adjudicated_labels)"
            + " SELECT"
            + " ?,i.id,COALESCE(jsonb_agg(jsonb_build_object('annotationId',a.id,'annotator',a.annotator_subject,'dimensions',(SELECT"
            + " jsonb_object_agg(dimension_code,ordinal_value) FROM"
            + " analytics.annotation_dimension_value WHERE annotation_id=a.id))),"
            + " '[]'::jsonb),COALESCE((SELECT"
            + " jsonb_agg(jsonb_build_object('reviewer',ad.reviewer_subject,'dimension',ad.dimension_code,'value',ad.ordinal_value,'rationale',ad.rationale))"
            + " FROM analytics.annotation_adjudication ad WHERE"
            + " ad.campaign_item_id=i.id),'[]'::jsonb) FROM analytics.annotation_campaign_item i"
            + " LEFT JOIN analytics.annotation a ON a.campaign_item_id=i.id WHERE i.campaign_id=?"
            + " GROUP BY i.id",
        did,
        id);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of(
            "id", did,
            "version", version,
            "manifestSha256", manifest,
            "samplingConfigHash", samplingConfigHash,
            "lineageSha256", lineageHash,
            "immutable", true));
  }

  @GetMapping("/campaigns/{id}/datasets/{version}/export")
  @Operation(summary = "Exportar dataset publicado")
  public ResponseEntity<?> export(@PathVariable UUID id, @PathVariable int version) {
    var rows =
        db.queryForList(
            "SELECT"
                + " d.id,d.version_number,d.manifest_sha256,d.sampling_config_hash,d.lineage_sha256,"
                + "i.campaign_item_id,i.raw_labels,i.adjudicated_labels"
                + " FROM analytics.annotation_dataset_version d JOIN"
                + " analytics.annotation_dataset_item i ON i.dataset_version_id=d.id WHERE"
                + " d.campaign_id=? AND d.version_number=? AND d.status='PUBLISHED'",
            id,
            version);
    return rows.isEmpty()
        ? ResponseEntity.notFound().build()
        : ResponseEntity.ok(Map.of("campaignId", id, "version", version, "items", rows));
  }

  private UUID ensureAnnotation(UUID item, String who) {
    var rows =
        db.queryForList(
            "SELECT id FROM analytics.annotation WHERE campaign_item_id=? AND annotator_subject=?",
            item,
            who);
    if (!rows.isEmpty()) return (UUID) rows.getFirst().get("id");
    UUID id = UUID.randomUUID();
    db.update(
        "INSERT INTO analytics.annotation(id,campaign_item_id,annotator_subject,protocol_version)"
            + " SELECT ?,id,?,'annotation-protocol-v1' FROM analytics.annotation_campaign_item"
            + " WHERE id=?",
        id,
        who,
        item);
    return id;
  }

  private void event(
      UUID aid, UUID item, String type, Authentication auth, Map<String, Object> payload) {
    try {
      db.update(
          "INSERT INTO"
              + " analytics.annotation_event(id,annotation_id,campaign_item_id,event_type,actor_subject,payload)"
              + " VALUES (?,?,?,?,?,?::jsonb)",
          UUID.randomUUID(),
          aid,
          item,
          type,
          subject(auth),
          json.writeValueAsString(payload));
    } catch (Exception ignored) {
    }
  }
}
