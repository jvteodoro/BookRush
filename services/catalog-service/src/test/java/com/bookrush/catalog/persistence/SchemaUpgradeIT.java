package com.bookrush.catalog.persistence;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SchemaUpgradeIT {
  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate jdbc;

  @Test void existingV4AssetSurvivesIngestionAndProcessingMigrations() {
    String schema = "upgrade_" + UUID.randomUUID().toString().replace("-","");
    try {
      Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("4").load().migrate();
      UUID book=UUID.randomUUID(), asset=UUID.randomUUID(), version=UUID.randomUUID();
      jdbc.update("INSERT INTO "+schema+".book(id,canonical_title) VALUES (?,'Existing')",book);
      jdbc.update("INSERT INTO "+schema+".book_asset(id,book_id,asset_type,asset_role,source_id) VALUES (?,?,'TXT','SOURCE','10000000-0000-4000-8000-000000000001')",asset,book);
      jdbc.update("INSERT INTO "+schema+".book_asset_version(id,book_asset_id,version_number,storage_provider,bucket,object_key) VALUES (?,?,1,'S3','books-source','existing')",version,asset);
      Flyway latest=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
      // V7+ operational migrations are additive; the upgrade must apply at
      // least the ingestion and processing migrations introduced after V4.
      assertTrue(latest.migrate().migrationsExecuted >= 2);
      latest.validate();
      assertEquals("existing",jdbc.queryForObject("SELECT object_key FROM "+schema+".book_asset_version WHERE id=?",String.class,version));
      assertNull(jdbc.queryForObject("SELECT ingestion_item_id FROM "+schema+".book_asset_version WHERE id=?",UUID.class,version));
      assertEquals(0,latest.migrate().migrationsExecuted);
    } finally {
      jdbc.execute("DROP SCHEMA "+schema+" CASCADE");
    }
  }
}
