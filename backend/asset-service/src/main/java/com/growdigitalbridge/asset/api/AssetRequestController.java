package com.growdigitalbridge.asset.api;

import com.growdigitalbridge.asset.api.dto.AssetRequestDtos;
import com.growdigitalbridge.asset.security.CurrentActor;
import com.growdigitalbridge.asset.service.AssetRequestService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/asset-requests")
class AssetRequestController {

    private final AssetRequestService service;

    AssetRequestController(AssetRequestService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<AssetRequestDtos.Response> create(@Valid @RequestBody AssetRequestDtos.CreateRequest request,
                                                       Authentication authentication) {
        AssetRequestDtos.Response created = service.create(request, authentication, CurrentActor.resolve());
        return ResponseEntity.created(URI.create("/api/v1/asset-requests/" + created.id())).body(created);
    }
}
