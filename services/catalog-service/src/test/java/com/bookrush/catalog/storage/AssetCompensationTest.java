package com.bookrush.catalog.storage;

import com.bookrush.catalog.asset.AssetService;
import com.bookrush.catalog.persistence.model.*;
import jakarta.persistence.*;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssetCompensationTest {
  @Test void failedCommitCompensatesEvenWhenDeletionAlsoFails() throws Exception { exercise(true); }
  @Test void failedCommitCompensatesSuccessfulUpload() throws Exception { exercise(false); }
  @SuppressWarnings("unchecked")
  private void exercise(boolean deleteFails) throws Exception {
    EntityManager em=mock(EntityManager.class);PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenAnswer(i->new SimpleTransactionStatus());
    java.util.concurrent.atomic.AtomicInteger commits=new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(i->{if(commits.incrementAndGet()==2) throw new org.springframework.transaction.TransactionSystemException("commit failed");return null;}).when(manager).commit(any());
    UUID book=UUID.randomUUID(),source=UUID.randomUUID();
    when(em.find(Book.class,book)).thenReturn(new Book());
    when(em.find(Source.class,source)).thenReturn(new Source());
    final BookAsset[] asset={null};final BookAssetVersion[] version={null};
    doAnswer(i->{
      Object e=i.getArgument(0);
      if(e instanceof BookAsset a){a.setId(UUID.randomUUID());asset[0]=a;when(em.find(BookAsset.class,a.getId())).thenReturn(a);}
      if(e instanceof BookAssetVersion v){v.setId(UUID.randomUUID());version[0]=v;when(em.find(BookAssetVersion.class,v.getId())).thenReturn(v);}
      return null;
    }).when(em).persist(any());
    TypedQuery<Integer> query=mock(TypedQuery.class);when(em.createQuery(anyString(),eq(Integer.class))).thenReturn(query);
    when(query.setParameter(anyString(),any())).thenReturn(query);when(query.getSingleResult()).thenReturn(0);
    ObjectStorage storage=mock(ObjectStorage.class);
    when(storage.head(any())).thenReturn(Optional.of(new ObjectStorage.Metadata(3,"text/plain","ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")));
    if(deleteFails) doThrow(new StorageFailure(StorageFailure.Kind.UNAVAILABLE)).when(storage).delete(any());
    var p=new StorageProperties(true,null,null,"us-east-1",null,null,true,60,100,Duration.ofSeconds(30),"test-token-long-enough-to-be-validated",
        new StorageProperties.Buckets("src","pub","tmp","models"));
    var service=new AssetService(em,manager,storage,p,new BucketSelector(p.buckets()));
    assertThrows(org.springframework.transaction.TransactionSystemException.class,()->service.upload(book,null,null,source,null,
        BookAssetAssetType.TXT,BookAssetAssetRole.SOURCE,"a.txt","text/plain",new ByteArrayInputStream("abc".getBytes())));
    verify(storage).delete(any());assertEquals(BookAssetVersionStatus.FAILED,version[0].getStatus());
    assertNotNull(version[0].getObjectKey());assertEquals(3,commits.get());
  }
}
