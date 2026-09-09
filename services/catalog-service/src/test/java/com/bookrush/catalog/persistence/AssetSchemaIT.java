package com.bookrush.catalog.persistence;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AssetSchemaIT {
  @Autowired JdbcTemplate jdbc;
  private UUID book() {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO catalog.book(id,canonical_title) VALUES (?,'Test work')", id);
    return id;
  }
  private UUID asset(UUID book, UUID edition) {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO catalog.book_asset(id,book_id,edition_id,asset_type,asset_role,source_id)
        SELECT ?,?,?,'EPUB','SOURCE',id FROM catalog.source WHERE code='GUTENBERG'
        """, id, book, edition);
    return id;
  }
  private UUID version(UUID asset, int number, String key, String hash, Long size, String status) {
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO catalog.book_asset_version(id,book_asset_id,version_number,storage_provider,
          bucket,object_key,content_type,sha256,size_bytes,status)
        VALUES (?,?,?,'S3','books-source',?,'application/epub+zip',?,?,?)
        """, id, asset, number, key, hash, size, status);
    return id;
  }
  @Test void editionMustBelongToAssetWork() {
    UUID work = book(), edition = UUID.randomUUID();
    jdbc.update("INSERT INTO catalog.edition(id,book_id) VALUES (?,?)", edition, work);
    asset(work, edition);
    asset(work, null);
    assertThrows(DataIntegrityViolationException.class, () -> asset(book(), edition));
    assertThrows(DataIntegrityViolationException.class, () -> asset(work, UUID.randomUUID()));
  }
  @Test void versionsShareHashButNotNumberOrObjectIdentity() {
    UUID asset = asset(book(), null);
    String key = UUID.randomUUID().toString();
    version(asset, 1, key, "a".repeat(64), 1L, "AVAILABLE");
    version(asset, 2, key + "-2", "a".repeat(64), 1L, "AVAILABLE");
    assertThrows(DataIntegrityViolationException.class, () -> version(asset, 2, key + "-3", null, null, "PENDING_UPLOAD"));
    assertThrows(DataIntegrityViolationException.class, () -> version(asset(book(), null), 1, key, null, null, "PENDING_UPLOAD"));
  }
  @Test void rejectsInvalidPhysicalMetadata() {
    UUID asset = asset(book(), null);
    for (String hash : new String[] {"A".repeat(64), "a".repeat(63), "g".repeat(64)}) {
      assertThrows(DataIntegrityViolationException.class, () -> version(asset, 1, UUID.randomUUID().toString(), hash, 1L, "AVAILABLE"));
    }
    assertThrows(DataIntegrityViolationException.class, () -> version(asset, 0, "invalid", null, null, "PENDING_UPLOAD"));
    assertThrows(DataIntegrityViolationException.class, () -> version(asset, 1, "invalid", null, -1L, "PENDING_UPLOAD"));
    assertThrows(DataIntegrityViolationException.class, () -> version(asset, 1, " ", null, null, "PENDING_UPLOAD"));
    assertThrows(DataIntegrityViolationException.class, () -> version(UUID.randomUUID(), 1, "invalid", null, null, "PENDING_UPLOAD"));
  }
  @Test void availableRequiresVerifiedMetadata() {
    UUID asset = asset(book(), null);
    UUID pending = version(asset, 1, UUID.randomUUID().toString(), null, null, "PENDING_UPLOAD");
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE catalog.book_asset_version SET status='AVAILABLE' WHERE id=?", pending));
    jdbc.update("UPDATE catalog.book_asset_version SET sha256=?,size_bytes=0,status='AVAILABLE' WHERE id=?", "b".repeat(64), pending);
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE catalog.book_asset_version SET content_type=NULL WHERE id=?", pending));
    jdbc.update("UPDATE catalog.book_asset_version SET status='MISSING' WHERE id=?", pending);
    assertEquals("MISSING", jdbc.queryForObject("SELECT status FROM catalog.book_asset_version WHERE id=?", String.class, pending));
  }
  @Test void latestAvailableSkipsFailedAndPendingVersions() {
    UUID asset = asset(book(), null);
    UUID expected = version(asset, 1, UUID.randomUUID().toString(), "c".repeat(64), 10L, "AVAILABLE");
    version(asset, 2, UUID.randomUUID().toString(), null, null, "FAILED");
    version(asset, 3, UUID.randomUUID().toString(), null, null, "PENDING_UPLOAD");
    assertEquals(expected, jdbc.queryForObject("""
        SELECT v.id FROM catalog.book_asset_version v
        JOIN catalog.book_asset a ON a.id=v.book_asset_id
        WHERE a.id=? AND a.status='ACTIVE' AND v.status='AVAILABLE'
        ORDER BY v.version_number DESC LIMIT 1
        """, UUID.class, asset));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM catalog.book_asset WHERE id=?", asset));
  }
  @Test void assetLicenseAndFiniteConceptsAreEnforced() {
    UUID asset = asset(book(), null);
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE catalog.book_asset SET license_id=? WHERE id=?", UUID.randomUUID(), asset));
    for (String column : new String[] {"asset_type", "asset_role", "status"}) {
      assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE catalog.book_asset SET " + column + "='INVALID' WHERE id=?", asset));
    }
    jdbc.update("UPDATE catalog.book_asset SET license_id=(SELECT id FROM catalog.license WHERE code='UNKNOWN') WHERE id=?", asset);
  }
}
