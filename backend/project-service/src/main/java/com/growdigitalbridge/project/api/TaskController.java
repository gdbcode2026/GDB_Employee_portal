package com.growdigitalbridge.project.api;

import com.growdigitalbridge.project.api.dto.PageResponse;
import com.growdigitalbridge.project.api.dto.TaskDtos;
import com.growdigitalbridge.project.domain.TaskPriority;
import com.growdigitalbridge.project.domain.TaskStatus;
import com.growdigitalbridge.project.security.CurrentActor;
import com.growdigitalbridge.project.security.CurrentCorrelation;
import com.growdigitalbridge.project.service.TaskService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TaskController {

    private final TaskService service;

    TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/projects/{projectId}/tasks")
    PageResponse<TaskDtos.Response> listInProject(@PathVariable UUID projectId, Authentication authentication,
                                                   @RequestParam(required = false) TaskStatus status,
                                                   @RequestParam(required = false) TaskPriority priority,
                                                   @RequestParam(required = false) UUID assigneeId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String sort) {
        return service.listInProject(authentication, projectId, status, priority, assigneeId, PagingSupport.of(page, size, sort, "createdAt"));
    }

    @PostMapping("/api/v1/projects/{projectId}/tasks")
    ResponseEntity<TaskDtos.Response> create(@PathVariable UUID projectId, Authentication authentication,
                                              @Valid @RequestBody TaskDtos.CreateRequest request) {
        TaskDtos.Response created = service.create(authentication, projectId, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + created.id())).body(created);
    }

    @GetMapping("/api/v1/tasks/me")
    PageResponse<TaskDtos.Response> me(Authentication authentication,
                                        @RequestParam(required = false) TaskStatus status,
                                        @RequestParam(required = false) TaskPriority priority,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(required = false) String sort) {
        return service.listSelf(authentication, status, priority, PagingSupport.of(page, size, sort, "createdAt"));
    }

    @GetMapping("/api/v1/tasks")
    PageResponse<TaskDtos.Response> list(Authentication authentication,
                                          @RequestParam(required = false) TaskStatus status,
                                          @RequestParam(required = false) TaskPriority priority,
                                          @RequestParam(required = false) UUID assigneeId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String sort) {
        return service.list(authentication, status, priority, assigneeId, PagingSupport.of(page, size, sort, "createdAt"));
    }

    @PatchMapping("/api/v1/tasks/{id}")
    TaskDtos.Response update(@PathVariable UUID id, Authentication authentication, @Valid @RequestBody TaskDtos.UpdateRequest request) {
        return service.update(authentication, id, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }
}
