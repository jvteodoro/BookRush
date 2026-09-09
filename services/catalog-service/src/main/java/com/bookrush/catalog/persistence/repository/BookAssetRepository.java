package com.bookrush.catalog.persistence.repository;

import com.bookrush.catalog.persistence.model.BookAsset;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookAssetRepository extends JpaRepository<BookAsset, UUID> {
  Page<BookAsset> findByBookIdOrderByIdAsc(UUID bookId, Pageable pageable);
}
