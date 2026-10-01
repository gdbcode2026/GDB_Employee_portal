package com.growdigitalbridge.payroll.service;

import com.growdigitalbridge.payroll.api.dto.StatutoryProfileDtos;
import com.growdigitalbridge.payroll.domain.EmployeeStatutoryProfile;
import com.growdigitalbridge.payroll.repository.EmployeeStatutoryProfileRepository;
import com.growdigitalbridge.payroll.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages one {@link EmployeeStatutoryProfile} per employee (item 3). No statutory rate,
 * threshold, or eligibility-calculation rule is ever accepted or computed here - only
 * applicability status, identifiers, and effective dates. {@code upsert} never infers {@code
 * NOT_APPLICABLE} from a missing identifier - the caller must explicitly set each status.
 */
@Service
public class StatutoryProfileService {

    private final EmployeeStatutoryProfileRepository repository;
    private final PayrollAuditLog auditLog;

    public StatutoryProfileService(EmployeeStatutoryProfileRepository repository, PayrollAuditLog auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    @Transactional
    public StatutoryProfileDtos.Response upsert(UUID employeeRef, StatutoryProfileDtos.UpsertRequest request,
                                                 String actor, UUID correlationId) {
        Instant now = Instant.now();
        EmployeeStatutoryProfile profile = repository.findByEmployeeRef(employeeRef).orElse(null);
        boolean isNew = profile == null;
        if (isNew) {
            profile = new EmployeeStatutoryProfile(UUID.randomUUID(), employeeRef,
                    request.pfStatus(), request.pfUan(), request.pfMemberId(), request.pfEffectiveFrom(), request.pfEffectiveTo(),
                    request.esiStatus(), request.esiIdentifier(), request.esiEffectiveFrom(), request.esiEffectiveTo(),
                    request.ptStatus(), request.ptJurisdiction(), request.ptEffectiveFrom(), request.ptEffectiveTo(),
                    actor, now);
        } else {
            profile.update(request.pfStatus(), request.pfUan(), request.pfMemberId(), request.pfEffectiveFrom(), request.pfEffectiveTo(),
                    request.esiStatus(), request.esiIdentifier(), request.esiEffectiveFrom(), request.esiEffectiveTo(),
                    request.ptStatus(), request.ptJurisdiction(), request.ptEffectiveFrom(), request.ptEffectiveTo(),
                    actor, now);
        }
        repository.save(profile);

        if (isNew) {
            auditLog.statutoryProfileCreated(profile.getId(), employeeRef, actor, correlationId);
        } else {
            auditLog.statutoryProfileUpdated(profile.getId(), employeeRef, actor, correlationId);
        }
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public StatutoryProfileDtos.Response getByEmployee(UUID employeeRef, String actor, UUID correlationId) {
        EmployeeStatutoryProfile profile = repository.findByEmployeeRef(employeeRef)
                .orElseThrow(() -> new ResourceNotFoundException("No statutory profile exists for employee " + employeeRef + "."));
        auditLog.sensitiveRead("statutory_profile", profile.getId(), actor, correlationId);
        return toResponse(profile);
    }

    private StatutoryProfileDtos.Response toResponse(EmployeeStatutoryProfile profile) {
        return new StatutoryProfileDtos.Response(profile.getId(), profile.getEmployeeRef(),
                profile.getPfStatus(), profile.getPfUan(), profile.getPfMemberId(), profile.getPfEffectiveFrom(), profile.getPfEffectiveTo(),
                profile.getEsiStatus(), profile.getEsiIdentifier(), profile.getEsiEffectiveFrom(), profile.getEsiEffectiveTo(),
                profile.getPtStatus(), profile.getPtJurisdiction(), profile.getPtEffectiveFrom(), profile.getPtEffectiveTo(),
                profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
