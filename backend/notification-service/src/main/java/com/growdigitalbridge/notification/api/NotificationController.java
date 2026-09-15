package com.growdigitalbridge.notification.api;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/notifications") class NotificationController {
 @GetMapping public ProblemDetail unavailable() { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED, "Notifications are not enabled in the platform foundation."); }
}
