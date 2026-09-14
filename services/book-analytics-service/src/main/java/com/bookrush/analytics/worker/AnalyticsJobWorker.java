package com.bookrush.analytics.worker;

import com.bookrush.analytics.excerpts.CandidatePruner;
import com.bookrush.analytics.excerpts.ExcerptCandidateGenerator;
import com.bookrush.analytics.features.TextFeatureCalculator;
import com.bookrush.analytics.observability.AnalyticsMetrics;
import com.bookrush.analytics.storage.TextAssetReader;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

/**
 * Persistent, bounded worker for the deterministic analytics baseline.
 * Claiming and completion are short database transactions; object storage I/O
 * happens outside them. Providers that need models remain optional adapters.
 */
@Component
@ConditionalOnProperty(name = "analytics.worker.enabled", havingValue = "true")
public final class AnalyticsJobWorker {
  private static final String GENERATOR_VERSION = "sentence-window-v1";
  private final JdbcTemplate jdbc;
  private final S3Client s3;
  private final AnalyticsMetrics metrics;

  public AnalyticsJobWorker(JdbcTemplate jdbc, S3Client s3, AnalyticsMetrics metrics) {
    this.jdbc = jdbc;
    this.s3 = s3;
    this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "${analytics.worker.poll-delay-ms:1000}")
  public void poll() {
    var item = claimOne();
    if (item == null) return;
    try {
      process(item);
      complete(item);
    } catch (Exception e) {
      fail(item, errorCode(e), e.getMessage());
    }
  }

  private Item claimOne() {
    return jdbc.query("""
        WITH next_item AS (
          SELECT i.id FROM analytics.analysis_job_item i
          JOIN analytics.analysis_job j ON j.id=i.job_id
          WHERE i.status='PENDING' AND j.cancel_requested=false AND j.status IN ('PENDING','RUNNING')
          ORDER BY i.created_at, i.id FOR UPDATE SKIP LOCKED LIMIT 1
        )
        UPDATE analytics.analysis_job_item i
           SET status='RUNNING', attempt_count=i.attempt_count+1, started_at=clock_timestamp(), finished_at=NULL
          FROM next_item n WHERE i.id=n.id
        RETURNING i.id, i.job_id, i.input_asset_version_id
        """, rs -> rs.next() ? new Item(rs.getObject("id", UUID.class), rs.getObject("job_id", UUID.class), rs.getObject("input_asset_version_id", UUID.class)) : null);
  }

  private void process(Item item) throws IOException {
    var physical = jdbc.query("""
        SELECT v.bucket, v.object_key, v.size_bytes, v.sha256
          FROM catalog.book_asset_version v
         WHERE v.id=? AND v.status='AVAILABLE' AND v.availability_status='AVAILABLE'
        """, rs -> rs.next() ? new Physical(rs.getString("bucket"), rs.getString("object_key"), rs.getLong("size_bytes"), rs.getString("sha256")) : null, item.inputVersion());
    if (physical == null) throw new IllegalStateException("ASSET_VERSION_UNAVAILABLE");
    String text;
    try (var stream = s3.getObject(GetObjectRequest.builder().bucket(physical.bucket()).key(physical.key()).build())) {
      text = TextAssetReader.readNormalized(stream, physical.size(), physical.sha256());
    }
    var analyzer = jdbc.queryForObject("SELECT id FROM analytics.analyzer WHERE code='deterministic-text-v1'", UUID.class);
    var run = jdbc.query("SELECT id FROM analytics.analysis_run WHERE input_asset_version_id=? AND analyzer_id=? AND status='COMPLETED' AND configuration->>'generatorVersion'=? ORDER BY created_at DESC LIMIT 1",
        rs -> rs.next() ? rs.getObject("id", UUID.class) : null, item.inputVersion(), analyzer, GENERATOR_VERSION);
    if (run == null) {
      run = UUID.randomUUID();
      jdbc.update("INSERT INTO analytics.analysis_run(id,analyzer_id,input_asset_version_id,status,configuration,started_at) VALUES (?,?,?,'RUNNING',?::jsonb,clock_timestamp())",
          run, analyzer, item.inputVersion(), "{\"generatorVersion\":\"" + GENERATOR_VERSION + "\"}");
      var candidates = CandidatePruner.prune(ExcerptCandidateGenerator.generate(text, 80, 1, GENERATOR_VERSION), new CandidatePruner.Config(8, 160, "cheap-v1")).accepted();
      for (var candidate : candidates) persistExcerpt(run, item.inputVersion(), candidate, TextAssetReader.codePointLength(text));
      jdbc.update("UPDATE analytics.analysis_run SET status='COMPLETED', finished_at=clock_timestamp() WHERE id=?", run);
      metrics.excerptsGenerated(candidates.size());
    }
    jdbc.update("UPDATE analytics.analysis_job_item SET analysis_run_id=? WHERE id=?", run, item.id());
  }

  private void persistExcerpt(UUID run, UUID version, ExcerptCandidateGenerator.Candidate candidate, int textLength) {
    var excerpt = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO analytics.excerpt(id,source_asset_version_id,start_codepoint,end_codepoint,text,text_sha256,word_count,sentence_count,generation_method,generator_version)
        VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT (source_asset_version_id,start_codepoint,end_codepoint,generator_version) DO NOTHING
        """, excerpt, version, candidate.startCodepoint(), candidate.endCodepoint(), candidate.text(), candidate.textSha256(), candidate.wordCount(), candidate.sentenceCount(), "SENTENCE_WINDOW", candidate.generatorVersion());
    var actual = jdbc.query("SELECT id FROM analytics.excerpt WHERE source_asset_version_id=? AND start_codepoint=? AND end_codepoint=? AND generator_version=?", rs -> rs.next() ? rs.getObject("id", UUID.class) : null, version, candidate.startCodepoint(), candidate.endCodepoint(), candidate.generatorVersion());
    if (actual == null) return;
    var definitions = jdbc.query("SELECT id, code FROM analytics.feature_definition WHERE scope='EXCERPT' AND active=true", (rs, n) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("code")));
    var values = TextFeatureCalculator.calculate(candidate.text(), candidate.startCodepoint(), textLength, candidate.startCodepoint(), Math.max(1, textLength));
    for (var definition : definitions) {
      var value = values.get(definition.getValue());
      if (value == null) continue;
      jdbc.update("INSERT INTO analytics.excerpt_feature(analysis_run_id,feature_definition_id,excerpt_id,numeric_value,computed_at) VALUES (?,?,?,?,clock_timestamp()) ON CONFLICT DO NOTHING", run, definition.getKey(), actual, value);
    }
  }

  private void complete(Item item) {
    jdbc.update("UPDATE analytics.analysis_job_item SET status='COMPLETED', finished_at=clock_timestamp(), error_code=NULL, error_message=NULL WHERE id=?", item.id());
    jdbc.update("""
      UPDATE analytics.analysis_job j SET processed_items=(SELECT count(*) FROM analytics.analysis_job_item i WHERE i.job_id=j.id AND i.status IN ('COMPLETED','FAILED','CANCELLED')),
          succeeded_items=(SELECT count(*) FROM analytics.analysis_job_item i WHERE i.job_id=j.id AND i.status='COMPLETED'),
          failed_items=(SELECT count(*) FROM analytics.analysis_job_item i WHERE i.job_id=j.id AND i.status='FAILED'),
          status=CASE WHEN EXISTS(SELECT 1 FROM analytics.analysis_job_item i WHERE i.job_id=j.id AND i.status='PENDING') THEN 'RUNNING'
                     WHEN EXISTS(SELECT 1 FROM analytics.analysis_job_item i WHERE i.job_id=j.id AND i.status='FAILED') THEN 'COMPLETED_WITH_ERRORS' ELSE 'COMPLETED' END,
          started_at=COALESCE(started_at,clock_timestamp()), finished_at=CASE WHEN NOT EXISTS(SELECT 1 FROM analytics.analysis_job_item i WHERE i.job_id=j.id AND i.status IN ('PENDING','RUNNING')) THEN clock_timestamp() ELSE NULL END
       WHERE j.id=?
      """, item.jobId());
  }

  private void fail(Item item, String code, String message) {
    jdbc.update("UPDATE analytics.analysis_job_item SET status='FAILED', finished_at=clock_timestamp(), error_code=?, error_message=? WHERE id=?", code, message == null ? code : message.substring(0, Math.min(1000, message.length())), item.id());
    jdbc.update("UPDATE analytics.analysis_job SET processed_items=(SELECT count(*) FROM analytics.analysis_job_item WHERE job_id=? AND status IN ('COMPLETED','FAILED','CANCELLED')), failed_items=(SELECT count(*) FROM analytics.analysis_job_item WHERE job_id=? AND status='FAILED'), status='COMPLETED_WITH_ERRORS', finished_at=clock_timestamp() WHERE id=?", item.jobId(), item.jobId(), item.jobId());
    metrics.jobItemFailed(code);
  }

  private static String errorCode(Exception e) { return e instanceof TextAssetReader.IntegrityException ? e.getMessage() : e.getClass().getSimpleName(); }
  private record Item(UUID id, UUID jobId, UUID inputVersion) {}
  private record Physical(String bucket, String key, long size, String sha256) {}
}
