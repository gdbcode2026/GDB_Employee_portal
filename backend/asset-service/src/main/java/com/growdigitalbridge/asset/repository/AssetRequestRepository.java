package com.growdigitalbridge.asset.repository;

import com.growdigitalbridge.asset.domain.AssetRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRequestRepository extends JpaRepository<AssetRequest, UUID> { }
