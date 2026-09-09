package com.bookrush.catalog.asset;

import com.bookrush.catalog.persistence.model.*;
import com.bookrush.catalog.storage.*;
import jakarta.persistence.*;
import java.io.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
@ConditionalOnProperty(name="storage.enabled",havingValue="true")
public class AssetService {
  private static final Logger log=LoggerFactory.getLogger(AssetService.class);
  private final EntityManager em;
  private final TransactionTemplate tx;
  private final ObjectStorage storage;
  private final StorageProperties props;
  private final BucketSelector buckets;
  private final JdbcTemplate jdbc;
  public AssetService(EntityManager em,PlatformTransactionManager manager,ObjectStorage storage,StorageProperties props,BucketSelector buckets) {
    this(em, manager, storage, props, buckets, null);
  }
  @Autowired
  public AssetService(EntityManager em,PlatformTransactionManager manager,ObjectStorage storage,StorageProperties props,
      BucketSelector buckets, JdbcTemplate jdbc) {
    this.em=em;this.tx=new TransactionTemplate(manager);this.storage=storage;this.props=props;this.buckets=buckets;this.jdbc=jdbc;
  }
  /** Asset metadata only: no signed capability or permanent URL is persisted. */
  public record AssetView(UUID id,UUID bookId,UUID editionId,BookAssetAssetType type,BookAssetAssetRole role,BookAssetStatus status) {}
  public record VersionView(UUID id,int number,BookAssetVersionStatus status,String bucket,String objectKey,String contentType,Long sizeBytes,String sha256) {}
  public record UploadResult(AssetView asset,VersionView version) {}
  public record DownloadLink(URI url,Instant expiresAt) {}
  private record Reservation(UUID assetId,UUID versionId,ObjectStorage.Location location) {}
  private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Asset or book not found"); }
  private BookAsset asset(UUID book,UUID id) {
    BookAsset a=em.find(BookAsset.class,id);
    if(a==null || !a.getBookId().equals(book)) throw missing();
    return a;
  }
  private AssetView view(BookAsset a) { return new AssetView(a.getId(),a.getBookId(),a.getEditionId(),a.getAssetType(),a.getAssetRole(),a.getStatus()); }
  private VersionView view(BookAssetVersion v) { return new VersionView(v.getId(),v.getVersionNumber(),v.getStatus(),v.getBucket(),v.getObjectKey(),v.getContentType(),v.getSizeBytes(),v.getSha256()); }
  private ObjectStorage.Location location(BookAssetVersion v) { return new ObjectStorage.Location(v.getBucket(),v.getObjectKey()); }
  public UploadResult upload(UUID book,UUID existing,UUID edition,UUID source,UUID license,
      BookAssetAssetType type,BookAssetAssetRole role,String filename,String declared,InputStream input) throws IOException {
    try(UploadFile file=UploadFile.read(input,filename,declared,type,props.maxUploadBytes())) {
      if (existing != null && role == BookAssetAssetRole.SOURCE)
        throw new IllegalArgumentException("Source assets are immutable");
      Reservation r=tx.execute(status->{
        if(em.find(Book.class,book)==null) throw missing();
        BookAsset a;
        if(existing==null) {
          if(source==null || em.find(Source.class,source)==null) throw new IllegalArgumentException("Unknown source");
          if(license!=null && em.find(License.class,license)==null) throw new IllegalArgumentException("Unknown license");
          if(edition!=null) {
            Edition e=em.find(Edition.class,edition);
            if(e==null || !e.getBookId().equals(book)) throw new IllegalArgumentException("Edition does not belong to work");
          }
          a=new BookAsset();a.setBookId(book);a.setEditionId(edition);a.setSourceId(source);a.setLicenseId(license);
          a.setAssetType(type);a.setAssetRole(role);a.setStatus(BookAssetStatus.INACTIVE);em.persist(a);em.flush();
        } else {
          a=asset(book,existing); em.lock(a,LockModeType.PESSIMISTIC_WRITE);
          if(a.getStatus()==BookAssetStatus.DELETED) throw new IllegalArgumentException("Asset is deleted");
          if(a.getAssetType()!=type) throw new IllegalArgumentException("Version type differs");
          a.setStatus(BookAssetStatus.INACTIVE); // Every new version requires explicit approval again.
        }
        int number=em.createQuery("select coalesce(max(v.versionNumber),0) from BookAssetVersion v where v.bookAssetId=:id",Integer.class)
            .setParameter("id",a.getId()).getSingleResult()+1;
        BookAssetVersion v=new BookAssetVersion();v.setBookAssetId(a.getId());v.setVersionNumber(number);v.setStorageProvider(StorageProvider.S3);
        v.setBucket(buckets.select(a.getAssetRole()));v.setObjectKey(ObjectKeyBuilder.build(book,a.getId(),a.getAssetRole(),number,file.filename()));
        v.setOriginalFilename(file.filename());v.setContentType(file.contentType());v.setSizeBytes(file.size());v.setSha256(file.sha256());
        v.setStatus(BookAssetVersionStatus.PENDING_UPLOAD);em.persist(v);em.flush();
        return new Reservation(a.getId(),v.getId(),location(v));
      });
      boolean collision=false;
      try {
        storage.upload(r.location(),file.path(),file.contentType(),file.sha256());
        var metadata=storage.head(r.location()).orElseThrow(()->new StorageFailure(StorageFailure.Kind.NOT_FOUND));
        if(metadata.size()!=file.size() || !file.sha256().equals(metadata.sha256()) || !file.contentType().equals(metadata.contentType()))
          throw new StorageFailure(StorageFailure.Kind.UNAVAILABLE);
        UploadResult result=tx.execute(status->{
          var current=asset(book,r.assetId());em.lock(current,LockModeType.PESSIMISTIC_WRITE);
          if(current.getStatus()==BookAssetStatus.DELETED) throw new IllegalArgumentException("Asset deleted during upload");
          if (jdbc != null) {
            em.createNativeQuery("UPDATE catalog.book_asset_version SET verified_at=clock_timestamp(), verification_method='HEAD_SHA256', availability_status='AVAILABLE', current_eligible=true WHERE id=:version")
                .setParameter("version", r.versionId()).executeUpdate();
          }
          var v=em.find(BookAssetVersion.class,r.versionId());v.setStatus(BookAssetVersionStatus.AVAILABLE);em.flush();
          if (jdbc != null) {
            em.createNativeQuery("UPDATE catalog.book_asset SET current_version_id=:version WHERE id=:asset")
                .setParameter("version", r.versionId()).setParameter("asset", r.assetId()).executeUpdate();
          }
          return new UploadResult(view(asset(book,r.assetId())),view(v));
        });
        log.info("asset upload result=ok bookId={} assetId={} versionId={}",book,r.assetId(),r.versionId());
        return result;
      } catch(RuntimeException failure) {
        collision=failure instanceof StorageFailure s && s.kind()==StorageFailure.Kind.CONFLICT;
        // Keep the object for storage_intent/reconciliation. A failed database
        // confirmation must not eagerly delete a possibly verified immutable object.
        try { tx.executeWithoutResult(status->{em.find(BookAssetVersion.class,r.versionId()).setStatus(BookAssetVersionStatus.FAILED);}); }
        catch(RuntimeException ignored) { log.error("asset recovery required bookId={} assetId={} versionId={}",book,r.assetId(),r.versionId()); }
        // A committed pending/failed row retains the exact key even if DB or compensation is unavailable.
        throw failure;
      }
    }
  }
  public List<AssetView> list(UUID book,int page) {
    return tx.execute(s->{
      if(em.find(Book.class,book)==null) throw missing();
      return em.createQuery("select a from BookAsset a where a.bookId=:book order by a.id",BookAsset.class)
          .setParameter("book",book).setFirstResult(Math.multiplyExact(Math.max(0,page),50)).setMaxResults(50).getResultList().stream().map(this::view).toList();
    });
  }
  public AssetView get(UUID book,UUID id) { return tx.execute(s->view(asset(book,id))); }
  public List<VersionView> versions(UUID book,UUID id,int page) {
    return tx.execute(s->{asset(book,id);return em.createQuery("select v from BookAssetVersion v where v.bookAssetId=:id order by v.versionNumber desc",BookAssetVersion.class)
        .setParameter("id",id).setFirstResult(Math.multiplyExact(Math.max(0,page),50)).setMaxResults(50).getResultList().stream().map(this::view).toList();});
  }
  public void approve(UUID book,UUID id) {
    tx.executeWithoutResult(s->{
      BookAsset a=asset(book,id);em.lock(a,LockModeType.PESSIMISTIC_WRITE);
      if(a.getStatus()==BookAssetStatus.DELETED || a.getAssetRole()!=BookAssetAssetRole.PUBLIC || !distributionAllowed(a))
        throw new ResponseStatusException(HttpStatus.CONFLICT,"Public distribution requires explicit redistribution permission");
      long pending=em.createQuery("select count(v) from BookAssetVersion v where v.bookAssetId=:id and v.status=:status",Long.class)
          .setParameter("id",id).setParameter("status",BookAssetVersionStatus.PENDING_UPLOAD).getSingleResult();
      if(pending>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Upload still pending");
      latest(a.getId());
      a.setStatus(BookAssetStatus.ACTIVE);
    });
  }
  private boolean licensed(BookAsset a) {
    UUID id=a.getLicenseId();
    if(id==null && a.getEditionId()!=null) id=em.find(Edition.class,a.getEditionId()).getLicenseId();
    return id!=null && Boolean.TRUE.equals(em.find(License.class,id).getRedistributionAllowed());
  }
  private boolean distributionAllowed(BookAsset a) {
    if (jdbc == null) return licensed(a);
    Integer approved = jdbc.queryForObject("""
        SELECT count(*) FROM catalog.rights_decision
        WHERE action='DISTRIBUTION' AND distribution_status='APPROVED' AND territory='GLOBAL'
          AND (asset_id=? OR edition_id=?)
          AND (valid_from IS NULL OR valid_from <= clock_timestamp())
          AND (valid_until IS NULL OR valid_until > clock_timestamp())
        """, Integer.class, a.getId(), a.getEditionId());
    return (approved != null && approved > 0) || licensed(a);
  }
  private BookAssetVersion latest(UUID asset) {
    return em.createQuery("select v from BookAssetVersion v where v.bookAssetId=:id and v.status=:status order by v.versionNumber desc",BookAssetVersion.class)
        .setParameter("id",asset).setParameter("status",BookAssetVersionStatus.AVAILABLE).setMaxResults(1).getResultStream().findFirst().orElseThrow(AssetService::missing);
  }
  public DownloadLink link(UUID book,UUID id,boolean publicRequest) {
    return tx.execute(s->{
      BookAsset a=asset(book,id);
      if(a.getStatus()==BookAssetStatus.DELETED) throw missing();
      if(publicRequest && (a.getStatus()!=BookAssetStatus.ACTIVE || a.getAssetRole()!=BookAssetAssetRole.PUBLIC
          || em.find(Book.class,book).getStatus()!=BookStatus.ACTIVE || !distributionAllowed(a))) throw missing();
      var v=latest(id);
      if(storage.head(location(v)).isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Object missing; reconciliation required");
      Duration ttl=Duration.ofSeconds(props.urlTtlSeconds());
      return new DownloadLink(storage.downloadUrl(location(v),v.getOriginalFilename(),ttl),Instant.now().plus(ttl));
    });
  }
  public void delete(UUID book,UUID id) {
    List<VersionView> versions=tx.execute(s->{
      BookAsset a=asset(book,id);em.lock(a,LockModeType.PESSIMISTIC_WRITE);a.setStatus(BookAssetStatus.DELETED);
      return em.createQuery("select v from BookAssetVersion v where v.bookAssetId=:id",BookAssetVersion.class).setParameter("id",id).getResultList().stream().map(this::view).toList();
    });
    for(var v:versions) if(v.status()!=BookAssetVersionStatus.DELETED) {
      storage.delete(new ObjectStorage.Location(v.bucket(),v.objectKey()));
      tx.executeWithoutResult(s->em.find(BookAssetVersion.class,v.id()).setStatus(BookAssetVersionStatus.DELETED));
    }
  }
}
