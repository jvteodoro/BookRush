package com.bookrush.catalog.storage;

import com.bookrush.catalog.asset.AssetService;
import com.bookrush.catalog.persistence.model.*;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named="STORAGE_ENABLED",matches="true")
class AssetRecoveryIT {
  @SpyBean ObjectStorage storage;
  @Autowired AssetService service;
  @Autowired JdbcTemplate jdbc;
  private UUID book() {
    UUID id=UUID.randomUUID();jdbc.update("INSERT INTO catalog.book(id,canonical_title,status) VALUES (?,'Recovery','ACTIVE')",id);return id;
  }
  private AssetService.UploadResult upload(UUID book,UUID existing) throws Exception {
    return service.upload(book,existing,null,UUID.fromString("10000000-0000-4000-8000-000000000001"),null,BookAssetAssetType.TXT,
        BookAssetAssetRole.PROCESSING,"retry.txt","text/plain",new ByteArrayInputStream("abc".getBytes()));
  }
  @Test void failedStorageLeavesDurableRecoveryRow() throws Exception {
    UUID book=book();
    doThrow(new StorageFailure(StorageFailure.Kind.UNAVAILABLE)).when(storage).upload(any(),any(),anyString(),anyString());
    try {
      assertThrows(StorageFailure.class,()->upload(book,null));
      assertEquals("FAILED",jdbc.queryForObject("""
          SELECT v.status FROM catalog.book_asset_version v JOIN catalog.book_asset a ON a.id=v.book_asset_id WHERE a.book_id=?
          """,String.class,book));
      assertNotNull(jdbc.queryForObject("""
          SELECT v.object_key FROM catalog.book_asset_version v JOIN catalog.book_asset a ON a.id=v.book_asset_id WHERE a.book_id=?
          """,String.class,book));
    } finally {reset(storage);}
  }
  @Test void partialDeleteIsRetryableAndVersionsRemainDistinct() throws Exception {
    UUID book=book();
    var first=upload(book,null);
    var second=upload(book,first.asset().id());
    assertEquals(2,second.version().number());
    assertNotEquals(first.version().objectKey(),second.version().objectKey());
    doThrow(new StorageFailure(StorageFailure.Kind.UNAVAILABLE)).when(storage).delete(any());
    try {
      assertThrows(StorageFailure.class,()->service.delete(book,first.asset().id()));
      assertEquals("DELETED",jdbc.queryForObject("SELECT status FROM catalog.book_asset WHERE id=?",String.class,first.asset().id()));
      assertTrue(storage.head(new ObjectStorage.Location(first.version().bucket(),first.version().objectKey())).isPresent());
    } finally {reset(storage);}
    service.delete(book,first.asset().id());
    assertTrue(service.versions(book,first.asset().id(),0).stream().allMatch(v->v.status()==BookAssetVersionStatus.DELETED));
  }
}
