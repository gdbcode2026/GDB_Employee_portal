package com.growdigitalbridge.asset.repository;

import com.growdigitalbridge.asset.domain.Asset;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    boolean existsByTag(String tag);

    Page<Asset> findAll(Pageable pageable);
}
