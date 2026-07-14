package com.restaurantplanner.storage.api;

import com.restaurantplanner.storage.domain.StorageResource;
import org.springframework.stereotype.Component;

@Component
public class StorageResourceMapper {

    public StorageResourceResponse toResponse(StorageResource resource) {
        return new StorageResourceResponse(
            resource.getId(),
            resource.getRestaurant().getId(),
            resource.getResourceType().name(),
            resource.getName(),
            resource.getQuantity(),
            resource.getCapacityPerUnit(),
            resource.getSetupTimeMinutes(),
            resource.isActive(),
            resource.getNotes(),
            resource.getCreatedAt(),
            resource.getUpdatedAt()
        );
    }
}
