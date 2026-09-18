package com.growdigitalbridge.leave.repository;

import com.growdigitalbridge.leave.domain.LeaveBalance;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {

    List<LeaveBalance> findByEmployeeRef(UUID employeeRef);

    List<LeaveBalance> findByEmployeeRefIn(Collection<UUID> employeeRefs);

    boolean existsByEmployeeRefAndLeaveTypeIdAndPeriodYear(UUID employeeRef, UUID leaveTypeId, int periodYear);

    /**
     * Locks the balance row for the duration of the enclosing transaction so a reserve/
     * release/consume sequence is safe under concurrent leave requests for the same
     * employee/type/year - the "concurrency-safe balance reservation" MICROSERVICES.md requires.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from LeaveBalance b where b.employeeRef = :employeeRef and b.leaveTypeId = :leaveTypeId and b.periodYear = :periodYear")
    Optional<LeaveBalance> findForUpdate(@Param("employeeRef") UUID employeeRef, @Param("leaveTypeId") UUID leaveTypeId,
                                          @Param("periodYear") int periodYear);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from LeaveBalance b where b.id = :id")
    Optional<LeaveBalance> findByIdForUpdate(@Param("id") UUID id);
}
