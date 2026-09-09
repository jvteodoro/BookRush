package com.bookrush.catalog.persistence.repository;

import com.bookrush.catalog.persistence.model.BookAssetVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookAssetVersionRepository extends JpaRepository<BookAssetVersion, UUID> {
  /** Ignores pending/failed/missing versions and inactive logical assets. No authorization is implied. */
  @Query(value = """
      SELECT v.* FROM catalog.book_asset_version v
      JOIN catalog.book_asset a ON a.id=v.book_asset_id
      WHERE a.id=:assetId AND a.status='ACTIVE' AND v.status='AVAILABLE'
      ORDER BY v.version_number DESC LIMIT 1
      """, nativeQuery = true)
  Optional<BookAssetVersion> findLatestAvailable(@Param("assetId") UUID assetId);
  Page<BookAssetVersion> findByBookAssetIdOrderByVersionNumberDesc(UUID assetId, Pageable pageable);
  Page<BookAssetVersion> findBySha256OrderByIdAsc(String sha256, Pageable pageable);
}
