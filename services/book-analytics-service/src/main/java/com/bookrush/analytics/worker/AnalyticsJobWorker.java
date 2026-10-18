package com.bookrush.analytics.worker;

import com.bookrush.analytics.config.AnalyticsV2GeneratorProperties;
import com.bookrush.analytics.excerpts.CandidatePruner;
import com.bookrush.analytics.excerpts.ExcerptCandidateGenerator;
import com.bookrush.analytics.excerpts.HeuristicRankerConfig;
import com.bookrush.analytics.features.EmbeddingMath;
import com.bookrush.analytics.features.LinguisticRuntimeClient;
import com.bookrush.analytics.features.TextFeatureCalculator;
import com.bookrush.analytics.llm.TopNliCandidateSelector;
import com.bookrush.analytics.observability.AnalyticsMetrics;
import com.bookrush.analytics.storage.TextAssetReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Persistent, bounded worker for the deterministic analytics baseline. Claiming and completion are
 * short database transactions; object storage I/O happens outside them. Providers that need models
 * remain optional adapters.
 */
@Component
@ConditionalOnProperty(name = "analytics.worker.enabled", havingValue = "true")
public final class AnalyticsJobWorker {
  private static final Logger log = LoggerFactory.getLogger(AnalyticsJobWorker.class);
  private final JdbcTemplate jdbc;
  private final S3Client s3;
  private final AnalyticsMetrics metrics;
  private final AnalyticsV2GeneratorProperties generatorProperties;
  private final LinguisticRuntimeClient linguisticRuntime;
  private final int staleAfterSeconds;
  private final int maxAttempts;
  private final int retryDelaySeconds;
  private final ExecutorService processingExecutor;
  private final Semaphore processingSlots;
  private final com.bookrush.analytics.excerpts.SupervisedCandidateScorer supervisedScorer;
  private final HeuristicRankerConfig heuristicRankerConfig;

  public AnalyticsJobWorker(
      JdbcTemplate jdbc,
      S3Client s3,
      AnalyticsMetrics metrics,
      AnalyticsV2GeneratorProperties generatorProperties,
      com.bookrush.analytics.config.AnalyticsRuntimeProperties runtimeProperties,
      @Value("${analytics.worker.stale-after-seconds:900}") int staleAfterSeconds,
      @Value("${analytics.worker.max-attempts:3}") int maxAttempts,
      @Value("${analytics.worker.retry-delay-seconds:30}") int retryDelaySeconds,
      @Value("${analytics.worker.concurrency:1}") int concurrency,
      @Value("${analytics.scorer.enabled:false}") boolean scorerEnabled,
      @Value("${analytics.scorer.artifact:${analytics.models.cache-dir}/candidate-scorer.json}") String scorerArtifact,
      @Value("${analytics.scorer.allow-experimental:false}") boolean allowExperimentalScorer,
      @Value("${analytics.ranker.config:classpath:analytics-ranker-v1.json}") Resource rankerConfigResource) {
    this.jdbc = jdbc;
    this.s3 = s3;
    this.metrics = metrics;
    this.generatorProperties = generatorProperties;
    this.linguisticRuntime = new LinguisticRuntimeClient(runtimeProperties);
    this.staleAfterSeconds = normalizeStaleAfterSeconds(staleAfterSeconds);
    this.maxAttempts = Math.max(1, maxAttempts);
    this.retryDelaySeconds = Math.max(1, retryDelaySeconds);
    int workerConcurrency = Math.max(1, concurrency);
    this.processingSlots = new Semaphore(workerConcurrency);
    this.processingExecutor = Executors.newFixedThreadPool(workerConcurrency, task -> {
      var thread = new Thread(task, "analytics-item-worker");
      thread.setDaemon(true);
      return thread;
    });
    this.heuristicRankerConfig = loadHeuristicRankerConfig(rankerConfigResource);
    this.supervisedScorer = loadSupervisedScorer(scorerEnabled, scorerArtifact, allowExperimentalScorer);
  }

  private HeuristicRankerConfig loadHeuristicRankerConfig(Resource resource) {
    try {
      var config = HeuristicRankerConfig.load(resource.getInputStream());
      log.info("analytics heuristic ranker configured code={} version={} state={}",
          config.code(), config.version(), config.state());
      return config;
    } catch (IOException e) {
      throw new IllegalStateException("unable to load analytics ranker configuration", e);
    }
  }

  private com.bookrush.analytics.excerpts.SupervisedCandidateScorer loadSupervisedScorer(
      boolean enabled, String artifact, boolean allowExperimental) {
    if (!enabled) return null;
    var scorer = com.bookrush.analytics.excerpts.SupervisedCandidateScorer.load(Path.of(artifact), allowExperimental);
    log.info("analytics supervised scorer enabled code={} version={} artifact={}",
        "EXCERPT_SUPERVISED_SCORER_V1", "configured", artifact);
    return scorer;
  }

  @Scheduled(fixedDelayString = "${analytics.worker.poll-delay-ms:1000}")
  public void poll() {
    recoverStaleItems();
    if (!processingSlots.tryAcquire()) return;
    var item = claimOne();
    if (item == null) {
      processingSlots.release();
      return;
    }
    try {
      processingExecutor.execute(() -> processClaimedItem(item));
    } catch (RuntimeException rejected) {
      processingSlots.release();
      throw rejected;
    }
  }

  private void processClaimedItem(Item item) {
    try {
      process(item);
      complete(item);
    } catch (Exception e) {
      fail(item, errorCode(e), e.getMessage(), e);
    } finally {
      processingSlots.release();
    }
  }

  @PreDestroy
  void shutdown() {
    processingExecutor.shutdownNow();
  }

  private Item claimOne() {
    return jdbc.query(
        claimSql(),
        rs ->
            rs.next()
                ? new Item(
                    rs.getObject("id", UUID.class),
                    rs.getObject("job_id", UUID.class),
                    rs.getObject("input_asset_version_id", UUID.class),
                    rs.getInt("attempt_count"))
                : null,
        maxAttempts,
        retryDelaySeconds);
  }

  static String claimSql() {
    return """
WITH next_item AS (
  SELECT i.id,
         GREATEST(i.attempt_count, COALESCE((SELECT MAX(p.attempt_count)
           FROM analytics.analysis_phase_timing p WHERE p.job_item_id=i.id), 0)) AS effective_attempt_count
    FROM analytics.analysis_job_item i
    JOIN analytics.analysis_job j ON j.id=i.job_id
   WHERE i.status='PENDING'
     AND GREATEST(i.attempt_count, COALESCE((SELECT MAX(p.attempt_count)
       FROM analytics.analysis_phase_timing p WHERE p.job_item_id=i.id), 0)) < ?
     AND (i.finished_at IS NULL OR i.finished_at <= clock_timestamp() -
       (? * GREATEST(GREATEST(i.attempt_count, COALESCE((SELECT MAX(p.attempt_count)
         FROM analytics.analysis_phase_timing p WHERE p.job_item_id=i.id), 0)), 1)
         * INTERVAL '1 second'))
     AND j.cancel_requested=false AND j.status IN ('PENDING','RUNNING')
  ORDER BY i.created_at, i.id FOR UPDATE SKIP LOCKED LIMIT 1
)
UPDATE analytics.analysis_job_item i
   SET status='RUNNING', attempt_count=n.effective_attempt_count+1, started_at=clock_timestamp(), finished_at=NULL,
       error_code=NULL, error_message=NULL,
       progress_stage='CLAIMED', progress_completed=0, progress_total=0,
       progress_message='Item atribuído ao worker', progress_updated_at=clock_timestamp()
  FROM next_item n WHERE i.id=n.id
RETURNING i.id, i.job_id, i.input_asset_version_id, i.attempt_count
""";
  }

  /**
   * Returns items abandoned by a worker/container restart to the durable queue.
   *
   * <p>The progress heartbeat prevents a crashed worker from permanently blocking a job while leaving
   * genuinely active work enough time to finish. Only active, non-cancelled jobs are recovered.
   */
  int recoverStaleItems() {
    int orphanedPhases = jdbc.update(orphanedPhaseRecoverySql());
    int recoveredPhases = jdbc.update(stalePhaseRecoverySql(), staleAfterSeconds);
    int recoveredItems = jdbc.update(
        staleLeaseRecoverySql(), maxAttempts, maxAttempts, maxAttempts, maxAttempts,
        maxAttempts, maxAttempts, staleAfterSeconds);
    int exhaustedItems = jdbc.update(exhaustedPendingRecoverySql(), maxAttempts);
    if (orphanedPhases > 0 || recoveredPhases > 0 || recoveredItems > 0 || exhaustedItems > 0) {
      log.warn("analytics stale recovery orphanedPhases={} phases={} items={} exhaustedPending={}",
          orphanedPhases, recoveredPhases, recoveredItems, exhaustedItems);
    }
    return recoveredItems + exhaustedItems;
  }

  static int normalizeStaleAfterSeconds(int seconds) {
    return Math.max(1, seconds);
  }

  static String staleLeaseRecoverySql() {
    return """
UPDATE analytics.analysis_job_item i
   SET status=CASE WHEN i.attempt_count >= ? THEN 'FAILED' ELSE 'PENDING' END,
       started_at=NULL,
       finished_at=CASE WHEN i.attempt_count >= ? THEN clock_timestamp() ELSE NULL END,
       analysis_run_id=NULL,
       progress_stage=CASE WHEN i.attempt_count >= ? THEN 'FAILED' ELSE 'RECOVERED' END,
       progress_message=CASE WHEN i.attempt_count >= ?
           THEN 'Lease expirado após atingir o limite de tentativas'
           ELSE 'Lease expirado; item recolocado na fila' END,
       error_code=CASE WHEN i.attempt_count >= ? THEN 'WORKER_RESTART_RECOVERY' ELSE NULL END,
       error_message=CASE WHEN i.attempt_count >= ?
           THEN 'O worker foi interrompido após o limite de tentativas'
           ELSE NULL END,
       progress_updated_at=clock_timestamp()
  FROM analytics.analysis_job j
 WHERE i.job_id=j.id
   AND i.status='RUNNING'
   AND j.cancel_requested=false
   AND j.status IN ('PENDING','RUNNING')
   AND COALESCE(i.progress_updated_at, i.started_at) < clock_timestamp() - (? * INTERVAL '1 second')
""";
  }

  static String stalePhaseRecoverySql() {
    return """
UPDATE analytics.analysis_phase_timing p
   SET status='FAILED', finished_at=clock_timestamp(),
       duration_ms=GREATEST(0, (EXTRACT(EPOCH FROM (clock_timestamp() - p.started_at)) * 1000)::bigint),
       error_code='WORKER_RESTART_RECOVERY'
  FROM analytics.analysis_job_item i
  JOIN analytics.analysis_job j ON j.id=i.job_id
 WHERE p.job_item_id=i.id
   AND p.job_id=i.job_id
   AND p.attempt_count=i.attempt_count
   AND p.status='RUNNING'
   AND i.status='RUNNING'
   AND j.cancel_requested=false
   AND j.status IN ('PENDING','RUNNING')
   AND COALESCE(i.progress_updated_at, i.started_at) < clock_timestamp() - (? * INTERVAL '1 second')
""";
  }

  static String orphanedPhaseRecoverySql() {
    return """
UPDATE analytics.analysis_phase_timing p
   SET status='FAILED', finished_at=COALESCE(p.finished_at, clock_timestamp()),
       duration_ms=GREATEST(0, (EXTRACT(EPOCH FROM (COALESCE(p.finished_at, clock_timestamp()) - p.started_at)) * 1000)::bigint),
       error_code='WORKER_RESTART_RECOVERY'
  FROM analytics.analysis_job_item i
 WHERE p.job_item_id=i.id
   AND p.job_id=i.job_id
   AND p.status='RUNNING'
   AND p.attempt_count < i.attempt_count
""";
  }

  static String exhaustedPendingRecoverySql() {
    return """
UPDATE analytics.analysis_job_item i
   SET status='FAILED', finished_at=COALESCE(i.finished_at, clock_timestamp()),
       progress_stage='FAILED',
       progress_message='Item pendente atingiu o limite de tentativas',
       progress_updated_at=clock_timestamp(),
       error_code='WORKER_RESTART_RECOVERY',
       error_message='Item não pode ser reclamado após o limite de tentativas'
  FROM analytics.analysis_job j
 WHERE i.job_id=j.id
   AND i.status='PENDING'
   AND i.attempt_count >= ?
   AND j.cancel_requested=false
   AND j.status IN ('PENDING','RUNNING')
""";
  }

  private void process(Item item) throws Exception {
    timed(item, "ITEM_TOTAL", () -> {
      processPhases(item);
      return null;
    });
  }

  private void processPhases(Item item) throws Exception {
    updateProgress(item, "LOADING_TEXT", 0, 1, "Carregando texto normalizado");
    var physical =
        timed(item, "LOAD_METADATA", () -> jdbc.query(
            """
            SELECT v.bucket, v.object_key, v.size_bytes, v.sha256
              FROM catalog.book_asset_version v
             WHERE v.id=? AND v.status='AVAILABLE' AND v.availability_status='AVAILABLE'
            """,
            rs ->
                rs.next()
                    ? new Physical(
                        rs.getString("bucket"),
                        rs.getString("object_key"),
                        rs.getLong("size_bytes"),
                        rs.getString("sha256"))
                    : null,
            item.inputVersion()));
    if (physical == null) throw new IllegalStateException("ASSET_VERSION_UNAVAILABLE");
    String text;
    text = timed(item, "READ_ASSET", () -> {
      try (var stream =
          s3.getObject(
              GetObjectRequest.builder().bucket(physical.bucket()).key(physical.key()).build())) {
        return TextAssetReader.readNormalized(stream, physical.size(), physical.sha256());
      }
    });
    var generatorConfig = generatorProperties.candidateConfig();
    var configuration = configurationJson(generatorConfig);
    var configurationHash = sha256(configuration);
    var analyzer =
        jdbc.queryForObject(
            "SELECT id FROM analytics.analyzer WHERE code='deterministic-text-v2'", UUID.class);
    var run =
        jdbc.query(
            "SELECT id FROM analytics.analysis_run WHERE input_asset_version_id=? AND analyzer_id=?"
                + " AND status='COMPLETED' AND configuration->>'generatorVersion'=? ORDER BY"
                + " created_at DESC LIMIT 1",
            rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
            item.inputVersion(),
            analyzer,
            generatorConfig.version());
    if (run == null) {
      run = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO"
              + " analytics.analysis_run(id,analyzer_id,input_asset_version_id,status,configuration,configuration_hash,code_version,stage,started_at)"
              + " VALUES (?,?,?,'RUNNING',?::jsonb,?,?,'EXCERPT_CHEAP_FEATURES',clock_timestamp())",
          run,
          analyzer,
          item.inputVersion(),
          configuration,
          configurationHash,
          "2");
      var chapters = timed(item, "CHAPTER_DISCOVERY",
          () -> chapters(item.inputVersion(), TextAssetReader.codePointLength(text)));
      String catalogLanguage = languageFor(item.inputVersion());
      String language = catalogLanguage;
      var detected = timed(item, "LANGUAGE_VALIDATION", () -> linguisticRuntime.language(text));
      if ("VALID".equals(detected.status())
          && ("en".equals(detected.language()) || "pt".equals(detected.language()))
          && detected.confidence() >= 0.60d) language = detected.language();
      persistLanguageValidation(run, item.inputVersion(), catalogLanguage, detected);
      var chapterById =
          chapters.stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      ExcerptCandidateGenerator.ChapterBoundary::chapterId, chapter -> chapter));
      int pilotLimit =
          Integer.parseInt(System.getenv().getOrDefault("ANALYTICS_MAX_EXCERPTS_PER_ITEM", "500"));
      var generatedCandidates = timed(item, "EXCERPT_SELECTION", () ->
          ExcerptCandidateGenerator.generateV2(text, chapters, generatorConfig));
      if (pilotLimit > 0 && generatedCandidates.size() > pilotLimit * 3) {
        generatedCandidates = generatedCandidates.subList(0, pilotLimit * 3);
      }
      final var candidatePool = generatedCandidates;
      var candidates = timed(item, "EXCERPT_SELECTION", () ->
          CandidatePruner.pruneV2(
                  candidatePool,
                  new CandidatePruner.Config(
                      generatorConfig.minWords(), generatorConfig.maxWords(), "cheap-v2"))
              .accepted());
      if (pilotLimit > 0 && candidates.size() > pilotLimit) {
        candidates = candidates.subList(0, pilotLimit);
      }
      final var selectedRun = run;
      final var selectedCandidates = candidates;
      final var selectedLanguage = language;
      updateProgress(item, "EXCERPTS", candidates.size(), candidates.size(),
          "Excerpts candidatos persistidos");
      var excerptIds = timed(item, "EXCERPT_PERSISTENCE", () -> {
        var ids = new java.util.ArrayList<UUID>();
        for (var candidate : selectedCandidates) {
          var id = persistExcerpt(selectedRun, item.inputVersion(), candidate, chapterById,
              TextAssetReader.codePointLength(text), selectedLanguage);
          if (id != null) ids.add(id);
        }
        return ids;
      });
      var excerptVectors = timed(item, "EXCERPT_EMBEDDINGS",
          () -> persistEmbeddings(item, excerptIds, selectedCandidates));
      updateProgress(item, "SCOPE_EMBEDDINGS", 0, 1, "Calculando embeddings de capítulo/documento");
      var scopeEmbeddings = timed(item, "SCOPE_EMBEDDINGS", () ->
          persistChapterAndDocumentEmbeddings(
              item.inputVersion(), text, chapters, generatorConfig.version()));
      timed(item, "FEATURES", () -> {
        persistPrototypeScores(selectedRun, excerptIds, selectedCandidates, excerptVectors, scopeEmbeddings, selectedLanguage);
        persistRanks(selectedRun, excerptIds);
        persistNli(selectedRun, excerptIds, selectedCandidates, excerptVectors, selectedLanguage);
        return null;
      });
      jdbc.update(
          "UPDATE analytics.analysis_run SET status='COMPLETED', finished_at=clock_timestamp()"
              + " WHERE id=?",
          run);
      metrics.excerptsGenerated(candidates.size());
    }
    jdbc.update(
        "UPDATE analytics.analysis_phase_timing SET analysis_run_id=?"
            + " WHERE job_item_id=? AND attempt_count=? AND analysis_run_id IS NULL",
        run, item.id(), item.attemptCount());
    jdbc.update(
        "UPDATE analytics.analysis_job_item SET analysis_run_id=? WHERE id=?", run, item.id());
  }

  private <T> T timed(Item item, String phase, PhaseOperation<T> operation) throws Exception {
    var timing = startPhase(item, phase);
    try {
      return metrics.timePhaseChecked(phase, operation::run);
    } catch (Exception exception) {
      timing.failed = true;
      throw exception;
    } finally {
      timing.close();
    }
  }

  private PhaseTiming startPhase(Item item, String phase) {
    var id = UUID.randomUUID();
    jdbc.update("INSERT INTO analytics.analysis_phase_timing"
        + "(id,job_id,job_item_id,attempt_count,phase,status) VALUES (?,?,?,?,?,'RUNNING')",
        id, item.jobId(), item.id(), item.attemptCount(), phase);
    writePhaseLog(id, item, "INFO", "Fase iniciada", null, null);
    return new PhaseTiming(id, phase, item);
  }

  @FunctionalInterface
  private interface PhaseOperation<T> { T run() throws Exception; }

  private final class PhaseTiming implements AutoCloseable {
    private final UUID id;
    private final String phase;
    private final Item item;
    private final long startedNanos = System.nanoTime();
    private boolean failed;

    private PhaseTiming(UUID id, String phase, Item item) {
      this.id = id;
      this.phase = phase;
      this.item = item;
    }

    @Override
    public void close() {
      var durationMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
      var status = failed ? "FAILED" : "COMPLETED";
      jdbc.update("UPDATE analytics.analysis_phase_timing SET status=?, finished_at=clock_timestamp(),"
          + " duration_ms=?, error_code=? WHERE id=?",
          status, durationMs, failed ? "PHASE_FAILED" : null, id);
      writePhaseLog(id, item, failed ? "ERROR" : "INFO",
          failed ? "Fase encerrada com falha" : "Fase concluída", null, null);
      if (failed) metrics.phaseFailed(phase); else metrics.phaseCompleted(phase);
    }
  }

  private void writePhaseLog(
      UUID phaseTimingId,
      Item item,
      String level,
      String message,
      Integer completed,
      Integer total) {
    jdbc.update(
        "INSERT INTO analytics.analysis_phase_log"
            + "(id,phase_timing_id,job_id,job_item_id,attempt_count,level,message,progress_completed,progress_total)"
            + " VALUES (?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID(), phaseTimingId, item.jobId(), item.id(), item.attemptCount(), level,
        message, completed, total);
  }

  private void persistRanks(UUID run, List<UUID> excerptIds) {
    var weights = heuristicRankerConfig.weights();
    String weightJson = weights.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> "\"" + entry.getKey() + "\":" + entry.getValue())
        .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    for (UUID excerptId : excerptIds) {
      var values =
          jdbc.query(
              """
              SELECT fd.code, ef.numeric_value, ef.value_status FROM analytics.excerpt_feature ef
              JOIN analytics.feature_definition fd ON fd.id=ef.feature_definition_id
              WHERE ef.analysis_run_id=? AND ef.excerpt_id=? AND ef.value_status='VALID'
              """,
              rs -> {
                var m = new java.util.LinkedHashMap<String, Double>();
                var statuses = new java.util.LinkedHashMap<String, String>();
                while (rs.next()) {
                  m.put(rs.getString(1), rs.getObject(2, Double.class));
                  statuses.put(rs.getString(1), rs.getString(3));
                }
                return new FeatureSnapshot(m, statuses);
              },
              run,
              excerptId);
      var ranked =
          com.bookrush.analytics.excerpts.ExcerptRanker.rank(
              values.features(),
              heuristicRankerConfig.asWeights());
      var supervised = supervisedScorer == null ? null : supervisedScorer.score(values.features());
      var dimensions = supervised == null
          ? com.bookrush.analytics.excerpts.CandidateDimensionSnapshot.from(values.features(), values.statuses())
          : supervised.dimensions();
      String rankerCode = supervised == null ? heuristicRankerConfig.code() : supervised.code();
      String rankerVersion = supervised == null ? ranked.version() : supervised.version();
      double finalScore = supervised == null ? ranked.score() : supervised.score();
      String components =
          "{"
              + ranked.contributions().entrySet().stream()
                  .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                  .collect(java.util.stream.Collectors.joining(","))
              + ",\"dimensions\":"
              + dimensionsJson(dimensions)
              + (supervised == null ? "" : ",\"artifact_sha256\":\"" + supervised.artifactSha256() + "\"")
              + "}";
      jdbc.update(
          """
INSERT INTO analytics.excerpt_rank(id,excerpt_id,ranker_code,ranker_version,configuration_hash,components,weights,final_score)
VALUES (?,?,?,?,?,?::jsonb,?::jsonb,?) ON CONFLICT DO NOTHING
""",
          UUID.randomUUID(),
          excerptId,
          rankerCode,
          rankerVersion,
          supervised == null ? sha256(weightJson) : supervised.artifactSha256(),
          components,
          weightJson,
          finalScore);
    }
  }

  private static String dimensionsJson(
      Map<String, com.bookrush.analytics.excerpts.CandidateDimensionSnapshot.Dimension> dimensions) {
    return dimensions.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            e -> {
              var d = e.getValue();
              String value = d.value() == null ? "null" : Double.toString(d.value());
              return "\""
                  + e.getKey()
                  + "\":{\"value\":"
                  + value
                  + ",\"status\":\""
                  + d.status()
                  + "\",\"source_feature\":\""
                  + d.sourceFeature()
                  + "\"}";
            })
        .collect(java.util.stream.Collectors.joining(",", "{", "}"));
  }

  private record FeatureSnapshot(
      Map<String, Double> features, Map<String, String> statuses) {}

  private void persistNli(
      UUID run,
      List<UUID> excerptIds,
      List<ExcerptCandidateGenerator.V2Candidate> candidates,
      Map<UUID, List<Double>> embeddings,
      String language) {
    if (!Boolean.parseBoolean(System.getenv().getOrDefault("ANALYTICS_NLI_ENABLED", "false"))
        || excerptIds.isEmpty()) return;
    int limit =
        Math.max(
            1, Integer.parseInt(System.getenv().getOrDefault("ANALYTICS_NLI_MAX_EXCERPTS", "50")));
    var selected =
        TopNliCandidateSelector.select(candidates, excerptIds, embeddings, limit).stream()
            .map(TopNliCandidateSelector.Selection::excerptId)
            .toList();
    var hypotheses = nliHypotheses(language);
    for (UUID excerptId : selected) {
      String text =
          jdbc.query(
              "SELECT text FROM analytics.excerpt WHERE id=?",
              rs -> rs.next() ? rs.getString(1) : null,
              excerptId);
      if (text == null) continue;
      for (var entry : hypotheses.entrySet()) {
        var result = linguisticRuntime.nli(text, entry.getValue());
        if (!"VALID".equals(result.status())) continue;
        var score = result.score();
        persistNliValue(
            run, excerptId, "narrative." + entry.getKey(), "entailment", score.entailment());
        persistNliValue(run, excerptId, "narrative." + entry.getKey(), "neutral", score.neutral());
        persistNliValue(
            run, excerptId, "narrative." + entry.getKey(), "contradiction", score.contradiction());
        persistNliValue(run, excerptId, "narrative." + entry.getKey(), "support", score.support());
        persistNliValue(
            run, excerptId, "narrative." + entry.getKey(), "confidence", score.confidence());
      }
      for (var entry : emotionHypotheses(language).entrySet()) {
        var result = linguisticRuntime.nli(text, entry.getValue());
        if (!"VALID".equals(result.status())) continue;
        var score = result.score();
        String prefix = "emotion." + entry.getKey();
        persistNliValue(run, excerptId, prefix, "entailment", score.entailment());
        persistNliValue(run, excerptId, prefix, "neutral", score.neutral());
        persistNliValue(run, excerptId, prefix, "contradiction", score.contradiction());
        persistNliValue(run, excerptId, prefix, "support", score.support());
        persistNliValue(run, excerptId, prefix, "confidence", score.confidence());
      }
    }
  }

  private void persistNliValue(
      UUID run, UUID excerptId, String prefix, String metric, double value) {
    jdbc.update(
        """
INSERT INTO analytics.excerpt_feature(analysis_run_id,feature_definition_id,excerpt_id,numeric_value,value_status,computed_at)
SELECT ?,fd.id,?,?, 'VALID',clock_timestamp() FROM analytics.feature_definition fd
 WHERE fd.code=? ON CONFLICT DO NOTHING
""",
        run,
        excerptId,
        value,
        prefix + "." + metric);
  }

  private void persistLanguageValidation(
      UUID run,
      UUID inputVersion,
      String catalogLanguage,
      LinguisticRuntimeClient.LanguageResult detected) {
    String status = switch (detected.status()) {
      case "VALID", "MODEL_UNAVAILABLE", "INVALID_INPUT", "INSUFFICIENT_SAMPLE", "ERROR" -> detected.status();
      default -> "UNSUPPORTED";
    };
    jdbc.update(
        """
INSERT INTO analytics.document_feature
  (analysis_run_id,feature_definition_id,input_asset_version_id,text_value,value_status,confidence,sample_size,warning,computed_at)
SELECT ?,fd.id,?,?,?, ?,NULL,?,clock_timestamp()
FROM analytics.feature_definition fd WHERE fd.code='language.detected'
ON CONFLICT (analysis_run_id,feature_definition_id)
DO UPDATE SET input_asset_version_id=EXCLUDED.input_asset_version_id,
  text_value=EXCLUDED.text_value,value_status=EXCLUDED.value_status,
  confidence=EXCLUDED.confidence,sample_size=EXCLUDED.sample_size,
  warning=EXCLUDED.warning,computed_at=EXCLUDED.computed_at
""",
        run, inputVersion, detected.language(), status,
        "VALID".equals(status) ? detected.confidence() : null, detected.warning());
    jdbc.update(
        """
INSERT INTO analytics.document_feature
  (analysis_run_id,feature_definition_id,input_asset_version_id,numeric_value,value_status,confidence,sample_size,warning,computed_at)
SELECT ?,fd.id,?,?,?, ?,NULL,?,clock_timestamp()
FROM analytics.feature_definition fd WHERE fd.code='language.confidence'
ON CONFLICT (analysis_run_id,feature_definition_id)
DO UPDATE SET input_asset_version_id=EXCLUDED.input_asset_version_id,
  numeric_value=EXCLUDED.numeric_value,value_status=EXCLUDED.value_status,
  confidence=EXCLUDED.confidence,sample_size=EXCLUDED.sample_size,
  warning=EXCLUDED.warning,computed_at=EXCLUDED.computed_at
""",
        run, inputVersion, detected.confidence(), status,
        "VALID".equals(status) ? detected.confidence() : null, detected.warning());
    if ("VALID".equals(status) && catalogLanguage != null && detected.language() != null) {
      jdbc.update(
          """
INSERT INTO analytics.document_feature
  (analysis_run_id,feature_definition_id,input_asset_version_id,numeric_value,value_status,confidence,computed_at)
SELECT ?,fd.id,?,?, 'VALID',1,clock_timestamp()
FROM analytics.feature_definition fd WHERE fd.code='language.agreement'
ON CONFLICT (analysis_run_id,feature_definition_id)
DO UPDATE SET input_asset_version_id=EXCLUDED.input_asset_version_id,
  numeric_value=EXCLUDED.numeric_value,value_status=EXCLUDED.value_status,
  confidence=EXCLUDED.confidence,computed_at=EXCLUDED.computed_at
""",
          run, inputVersion, catalogLanguage.equalsIgnoreCase(detected.language()) ? 1d : 0d);
    }
  }

  private static Map<String, String> nliHypotheses(String language) {
    boolean pt = "pt".equalsIgnoreCase(language);
    var values = new java.util.LinkedHashMap<String, String>();
    if (pt) {
      values.put(
          "conflict",
          "Este trecho contém um conflito ativo entre personagens, grupos, objetivos ou forças"
              + " opostas.");
      values.put(
          "suspense",
          "Este trecho cria incerteza sobre um resultado importante que ainda está por acontecer.");
      values.put(
          "introspection",
          "Este trecho se concentra nos pensamentos, emoções, autorreflexão ou conflito interno de"
              + " um personagem.");
      values.put(
          "self_containment",
          "Este trecho pode ser compreendido por si só sem precisar de informações importantes do"
              + " texto ao redor.");
      values.put(
          "curiosity_gap",
          "Este trecho deixa uma pergunta importante sem resposta ou uma informação relevante"
              + " faltando que incentiva a continuar lendo.");
      values.put(
          "cliffhanger",
          "O final deste trecho deixa deliberadamente um acontecimento ou resultado sem"
              + " resolução.");
      values.put(
          "revelation",
          "Este trecho revela uma informação importante que antes estava escondida, desconhecida ou"
              + " mal compreendida.");
      values.put(
          "resolution",
          "Este trecho resolve um conflito, uma incerteza ou uma questão narrativa importante.");
      values.put(
          "quotability",
          "Este trecho contém uma afirmação memorável que mantém sentido e pode ser compreendida"
              + " quando citada isoladamente.");
    } else {
      values.put(
          "conflict",
          "This passage contains an active conflict between characters, groups, goals, or opposing"
              + " forces.");
      values.put(
          "suspense", "This passage creates uncertainty about an important upcoming outcome.");
      values.put(
          "introspection",
          "This passage focuses on a character's thoughts, emotions, self-reflection, or inner"
              + " conflict.");
      values.put(
          "self_containment",
          "This passage can be understood on its own without needing important information from the"
              + " surrounding text.");
      values.put(
          "curiosity_gap",
          "This passage leaves an important unresolved question or missing piece of information"
              + " that encourages continued reading.");
      values.put(
          "cliffhanger",
          "The ending of this passage deliberately leaves an event or outcome unresolved.");
      values.put(
          "revelation",
          "This passage reveals important information that was previously hidden, unknown, or"
              + " misunderstood.");
      values.put(
          "resolution",
          "This passage resolves an important conflict, uncertainty, or narrative question.");
      values.put(
          "quotability",
          "This passage contains a memorable statement that is meaningful and understandable when"
              + " quoted on its own.");
    }
    return values;
  }

  private static Map<String, String> emotionHypotheses(String language) {
    boolean pt = "pt".equalsIgnoreCase(language);
    var values = new java.util.LinkedHashMap<String, String>();
    if (pt) {
      values.put(
          "joy", "Este trecho expressa ou retrata alegria, felicidade, prazer ou celebração.");
      values.put(
          "sadness", "Este trecho expressa ou retrata tristeza, pesar, luto ou perda emocional.");
      values.put("fear", "Este trecho expressa ou retrata medo, pavor, ansiedade ou ameaça.");
      values.put(
          "anger", "Este trecho expressa ou retrata raiva, fúria, ressentimento ou hostilidade.");
      values.put(
          "surprise",
          "Este trecho expressa ou retrata surpresa, choque ou um acontecimento inesperado.");
      values.put("disgust", "Este trecho expressa ou retrata nojo, repulsa ou forte aversão.");
      values.put(
          "affection",
          "Este trecho expressa ou retrata afeto, ternura, amor ou proximidade emocional.");
    } else {
      values.put(
          "joy", "This passage expresses or depicts joy, happiness, delight, or celebration.");
      values.put(
          "sadness",
          "This passage expresses or depicts sadness, sorrow, grief, or emotional loss.");
      values.put("fear", "This passage expresses or depicts fear, dread, anxiety, or threat.");
      values.put(
          "anger", "This passage expresses or depicts anger, rage, resentment, or hostility.");
      values.put(
          "surprise",
          "This passage expresses or depicts surprise, shock, or an unexpected development.");
      values.put(
          "disgust", "This passage expresses or depicts disgust, revulsion, or strong aversion.");
      values.put(
          "affection",
          "This passage expresses or depicts affection, tenderness, love, or emotional closeness.");
    }
    return values;
  }

  private List<ExcerptCandidateGenerator.ChapterBoundary> chapters(UUID inputVersion, int textLength) {
    return jdbc.query(
        """
        SELECT id, start_offset, end_offset
          FROM catalog.book_chapter
         WHERE text_asset_version_id=? AND parent_chapter_id IS NULL AND end_offset > start_offset
         ORDER BY start_offset, end_offset, id
        """,
        (rs, row) ->
            new ExcerptCandidateGenerator.ChapterBoundary(
                rs.getObject("id", UUID.class),
                Math.max(0, rs.getInt("start_offset")),
                Math.min(textLength, rs.getInt("end_offset"))),
        inputVersion).stream()
        .filter(chapter -> chapter.startCodepoint() < textLength)
        .filter(chapter -> chapter.endCodepoint() > chapter.startCodepoint())
        .toList();
  }

  private UUID persistExcerpt(
      UUID run,
      UUID version,
      ExcerptCandidateGenerator.V2Candidate candidate,
      Map<UUID, ExcerptCandidateGenerator.ChapterBoundary> chapters,
      int textLength,
      String language) {
    var chapter = candidate.chapterId() == null ? null : chapters.get(candidate.chapterId());
    int chapterStart = chapter == null ? candidate.startCodepoint() : chapter.startCodepoint();
    int chapterLength =
        chapter == null
            ? Math.max(1, textLength)
            : chapter.endCodepoint() - chapter.startCodepoint();
    var excerpt = UUID.randomUUID();
    var eligibility = com.bookrush.analytics.excerpts.StructuredContentEligibility.classify(candidate.text());
    jdbc.update(
        """
INSERT INTO analytics.excerpt(id,source_asset_version_id,chapter_id,start_codepoint,end_codepoint,text,text_sha256,word_count,sentence_count,generation_method,generator_version,body_eligible,exclusion_reason)
VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (source_asset_version_id,start_codepoint,end_codepoint,generator_version) DO NOTHING
""",
        excerpt,
        version,
        candidate.chapterId(),
        candidate.startCodepoint(),
        candidate.endCodepoint(),
        candidate.text(),
        candidate.textSha256(),
        candidate.wordCount(),
        candidate.sentenceCount(),
        "SENTENCE_WINDOW",
        candidate.generatorVersion(),
        eligibility.bodyEligible(),
        eligibility.exclusionReason());
    var actual =
        jdbc.query(
            "SELECT id FROM analytics.excerpt WHERE source_asset_version_id=? AND start_codepoint=?"
                + " AND end_codepoint=? AND generator_version=?",
            rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
            version,
            candidate.startCodepoint(),
            candidate.endCodepoint(),
            candidate.generatorVersion());
    if (actual == null) return null;
    var definitions =
        jdbc.query(
            "SELECT id, code FROM analytics.feature_definition WHERE scope='EXCERPT' AND"
                + " active=true",
            (rs, n) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("code")));
    var values =
        TextFeatureCalculator.calculate(
            candidate.text(), candidate.startCodepoint(), textLength, chapterStart, chapterLength);
    values = new java.util.LinkedHashMap<>(values);
    values.putAll(
        com.bookrush.analytics.features.LexicalDiversityCalculator.calculate(candidate.text())
            .values());
    var linguistic = linguisticRuntime.analyze(language, candidate.text());
    values.putAll(linguistic.values());
    var featureRows = new java.util.ArrayList<Object[]>();
    for (var definition : definitions) {
      var value = values.get(definition.getValue());
      if (value == null && !definition.getValue().equals("unique_lemma_ratio")) continue;
      featureRows.add(
          new Object[] {
            run, definition.getKey(), actual, value, value == null ? linguistic.status() : "VALID"
          });
    }
    if (!featureRows.isEmpty())
      jdbc.batchUpdate(
          "INSERT INTO"
              + " analytics.excerpt_feature(analysis_run_id,feature_definition_id,excerpt_id,numeric_value,value_status,computed_at)"
              + " VALUES (?,?,?,?, ?, clock_timestamp()) ON CONFLICT DO NOTHING",
          featureRows);
    return actual;
  }

  private Map<UUID, List<Double>> persistEmbeddings(
      Item item, List<UUID> excerptIds, List<ExcerptCandidateGenerator.V2Candidate> candidates) {
    UUID version = item.inputVersion();
    var vectorsByExcerpt = new java.util.LinkedHashMap<UUID, List<Double>>();
    if (excerptIds.isEmpty() || excerptIds.size() != candidates.size()) return vectorsByExcerpt;
    // The runtime deliberately bounds requests to 32 texts. Chunking here also
    // prevents a large book from creating an oversized HTTP body and preserves
    // per-excerpt idempotency through the input hash/object key.
    // CPU-only BGE inference is deliberately kept small; larger batches can
    // exceed the runtime read timeout even though the request is valid.
    final int batchSize = 4;
    String bucket = System.getenv().getOrDefault("STORAGE_BUCKET_ML", "books-ml");
    int total = candidates.size();
    for (int offset = 0; offset < candidates.size(); offset += batchSize) {
      int end = Math.min(offset + batchSize, candidates.size());
      var texts =
          candidates.subList(offset, end).stream()
              .map(ExcerptCandidateGenerator.V2Candidate::text)
              .toList();
      updateProgress(item, "EMBEDDINGS", offset, total,
          "Solicitando lote " + (offset + 1) + "-" + end + " de " + total);
      long startedNanos = System.nanoTime();
      var result = linguisticRuntime.embeddings(texts, 8192);
      long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;
      log.info("analytics embeddings progress itemId={} version={} batch={}-{} total={} durationMs={} status={} vectors={}",
          item.id(), version, offset + 1, end, total, durationMs, result.status(), result.vectors().size());
      if (!"VALID".equals(result.status())
          || result.vectors().size() != texts.size()
          || result.inputHashes().size() != texts.size()) {
        updateProgress(item, "EMBEDDINGS", offset, total,
            "Runtime não retornou um lote válido: " + result.status());
        continue;
      }
      var modelId =
          jdbc.query(
              "SELECT id FROM analytics.embedding_model WHERE code=?",
              rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
              result.model());
      if (modelId == null) continue;
      for (int i = 0; i < texts.size(); i++) {
        var hash = result.inputHashes().get(i);
        String key = "analytics/embeddings/" + version + "/" + hash + ".json";
        String payload =
            "{\"model\":\""
                + result.model()
                + "\",\"dimension\":"
                + result.dimension()
                + ",\"input_sha256\":\""
                + hash
                + "\",\"vector\":"
                + result.vectors().get(i)
                + "}";
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/json")
                .build(),
            RequestBody.fromString(payload));
        jdbc.update(
            "INSERT INTO"
                + " analytics.excerpt_embedding(id,excerpt_id,embedding_model_id,input_sha256,dimension,storage_provider,bucket,object_key)"
                + " VALUES (?,?,?,?,?,'S3',?,?) ON CONFLICT DO NOTHING",
            UUID.randomUUID(),
            excerptIds.get(offset + i),
            modelId,
            hash,
            result.dimension(),
            bucket,
            key);
        vectorsByExcerpt.put(excerptIds.get(offset + i), result.vectors().get(i));
      }
      updateProgress(item, "EMBEDDINGS", end, total,
          "Lote persistido " + end + "/" + total + " (" + durationMs + " ms)");
    }
    return vectorsByExcerpt;
  }

  private void updateProgress(Item item, String stage, int completed, int total, String message) {
    jdbc.update(
        "UPDATE analytics.analysis_job_item SET progress_stage=?, progress_completed=?, progress_total=?,"
            + " progress_message=?, progress_updated_at=clock_timestamp() WHERE id=?",
        stage, Math.max(0, completed), Math.max(0, total), message, item.id());
    jdbc.update(
        "INSERT INTO analytics.analysis_phase_log"
            + "(id,phase_timing_id,job_id,job_item_id,attempt_count,level,message,progress_completed,progress_total)"
            + " SELECT ?,p.id,p.job_id,p.job_item_id,p.attempt_count,'INFO',?,?,?"
            + " FROM analytics.analysis_phase_timing p"
            + " WHERE p.job_item_id=? AND p.attempt_count=? AND p.status='RUNNING'"
            + " ORDER BY p.started_at DESC, p.id DESC LIMIT 1",
        UUID.randomUUID(), message, Math.max(0, completed), Math.max(0, total), item.id(), item.attemptCount());
  }

  private void persistPrototypeScores(
      UUID run,
      List<UUID> excerptIds,
      List<ExcerptCandidateGenerator.V2Candidate> candidates,
      Map<UUID, List<Double>> vectors,
      ScopeEmbeddings scopeEmbeddings,
      String language) {
    if (vectors.isEmpty()
        && scopeEmbeddings.chapterVectors().isEmpty()
        && scopeEmbeddings.documentVector() == null) return;
    var examples = new java.util.LinkedHashMap<String, String>();
    boolean pt = "pt".equalsIgnoreCase(language);
    if (pt) {
      examples.put(
          "action", "O trecho é conduzido por ação física, movimento ou acontecimento externo.");
      examples.put("dialogue", "Personagens se comunicam diretamente em diálogo falado.");
      examples.put(
          "description", "O trecho descreve cenário, objetos, pessoas ou detalhes sensoriais.");
      examples.put(
          "reflection",
          "O trecho se concentra em pensamentos, motivos, memórias ou reflexão interior.");
      examples.put("conflict", "Objetivos ou forças opostas estão em tensão ativa.");
      examples.put("mystery", "O trecho retém informação e convida à investigação.");
    } else {
      examples.put(
          "action", "The passage is driven by physical action, movement, or an external event.");
      examples.put("dialogue", "Characters communicate directly in spoken dialogue.");
      examples.put(
          "description", "The passage describes setting, objects, people, or sensory details.");
      examples.put(
          "reflection", "The passage focuses on thoughts, motives, memories, or inner reflection.");
      examples.put("conflict", "Opposing goals or forces are in active tension.");
      examples.put("mystery", "The passage withholds information and invites investigation.");
    }
    var prototypeTexts = new java.util.ArrayList<>(examples.values());
    var result = linguisticRuntime.embeddings(prototypeTexts, 8192);
    if (!"VALID".equals(result.status()) || result.vectors().size() != prototypeTexts.size())
      return;
    for (var entry : vectors.entrySet()) {
      for (int i = 0; i < prototypeTexts.size(); i++) {
        double score = cosine(entry.getValue(), result.vectors().get(i));
        jdbc.update(
            """
INSERT INTO analytics.excerpt_feature(analysis_run_id,feature_definition_id,excerpt_id,numeric_value,value_status,computed_at)
SELECT ?,fd.id,?,?, 'VALID',clock_timestamp() FROM analytics.feature_definition fd
 WHERE fd.code=? ON CONFLICT DO NOTHING
""",
            run,
            entry.getKey(),
            score,
            "semantic.prototype." + examples.keySet().toArray()[i] + ".raw_cosine");
      }
    }
    for (var entry : scopeEmbeddings.chapterVectors().entrySet()) {
      persistScopePrototypeScores(run, "chapter", entry.getKey(), entry.getValue(), result.vectors(), examples.keySet());
    }
    if (scopeEmbeddings.documentVector() != null) {
      persistScopePrototypeScores(run, "document", null, scopeEmbeddings.documentVector(), result.vectors(), examples.keySet());
    }
  }

  private void persistScopePrototypeScores(
      UUID run,
      String scope,
      UUID scopeId,
      List<Double> vector,
      List<List<Double>> prototypes,
      java.util.Set<String> concepts) {
    int index = 0;
    for (String concept : concepts) {
      double score = cosine(vector, prototypes.get(index++));
      String code = "semantic.prototype." + concept + ".raw_cosine." + scope;
      if ("chapter".equals(scope)) {
        jdbc.update(
            """
INSERT INTO analytics.chapter_feature(analysis_run_id,feature_definition_id,chapter_id,numeric_value,value_status,computed_at)
SELECT ?,fd.id,?,?, 'VALID',clock_timestamp() FROM analytics.feature_definition fd
 WHERE fd.code=? ON CONFLICT DO NOTHING
""",
            run, scopeId, score, code);
      } else {
        jdbc.update(documentFeatureInsertSql(), run, score, run, code);
      }
    }
  }

  static String documentFeatureInsertSql() {
    return """
INSERT INTO analytics.document_feature(analysis_run_id,feature_definition_id,input_asset_version_id,numeric_value,value_status,computed_at)
SELECT ?,fd.id,ar.input_asset_version_id,?, 'VALID',clock_timestamp()
 FROM analytics.feature_definition fd JOIN analytics.analysis_run ar ON ar.id=?
 WHERE fd.code=? ON CONFLICT DO NOTHING
""";
  }

  private static double cosine(List<Double> left, List<Double> right) {
    return EmbeddingMath.cosine(left, right);
  }

  /**
   * Creates lineage-preserving chapter and document vectors. Long text is split into deterministic
   * code-point chunks and aggregated with a weighted mean, so a whole book is never silently
   * truncated to a model context.
   */
  private ScopeEmbeddings persistChapterAndDocumentEmbeddings(
      UUID version,
      String text,
      List<ExcerptCandidateGenerator.ChapterBoundary> chapters,
      String generatorVersion) {
    if (!Boolean.parseBoolean(
        System.getenv().getOrDefault("ANALYTICS_SCOPE_EMBEDDINGS_ENABLED", "true")))
      return ScopeEmbeddings.empty();
    String bucket = System.getenv().getOrDefault("STORAGE_BUCKET_ML", "books-ml");
    UUID modelId =
        jdbc.query(
            "SELECT id FROM analytics.embedding_model WHERE code='bge-m3-dense-v1'",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
    if (modelId == null) return ScopeEmbeddings.empty();
    var chapterVectors = new java.util.ArrayList<WeightedVector>();
    var chapterEmbeddings = new java.util.LinkedHashMap<UUID, List<Double>>();
    for (var chapter : chapters) {
      if (chapter.endCodepoint() <= chapter.startCodepoint()) continue;
      String chapterText = codePointSlice(text, chapter.startCodepoint(), chapter.endCodepoint());
      var embedded = embedWholeText(chapterText);
      if (embedded == null) continue;
      String hash = sha256(chapterText);
      String key = "analytics/embeddings/chapters/" + chapter.chapterId() + "/" + hash + ".json";
      putVector(bucket, key, embedded.vector(), hash);
      jdbc.update(
          """
INSERT INTO analytics.chapter_embedding(id,chapter_id,embedding_model_id,input_sha256,dimension,pooling_strategy,storage_provider,bucket,object_key)
VALUES (?,?,?,?,?,'weighted-codepoint-chunks','S3',?,?) ON CONFLICT DO NOTHING
""",
          UUID.randomUUID(),
          chapter.chapterId(),
          modelId,
          hash,
          embedded.vector().size(),
          bucket,
          key);
      chapterVectors.add(
          new WeightedVector(embedded.vector(), Math.max(1, codePointLength(chapterText))));
      chapterEmbeddings.put(chapter.chapterId(), embedded.vector());
    }
    List<Double> document;
    if (chapterVectors.isEmpty()) {
      // Some normalized assets do not have catalogued chapters yet. The
      // document still requires a complete embedding, so aggregate chunks of
      // the whole normalized text instead of silently omitting it.
      var embedded = embedWholeText(text);
      if (embedded == null) return new ScopeEmbeddings(chapterEmbeddings, null);
      document = embedded.vector();
    } else {
      document = weightedMean(chapterVectors);
    }
    String documentHash = sha256(text);
    String key = "analytics/embeddings/documents/" + version + "/" + documentHash + ".json";
    putVector(bucket, key, document, documentHash);
    jdbc.update(
        """
INSERT INTO analytics.document_embedding(id,input_asset_version_id,embedding_model_id,input_sha256,dimension,storage_provider,bucket,object_key)
VALUES (?,?,?,?,?,'S3',?,?) ON CONFLICT DO NOTHING
""",
        UUID.randomUUID(),
        version,
        modelId,
        documentHash,
        document.size(),
        bucket,
        key);
    return new ScopeEmbeddings(chapterEmbeddings, document);
  }

  private record ScopeEmbeddings(
      Map<UUID, List<Double>> chapterVectors, List<Double> documentVector) {
    private static ScopeEmbeddings empty() {
      return new ScopeEmbeddings(Map.of(), null);
    }
  }

  private EmbeddedVector embedWholeText(String value) {
    int length = codePointLength(value);
    if (length == 0) return null;
    final int chunkSize = 6000;
    var chunks = new java.util.ArrayList<String>();
    for (int start = 0; start < length; start += chunkSize) {
      chunks.add(codePointSlice(value, start, Math.min(length, start + chunkSize)));
    }
    var vectors = new java.util.ArrayList<WeightedVector>();
    for (int offset = 0; offset < chunks.size(); offset += 4) {
      var batch = chunks.subList(offset, Math.min(offset + 4, chunks.size()));
      var result = linguisticRuntime.embeddings(batch, 8192);
      if (!"VALID".equals(result.status()) || result.vectors().size() != batch.size()) return null;
      for (int i = 0; i < batch.size(); i++)
        vectors.add(
            new WeightedVector(
                result.vectors().get(i), Math.max(1, codePointLength(batch.get(i)))));
    }
    return new EmbeddedVector(weightedMean(vectors));
  }

  private void putVector(String bucket, String key, List<Double> vector, String hash) {
    String payload =
        "{\"model\":\"bge-m3-dense-v1\",\"dimension\":"
            + vector.size()
            + ",\"input_sha256\":\""
            + hash
            + "\",\"vector\":"
            + vector
            + "}";
    s3.putObject(
        PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/json").build(),
        RequestBody.fromString(payload));
  }

  private static List<Double> weightedMean(List<WeightedVector> vectors) {
    int dimension = vectors.get(0).vector().size();
    double[] sum = new double[dimension];
    double total = 0;
    for (var item : vectors) {
      if (item.vector().size() != dimension)
        throw new IllegalStateException("EMBEDDING_DIMENSION_MISMATCH");
      total += item.weight();
      for (int i = 0; i < dimension; i++) sum[i] += item.vector().get(i) * item.weight();
    }
    double norm = 0;
    for (int i = 0; i < dimension; i++) {
      sum[i] /= total;
      norm += sum[i] * sum[i];
    }
    norm = Math.sqrt(norm);
    var result = new java.util.ArrayList<Double>(dimension);
    for (double value : sum) result.add(norm == 0 ? 0d : value / norm);
    return result;
  }

  private static String codePointSlice(String value, int start, int end) {
    int length = codePointLength(value);
    int safeStart = Math.max(0, Math.min(start, length));
    int safeEnd = Math.max(safeStart, Math.min(end, length));
    int begin = value.offsetByCodePoints(0, safeStart);
    int finish = value.offsetByCodePoints(begin, safeEnd - safeStart);
    return value.substring(begin, finish);
  }

  private static int codePointLength(String value) {
    return value.codePointCount(0, value.length());
  }

  private record WeightedVector(List<Double> vector, int weight) {}

  private record EmbeddedVector(List<Double> vector) {}

  private String languageFor(UUID assetVersionId) {
    // Language is metadata of the canonical asset. No language evidence means
    // Tier 1 remains UNSUPPORTED rather than guessing from text.
    return jdbc.query(
        """
        SELECT COALESCE(e.language, b.original_language, 'und')
          FROM catalog.book_asset_version v
          JOIN catalog.book_asset a ON a.id=v.book_asset_id
          JOIN catalog.book b ON b.id=a.book_id
          LEFT JOIN catalog.edition e ON e.id=a.edition_id
         WHERE v.id=?
        """,
        rs -> rs.next() ? rs.getString(1) : "und",
        assetVersionId);
  }

  private void complete(Item item) {
    jdbc.update(
        "UPDATE analytics.analysis_job_item SET status='COMPLETED', finished_at=clock_timestamp(),"
            + " progress_stage='COMPLETED', progress_completed=1, progress_total=1,"
            + " progress_message='Item concluído', progress_updated_at=clock_timestamp(),"
            + " error_code=NULL, error_message=NULL WHERE id=?",
        item.id());
    reconcileJob(item.jobId());
  }

  private void reconcileJob(UUID jobId) {
    jdbc.update(
        """
UPDATE analytics.analysis_job j SET processed_items=(SELECT count(*) FROM analytics.analysis_job_item i WHERE i.job_id=j.id AND i.status IN ('COMPLETED','FAILED','CANCELLED')),
    succeeded_items=(SELECT count(*) FROM analytics.analysis_job_item i WHERE i.job_id=j.id AND i.status='COMPLETED'),
    failed_items=(SELECT count(*) FROM analytics.analysis_job_item i WHERE i.job_id=j.id AND i.status='FAILED'),
    status=CASE WHEN EXISTS(SELECT 1 FROM analytics.analysis_job_item i WHERE i.job_id=j.id AND i.status='PENDING') THEN 'RUNNING'
               WHEN EXISTS(SELECT 1 FROM analytics.analysis_job_item i WHERE i.job_id=j.id AND i.status='FAILED') THEN 'COMPLETED_WITH_ERRORS' ELSE 'COMPLETED' END,
    started_at=COALESCE(started_at,clock_timestamp()), finished_at=CASE WHEN NOT EXISTS(SELECT 1 FROM analytics.analysis_job_item i WHERE i.job_id=j.id AND i.status IN ('PENDING','RUNNING')) THEN clock_timestamp() ELSE NULL END
 WHERE j.id=?
""",
        jobId);
  }

  private void fail(Item item, String code, String message, Exception exception) {
    String safeMessage = message == null ? code : message.substring(0, Math.min(1000, message.length()));
    if (isRetryable(code, exception)) {
      jdbc.update(
          "UPDATE analytics.analysis_job_item SET status='PENDING', finished_at=clock_timestamp(),"
              + " progress_stage='RETRYING', progress_message=?, progress_updated_at=clock_timestamp(),"
              + " error_code=?, error_message=? WHERE id=?",
          safeMessage,
          code,
          safeMessage,
          item.id());
      jdbc.update(
          "UPDATE analytics.analysis_job SET status='RUNNING', finished_at=NULL WHERE id=?"
              + " AND status IN ('PENDING','RUNNING')",
          item.jobId());
      return;
    }
    jdbc.update(
        "UPDATE analytics.analysis_job_item SET status='FAILED', finished_at=clock_timestamp(),"
            + " progress_stage='FAILED', progress_message=?, progress_updated_at=clock_timestamp(),"
            + " error_code=?, error_message=? WHERE id=?",
        safeMessage,
        code,
        safeMessage,
        item.id());
    // A failed item is terminal only for that item. The batch must remain
    // RUNNING while other items are pending or being processed, so a restart
    // or one bad book cannot strand the rest of the queue.
    reconcileJob(item.jobId());
    metrics.jobItemFailed(code);
  }

  static boolean isRetryable(String code, Exception exception) {
    if (code == null || code.equals("ASSET_VERSION_UNAVAILABLE")
        || exception instanceof TextAssetReader.IntegrityException
        || exception instanceof IllegalArgumentException) return false;
    return exception instanceof IOException
        || exception instanceof DataAccessException
        || code.contains("Timeout")
        || code.contains("Connect")
        || code.contains("Unavailable")
        || code.contains("S3Exception")
        || code.contains("ResourceAccess");
  }

  private static String configurationJson(ExcerptCandidateGenerator.V2Config config) {
    String profiles =
        config.windowProfiles().stream()
            .map(profile -> "[" + profile.targetWords() + "," + profile.sentenceStride() + "]")
            .collect(java.util.stream.Collectors.joining(","));
    return "{\"generatorVersion\":\""
        + config.version()
        + "\",\"minWords\":"
        + config.minWords()
        + ",\"windowProfiles\":["
        + profiles
        + "],\"maxWords\":"
        + config.maxWords()
        + ",\"crossChapter\":"
        + config.crossChapter()
        + "}";
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private static String errorCode(Exception e) {
    return e instanceof TextAssetReader.IntegrityException
        ? e.getMessage()
        : e.getClass().getSimpleName();
  }

  private record Item(UUID id, UUID jobId, UUID inputVersion, int attemptCount) {}

  private record Physical(String bucket, String key, long size, String sha256) {}
}
