package com.growdigitalbridge.audit.api;
import java.util.Map; import org.springframework.http.ProblemDetail; import org.springframework.http.HttpStatus; import org.springframework.web.bind.annotation.*;

/** Audit search is intentionally unavailable until GDB grants an approved query/retention policy. */
@RestController @RequestMapping("/api/v1/audit")
class AuditController {
    @GetMapping("/events")
    Map<String, Object> events() { throw new AuditSearchNotAvailableException(); }
    @ExceptionHandler(AuditSearchNotAvailableException.class)
    ProblemDetail unavailable() { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED, "Audit search is not enabled in the platform foundation."); }
    private static final class AuditSearchNotAvailableException extends RuntimeException { }
}
