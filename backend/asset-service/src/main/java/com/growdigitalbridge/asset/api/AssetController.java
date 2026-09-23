package com.growdigitalbridge.asset.api;

import com.growdigitalbridge.asset.api.dto.AssetAssignmentDtos;
import com.growdigitalbridge.asset.api.dto.AssetDtos;
import com.growdigitalbridge.asset.api.dto.PageResponse;
import com.growdigitalbridge.asset.security.CurrentActor;
import com.growdigitalbridge.asset.security.CurrentCorrelation;
import com.growdigitalbridge.asset.service.AssetService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AssetController {

    private final AssetService service;

    AssetController(AssetService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/assets/me")
    List<AssetDtos.Response> mine(Authentication authentication) {
        return service.listMine(authentication);
    }

    @GetMapping("/api/v1/assets")
    PageResponse<AssetDtos.Response> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String sort) {
        return service.list(PagingSupport.of(page, size, sort, "createdAt"));
    }

    @PostMapping("/api/v1/assets")
    ResponseEntity<AssetDtos.Response> create(@Valid @RequestBody AssetDtos.CreateRequest request) {
        AssetDtos.Response created = service.create(request, CurrentActor.resolve());
        return ResponseEntity.created(URI.create("/api/v1/assets/" + created.id())).body(created);
    }

    @PostMapping("/api/v1/assets/{id}/assignments")
    ResponseEntity<AssetAssignmentDtos.Response> assign(@PathVariable UUID id, @Valid @RequestBody AssetAssignmentDtos.CreateRequest request) {
        AssetAssignmentDtos.Response created = service.assign(id, request, CurrentActor.resolve(), CurrentCorrelation.resolve());
        return ResponseEntity.created(URI.create("/api/v1/assignments/" + created.id())).body(created);
    }

    @PostMapping("/api/v1/assignments/{id}/return")
    AssetAssignmentDtos.Response returnAsset(@PathVariable UUID id, @Valid @RequestBody(required = false) AssetAssignmentDtos.ReturnRequest request) {
        AssetAssignmentDtos.ReturnRequest body = request == null ? new AssetAssignmentDtos.ReturnRequest(null) : request;
        return service.returnAsset(id, body, CurrentActor.resolve());
    }
}
