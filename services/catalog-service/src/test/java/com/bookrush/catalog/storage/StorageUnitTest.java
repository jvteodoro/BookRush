package com.bookrush.catalog.storage;

import com.bookrush.catalog.asset.UploadFile;
import com.bookrush.catalog.persistence.model.*;
import java.io.*;
import java.nio.file.Files;
import java.util.UUID;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageUnitTest {
  @Test void keysRejectTraversalAndCollisions() {
    UUID book=UUID.randomUUID(),asset=UUID.randomUUID();
    for(String invalid:new String[]{"../a","a/b","a\\b","","á.txt",".hidden","a..b","x\n.txt"})
      assertThrows(IllegalArgumentException.class,()->ObjectKeyBuilder.build(book,asset,BookAssetAssetRole.SOURCE,1,invalid));
    assertThrows(IllegalArgumentException.class,()->ObjectKeyBuilder.build(book,asset,BookAssetAssetRole.SOURCE,0,"a.txt"));
    assertThrows(IllegalArgumentException.class,()->ObjectKeyBuilder.build(null,asset,BookAssetAssetRole.SOURCE,1,"a.txt"));
    assertEquals("books/"+book+"/source/"+asset+"/v1/a.txt",ObjectKeyBuilder.build(book,asset,BookAssetAssetRole.SOURCE,1,"a.txt"));
  }
  @Test void everyRoleSelectsConfiguredBucket() {
    var selector=new BucketSelector(new StorageProperties.Buckets("src","pub","tmp","models"));
    assertEquals("src",selector.select(BookAssetAssetRole.SOURCE));
    assertEquals("pub",selector.select(BookAssetAssetRole.PUBLIC));
    assertEquals("pub",selector.select(BookAssetAssetRole.COVER));
    for(var r:new BookAssetAssetRole[]{BookAssetAssetRole.NORMALIZED,BookAssetAssetRole.PROCESSING,BookAssetAssetRole.DERIVED}) assertEquals("tmp",selector.select(r));
    assertEquals("models",selector.select(BookAssetAssetRole.ANALYTICS));
    assertEquals("models",selector.select(BookAssetAssetRole.ML));
  }
  @Test void streamingHashAndSpoolCleanup() throws Exception {
    java.nio.file.Path path;
    try(var file=UploadFile.read(new ByteArrayInputStream("abc".getBytes()),"book.txt","text/plain",BookAssetAssetType.TXT,3)) {
      path=file.path();assertEquals(3,file.size());
      assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",file.sha256());
    }
    assertFalse(Files.exists(path));
  }
  @Test void rejectsMimeSpoofingOversizeAndInvalidContent() {
    assertThrows(IllegalArgumentException.class,()->UploadFile.read(new ByteArrayInputStream("abc".getBytes()),"x.pdf","application/pdf",BookAssetAssetType.PDF,10));
    assertThrows(IllegalArgumentException.class,()->UploadFile.read(new ByteArrayInputStream("abc".getBytes()),"x.txt","application/pdf",BookAssetAssetType.TXT,10));
    assertThrows(IllegalArgumentException.class,()->UploadFile.read(new ByteArrayInputStream("abcd".getBytes()),"x.txt","text/plain",BookAssetAssetType.TXT,3));
    assertThrows(IOException.class,()->UploadFile.read(new ByteArrayInputStream(new byte[]{(byte)255}),"x.txt","text/plain",BookAssetAssetType.TXT,10));
    assertThrows(IOException.class,()->UploadFile.read(new ByteArrayInputStream("{bad}".getBytes()),"x.json","application/json",BookAssetAssetType.JSON,10));
  }
  @Test void configurationRejectsMissingAdminBadEndpointAndDuplicateBuckets() {
    try(var factory=Validation.buildDefaultValidatorFactory()) {
      var p=new StorageProperties(true,java.net.URI.create("http://internal:8333"),null,"us-east-1","key",null,true,0,0,
          java.time.Duration.ZERO,"",new StorageProperties.Buckets("same","same","tmp","models"));
      assertTrue(factory.getValidator().validate(p).size()>=6);
      assertFalse(p.toString().contains("key"));
    }
  }
}
