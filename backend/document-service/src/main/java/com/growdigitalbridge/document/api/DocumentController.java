package com.growdigitalbridge.document.api;

import com.growdigitalbridge.document.api.dto.DocumentDtos;
import com.growdigitalbridge.document.security.CurrentActor;
import com.growdigitalbridge.document.security.CurrentCorrelation;
import com.growdigitalbridge.document.service.DocumentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
class DocumentController {

    private final DocumentService service;

    DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping("/uploads")
    ResponseEntity<DocumentDtos.Response> upload(Authentication authentication, @Valid @RequestBody DocumentDtos.UploadRequest request) {
        DocumentDtos.Response created = service.upload(authentication, request, CurrentActor.resolve());
        return ResponseEntity.created(URI.create("/api/v1/documents/" + created.id())).body(created);
    }

    @PostMapping("/uploads/{id}/complete")
    DocumentDtos.Response complete(@PathVariable UUID id, Authentication authentication, @Valid @RequestBody DocumentDtos.CompleteRequest request) {
        return service.complete(id, authentication, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
    }

    @GetMapping("/{id}")
    DocumentDtos.Response getById(@PathVariable UUID id, Authentication authentication) {
        return service.getById(id, authentication);
    }

    @GetMapping("/{id}/download")
    DocumentDtos.DownloadResponse download(@PathVariable UUID id, Authentication authentication) {
        return service.download(id, authentication);
    }
}
