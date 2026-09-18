package com.growdigitalbridge.leave.service;

import com.growdigitalbridge.leave.api.dto.LeaveBalanceDtos;
import com.growdigitalbridge.leave.domain.LeaveBalance;
import com.growdigitalbridge.leave.repository.LeaveBalanceRepository;
import com.growdigitalbridge.leave.repository.LeaveTypeRepository;
import com.growdigitalbridge.leave.service.exception.ConflictException;
import com.growdigitalbridge.leave.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns per-employee leave balance allocation. RBAC.md documents no dedicated
 * balance-management permission, so allocate/adjust are gated by {@code leave.approve.all}
 * (SecurityConfig) - the closest documented HR/Finance-level leave capability - as the
 * flagged minimum decision needed to make the "concurrency-safe balance reservation"
 * MICROSERVICES.md requires actually exercisable. GDB should define a dedicated permission.
 */
@Service
public class LeaveBalanceService {

    private final LeaveBalanceRepository repository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveAccessGuard accessGuard;

    public LeaveBalanceService(LeaveBalanceRepository repository, LeaveTypeRepository leaveTypeRepository, LeaveAccessGuard accessGuard) {
        this.repository = repository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional
    public LeaveBalanceDtos.Response allocate(LeaveBalanceDtos.AllocateRequest request, String actor) {
        if (!leaveTypeRepository.existsById(request.leaveTypeId())) {
            throw new ResourceNotFoundException("Leave type " + request.leaveTypeId() + " was not found.");
        }
        if (repository.existsByEmployeeRefAndLeaveTypeIdAndPeriodYear(request.employeeRef(), request.leaveTypeId(), request.periodYear())) {
            throw new ConflictException("A leave balance already exists for this employee, type, and year.");
        }
        Instant now = Instant.now();
        LeaveBalance balance = new LeaveBalance(UUID.randomUUID(), request.employeeRef(), request.leaveTypeId(),
                request.periodYear(), request.allocated(), actor, now);
        repository.save(balance);
        return toResponse(balance);
    }

    @Transactional
    public LeaveBalanceDtos.Response adjust(UUID id, LeaveBalanceDtos.AdjustRequest request, String actor) {
        LeaveBalance balance = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave balance " + id + " was not found."));
        balance.adjustAllocation(request.allocated(), actor, Instant.now());
        return toResponse(balance);
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceDtos.Response> listSelf(Authentication authentication) {
        UUID self = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        return repository.findByEmployeeRef(self).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceDtos.Response> list(Authentication authentication, UUID employeeId) {
        LeaveAccessGuard.ListScope scope = accessGuard.resolveListScope(authentication);
        if (!scope.allowed()) {
            throw new AccessDeniedException("Listing leave balances requires team or all read scope.");
        }
        List<LeaveBalance> balances;
        if (scope.unrestricted()) {
            balances = employeeId != null ? repository.findByEmployeeRefIn(Set.of(employeeId)) : repository.findAll();
        } else if (scope.allowedIds().isEmpty() || (employeeId != null && !scope.allowedIds().contains(employeeId))) {
            balances = List.of();
        } else {
            balances = repository.findByEmployeeRefIn(employeeId != null ? Set.of(employeeId) : scope.allowedIds());
        }
        return balances.stream().map(this::toResponse).toList();
    }

    private LeaveBalanceDtos.Response toResponse(LeaveBalance balance) {
        return new LeaveBalanceDtos.Response(balance.getId(), balance.getEmployeeRef(), balance.getLeaveTypeId(),
                balance.getPeriodYear(), balance.getAllocated(), balance.getUsed(), balance.getReserved(), balance.available());
    }
}
