package com.growdigitalbridge.asset.repository;

import com.growdigitalbridge.asset.domain.AssetAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetAssignmentRepository extends JpaRepository<AssetAssignment, UUID> {

    Optional<AssetAssignment> findByAssetIdAndReturnedAtIsNull(UUID assetId);

    List<AssetAssignment> findByEmployeeRefAndReturnedAtIsNull(UUID employeeRef);
}
