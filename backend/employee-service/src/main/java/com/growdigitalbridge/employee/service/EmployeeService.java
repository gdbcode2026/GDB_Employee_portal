package com.growdigitalbridge.employee.service;

import com.growdigitalbridge.employee.api.dto.EmployeeDtos;
import com.growdigitalbridge.employee.api.dto.PageResponse;
import com.growdigitalbridge.employee.domain.Employee;
import com.growdigitalbridge.employee.domain.EmergencyContact;
import com.growdigitalbridge.employee.domain.EmployeeStatus;
import com.growdigitalbridge.employee.domain.Employment;
import com.growdigitalbridge.employee.domain.EmploymentStatus;
import com.growdigitalbridge.employee.repository.EmergencyContactRepository;
import com.growdigitalbridge.employee.repository.EmployeeRepository;
import com.growdigitalbridge.employee.repository.EmploymentRepository;
import com.growdigitalbridge.employee.service.exception.ConflictException;
import com.growdigitalbridge.employee.service.exception.InvalidLifecycleTransitionException;
import com.growdigitalbridge.employee.service.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmploymentRepository employmentRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final EmployeeAccessGuard accessGuard;
    private final OutboxEventWriter outboxEventWriter;

    public EmployeeService(EmployeeRepository employeeRepository, EmploymentRepository employmentRepository,
                            EmergencyContactRepository emergencyContactRepository, EmployeeAccessGuard accessGuard,
                            OutboxEventWriter outboxEventWriter) {
        this.employeeRepository = employeeRepository;
        this.employmentRepository = employmentRepository;
        this.emergencyContactRepository = emergencyContactRepository;
        this.accessGuard = accessGuard;
        this.outboxEventWriter = outboxEventWriter;
    }

    @Transactional(readOnly = true)
    public EmployeeDtos.Response getSelf(Authentication authentication) {
        Employee employee = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        return toResponse(employee);
    }

    @Transactional(readOnly = true)
    public EmployeeDtos.Response getById(UUID id, Authentication authentication) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + id + " was not found."));
        if (!accessGuard.canRead(authentication, employee)) {
            // Masks existence from a caller who cannot see this record (404, not 403).
            throw new ResourceNotFoundException("Employee " + id + " was not found.");
        }
        return toResponse(employee);
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeDtos.Summary> list(Authentication authentication, EmployeeStatus status, String query, Pageable pageable) {
        EmployeeAccessGuard.ListScope scope = accessGuard.resolveListScope(authentication);
        if (!scope.allowed()) {
            throw new AccessDeniedException("Listing employees requires team or all read scope.");
        }
        if (!scope.unrestricted() && scope.allowedIds().isEmpty()) {
            return PageResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
        }
        Page<Employee> page = scope.unrestricted()
                ? employeeRepository.searchAll(status, query, pageable)
                : employeeRepository.searchWithinScope(scope.allowedIds(), status, query, pageable);
        return PageResponse.of(page.map(this::toSummary));
    }

    @Transactional
    public EmployeeDtos.Response create(EmployeeDtos.CreateRequest request, String actor, UUID correlationId) {
        if (employeeRepository.existsByEmployeeNumber(request.employeeNumber())) {
            throw new ConflictException("Employee number '" + request.employeeNumber() + "' already exists.");
        }
        if (employeeRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email '" + request.email() + "' already exists.");
        }
        if (request.identitySubject() != null && employeeRepository.existsByIdentitySubject(request.identitySubject())) {
            throw new ConflictException("This identity is already linked to another employee.");
        }

        Instant now = Instant.now();
        Employee employee = new Employee(UUID.randomUUID(), request.employeeNumber(), request.firstName(),
                request.lastName(), request.email(), request.phone(), request.identitySubject(), actor, now);
        employeeRepository.save(employee);

        Employment employment = new Employment(UUID.randomUUID(), employee.getId(), request.employment().jobTitle(),
                request.employment().employmentType(), request.employment().startDate(), actor, now);
        employmentRepository.save(employment);

        List<EmergencyContact> contacts = replaceEmergencyContacts(employee.getId(), request.emergencyContacts(), actor, now);

        outboxEventWriter.write("employee.created.v1", employee.getId(), java.util.Map.of(
                "employeeId", employee.getId().toString(),
                "employmentId", employment.getId().toString(),
                "status", employee.getStatus().name()), correlationId);

        return toResponse(employee, employment, contacts);
    }

    @Transactional
    public EmployeeDtos.Response updateSelf(Authentication authentication, EmployeeDtos.SelfUpdateRequest request, String actor, UUID correlationId) {
        Employee employee = accessGuard.resolveSelf(authentication)
                .orElseThrow(() -> new ResourceNotFoundException("No employee profile is linked to this identity."));
        Instant now = Instant.now();
        List<String> changed = new ArrayList<>();

        if (request.phone() != null && !request.phone().equals(employee.getPhone())) {
            employee.updatePhone(request.phone(), actor, now);
            changed.add("phone");
        }

        List<EmergencyContact> contacts;
        if (request.emergencyContacts() != null) {
            contacts = replaceEmergencyContacts(employee.getId(), request.emergencyContacts(), actor, now);
            changed.add("emergencyContacts");
        } else {
            contacts = emergencyContactRepository.findByEmployeeId(employee.getId());
        }

        if (!changed.isEmpty()) {
            outboxEventWriter.write("employee.updated.v1", employee.getId(),
                    java.util.Map.of("employeeId", employee.getId().toString(), "changedFields", changed), correlationId);
        }

        return toResponse(employee, contacts);
    }

    @Transactional
    public EmployeeDtos.Response updateByAdmin(UUID id, EmployeeDtos.AdminUpdateRequest request, String actor, UUID correlationId) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + id + " was not found."));
        Instant now = Instant.now();
        List<String> changed = new ArrayList<>();

        String firstName = request.firstName() != null ? request.firstName() : employee.getFirstName();
        String lastName = request.lastName() != null ? request.lastName() : employee.getLastName();
        String email = request.email() != null ? request.email() : employee.getEmail();
        String phone = request.phone() != null ? request.phone() : employee.getPhone();

        if (request.email() != null && !request.email().equals(employee.getEmail())
                && employeeRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new ConflictException("Email '" + request.email() + "' already exists.");
        }

        boolean profileChanged = !firstName.equals(employee.getFirstName())
                || !lastName.equals(employee.getLastName())
                || !email.equals(employee.getEmail())
                || !Objects.equals(phone, employee.getPhone());
        if (!firstName.equals(employee.getFirstName())) changed.add("firstName");
        if (!lastName.equals(employee.getLastName())) changed.add("lastName");
        if (!email.equals(employee.getEmail())) changed.add("email");
        if (!Objects.equals(phone, employee.getPhone())) changed.add("phone");
        if (profileChanged) {
            employee.updateProfile(firstName, lastName, email, phone, actor, now);
        }

        if (request.employment() != null) {
            Employment activeEmployment = employmentRepository.findByEmployeeIdAndStatus(id, EmploymentStatus.ACTIVE)
                    .orElseThrow(() -> new ConflictException("Employee " + id + " has no active employment to update."));
            if (!activeEmployment.getJobTitle().equals(request.employment().jobTitle())
                    || activeEmployment.getEmploymentType() != request.employment().employmentType()) {
                activeEmployment.updateDetails(request.employment().jobTitle(), request.employment().employmentType(), actor, now);
                changed.add("employment");
            }
        }

        List<EmergencyContact> contacts;
        if (request.emergencyContacts() != null) {
            contacts = replaceEmergencyContacts(id, request.emergencyContacts(), actor, now);
            changed.add("emergencyContacts");
        } else {
            contacts = emergencyContactRepository.findByEmployeeId(id);
        }

        boolean deactivating = false;
        if (request.status() != null && request.status() != employee.getStatus()) {
            if (request.status() == EmployeeStatus.INACTIVE && employee.getStatus() == EmployeeStatus.ACTIVE) {
                employee.deactivate(actor, now);
                employmentRepository.findByEmployeeIdAndStatus(id, EmploymentStatus.ACTIVE)
                        .ifPresent(active -> active.end(now.atZone(ZoneOffset.UTC).toLocalDate(), actor, now));
                deactivating = true;
            } else {
                throw new InvalidLifecycleTransitionException(
                        "Reactivating a deactivated employee is not supported in this foundation.");
            }
        }

        if (deactivating) {
            outboxEventWriter.write("employee.deactivated.v1", id,
                    java.util.Map.of("employeeId", id.toString(), "effectiveAt", now.toString()), correlationId);
        } else if (!changed.isEmpty()) {
            outboxEventWriter.write("employee.updated.v1", id,
                    java.util.Map.of("employeeId", id.toString(), "changedFields", changed), correlationId);
        }

        return toResponse(employee, contacts);
    }

    private List<EmergencyContact> replaceEmergencyContacts(UUID employeeId, List<EmployeeDtos.EmergencyContactRequest> requests,
                                                              String actor, Instant now) {
        if (requests == null) {
            return emergencyContactRepository.findByEmployeeId(employeeId);
        }
        emergencyContactRepository.deleteByEmployeeId(employeeId);
        List<EmergencyContact> contacts = requests.stream()
                .map(r -> new EmergencyContact(UUID.randomUUID(), employeeId, r.name(), r.phone(), r.relationship(), actor, now))
                .toList();
        return emergencyContactRepository.saveAll(contacts);
    }

    private EmployeeDtos.Response toResponse(Employee employee) {
        return toResponse(employee, emergencyContactRepository.findByEmployeeId(employee.getId()));
    }

    private EmployeeDtos.Response toResponse(Employee employee, List<EmergencyContact> contacts) {
        Employment employment = currentEmployment(employee.getId()).orElse(null);
        return toResponse(employee, employment, contacts);
    }

    private EmployeeDtos.Response toResponse(Employee employee, Employment employment, List<EmergencyContact> contacts) {
        return new EmployeeDtos.Response(
                employee.getId(), employee.getEmployeeNumber(), employee.getFirstName(), employee.getLastName(),
                employee.getEmail(), employee.getPhone(), employee.getStatus(),
                employment == null ? null : toSummary(employment),
                contacts.stream().map(this::toContactResponse).toList(),
                employee.getCreatedAt(), employee.getUpdatedAt());
    }

    private EmployeeDtos.Summary toSummary(Employee employee) {
        return new EmployeeDtos.Summary(employee.getId(), employee.getEmployeeNumber(), employee.getFirstName(),
                employee.getLastName(), employee.getEmail(), employee.getStatus());
    }

    private EmployeeDtos.EmploymentSummary toSummary(Employment employment) {
        return new EmployeeDtos.EmploymentSummary(employment.getId(), employment.getJobTitle(), employment.getEmploymentType(),
                employment.getStartDate(), employment.getEndDate(), employment.getStatus());
    }

    private EmployeeDtos.EmergencyContactResponse toContactResponse(EmergencyContact contact) {
        return new EmployeeDtos.EmergencyContactResponse(contact.getId(), contact.getName(), contact.getPhone(), contact.getRelationship());
    }

    private java.util.Optional<Employment> currentEmployment(UUID employeeId) {
        return employmentRepository.findByEmployeeIdAndStatus(employeeId, EmploymentStatus.ACTIVE)
                .or(() -> employmentRepository.findFirstByEmployeeIdOrderByCreatedAtDesc(employeeId));
    }
}
