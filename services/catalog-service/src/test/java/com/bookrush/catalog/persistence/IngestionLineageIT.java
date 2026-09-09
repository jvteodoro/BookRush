package com.bookrush.catalog.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Savepoint;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Sql("/catalog-fixture.sql")
class IngestionLineageIT {
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;
  private static final String JOB = "30000000-0000-4000-8000-000000000006";
  private static final String ITEM = "30000000-0000-4000-8000-000000000007";

  private void rejected(String sql, Object... args) throws Exception {
    Connection connection = DataSourceUtils.getConnection(dataSource);
    Savepoint savepoint = connection.setSavepoint();
    try {
      assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, args));
    } finally {
      connection.rollback(savepoint);
      connection.releaseSavepoint(savepoint);
    }
  }

  private List<String> queries() throws Exception {
    Path path = Path.of("documented-queries.sql");
    if (!Files.exists(path)) path = Path.of("../../docs/database/queries.sql");
    return java.util.Arrays.stream(Files.readString(path).replaceAll("(?m)--[^\n]*", "").split(";"))
        .map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  @Test void executesEveryDocumentedQueryAgainstFixture() throws Exception {
    int[] counts = {1,4,1,2,4,1,3,1};
    var queries = queries();
    assertEquals(counts.length, queries.size());
    for (int n=0; n<counts.length; n++) {
      assertEquals(counts[n], jdbc.queryForList(queries.get(n)).size(), "Query " + (n+1));
      assertFalse(jdbc.queryForList("EXPLAIN " + queries.get(n)).isEmpty());
    }
    assertEquals("50000000-0000-4000-8000-000000000002",
        jdbc.queryForList(queries.get(2)).getFirst().get("id").toString());
  }

  @Test void retryPreservesFailedAttemptAndAllowsUnresolvedWork() throws Exception {
    jdbc.update("""
        INSERT INTO catalog.ingestion_item(id,ingestion_job_id,source_id,external_identifier,attempt_number)
        VALUES (? ,?::uuid,'10000000-0000-4000-8000-000000000001','11',2)
        """, UUID.randomUUID(), JOB);
    assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM catalog.ingestion_item WHERE ingestion_job_id=?::uuid AND external_identifier='11'", Integer.class, JOB));
    rejected("UPDATE catalog.ingestion_item SET attempt_number=1 WHERE ingestion_job_id=?::uuid AND external_identifier='11' AND attempt_number=2", JOB);
    rejected("UPDATE catalog.ingestion_job SET items_processed=3 WHERE id=?::uuid", JOB);
    rejected("UPDATE catalog.ingestion_job SET items_failed=-1 WHERE id=?::uuid", JOB);
    rejected("UPDATE catalog.ingestion_item SET finished_at=now() WHERE id=?::uuid", ITEM);
    rejected("UPDATE catalog.ingestion_job SET started_at=now(),finished_at=now()-interval '1 hour' WHERE id=?::uuid", JOB);
  }

  @Test void provenanceRejectsWrongSourceIdentifierAndEdition() throws Exception {
    rejected("UPDATE catalog.ingestion_item SET source_id='10000000-0000-4000-8000-000000000002' WHERE id=?::uuid", ITEM);
    rejected("UPDATE catalog.ingestion_item SET external_identifier='other' WHERE id=?::uuid", ITEM);
    rejected("UPDATE catalog.ingestion_item SET book_id=NULL WHERE id=?::uuid", ITEM);
    rejected("UPDATE catalog.ingestion_item SET edition_id=? WHERE id=?::uuid", UUID.randomUUID(), ITEM);
    rejected("UPDATE catalog.book_asset_version SET ingestion_item_id=? WHERE id='50000000-0000-4000-8000-000000000001'", UUID.randomUUID());
    rejected("DELETE FROM catalog.source_record WHERE id='30000000-0000-4000-8000-000000000005'");
    rejected("UPDATE catalog.source_record SET raw_metadata='[]'");
    rejected("UPDATE catalog.source_record SET content_hash='INVALID'");
  }

  @Test void sourceRecordsAllowRevisionsButRejectIdenticalRevision() throws Exception {
    String insert = """
        INSERT INTO catalog.source_record(id,source_id,external_id,raw_metadata,retrieved_at,content_hash)
        VALUES (?,'10000000-0000-4000-8000-000000000001','1342','{}',now(),?)
        """;
    rejected(insert, UUID.randomUUID(), "a".repeat(64));
    jdbc.update(insert, UUID.randomUUID(), "e".repeat(64));
    assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM catalog.source_record WHERE external_id='1342'", Integer.class));
  }

  @Test void failedProcessingMayLackOutputButSuccessCannot() throws Exception {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO catalog.asset_processing(id,input_asset_version_id,processing_type,processor,processor_version,status)
        VALUES (?,'50000000-0000-4000-8000-000000000001','NORMALIZATION','test','1','FAILED')
        """, id);
    rejected("UPDATE catalog.asset_processing SET status='SUCCEEDED' WHERE id=?", id);
    rejected("UPDATE catalog.asset_processing SET output_asset_version_id=input_asset_version_id WHERE id=?", id);
    rejected("UPDATE catalog.asset_processing SET input_asset_version_id=? WHERE id=?", UUID.randomUUID(), id);
    rejected("UPDATE catalog.asset_processing SET started_at=now(),finished_at=now()-interval '1 hour' WHERE id=?", id);
  }

  @Test void recursiveQueryDetectsIndirectCycleAndTerminates() throws Exception {
    jdbc.update("""
        INSERT INTO catalog.asset_processing(id,input_asset_version_id,output_asset_version_id,processing_type,processor,processor_version,status)
        VALUES (?,'50000000-0000-4000-8000-000000000002','50000000-0000-4000-8000-000000000001','TEST','test','1','SUCCEEDED')
        """, UUID.randomUUID());
    var rows = jdbc.queryForList(queries().get(6));
    assertEquals(4, rows.size());
    assertEquals(1, rows.stream().filter(r -> Boolean.TRUE.equals(r.get("is_cycle"))).count());
  }
}
