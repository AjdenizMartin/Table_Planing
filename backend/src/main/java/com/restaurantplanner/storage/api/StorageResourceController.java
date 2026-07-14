package com.restaurantplanner.storage.api;

import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.storage.service.StorageResourceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/storage-resources")
public class StorageResourceController {

    private final StorageResourceService storageResourceService;

    public StorageResourceController(StorageResourceService storageResourceService) {
        this.storageResourceService = storageResourceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StorageResourceResponse create(
        @PathVariable Long restaurantId,
        @Valid @RequestBody CreateStorageResourceRequest request,
        Authentication authentication
    ) {
        return storageResourceService.create(restaurantId, request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping
    public List<StorageResourceResponse> findAll(
        @PathVariable Long restaurantId,
        @RequestParam(required = false) String resourceType,
        @RequestParam(required = false) Boolean active,
        Authentication authentication
    ) {
        return storageResourceService.findAll(
            restaurantId,
            resourceType,
            active,
            (AuthenticatedUser) authentication.getPrincipal()
        );
    }

    @GetMapping("/{resourceId}")
    public StorageResourceResponse findById(
        @PathVariable Long restaurantId,
        @PathVariable Long resourceId,
        Authentication authentication
    ) {
        return storageResourceService.findById(restaurantId, resourceId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PatchMapping("/{resourceId}")
    public StorageResourceResponse update(
        @PathVariable Long restaurantId,
        @PathVariable Long resourceId,
        @Valid @RequestBody UpdateStorageResourceRequest request,
        Authentication authentication
    ) {
        return storageResourceService.update(restaurantId, resourceId, request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @DeleteMapping("/{resourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable Long restaurantId,
        @PathVariable Long resourceId,
        Authentication authentication
    ) {
        storageResourceService.delete(restaurantId, resourceId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PostMapping("/{resourceId}/availability-check")
    public StorageAvailabilityResponse checkAvailability(
        @PathVariable Long restaurantId,
        @PathVariable Long resourceId,
        @Valid @RequestBody StorageAvailabilityRequest request,
        Authentication authentication
    ) {
        return storageResourceService.checkAvailability(
            restaurantId,
            resourceId,
            request.requestedQuantity(),
            (AuthenticatedUser) authentication.getPrincipal()
        );
    }
}
