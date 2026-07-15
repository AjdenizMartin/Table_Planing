package com.restaurantplanner.optimization.domain;

import com.restaurantplanner.storage.domain.StorageResource;

public record CandidateResourceRequirement(StorageResource resource, int quantity) {

    public int capacityContribution() {
        return quantity * resource.getCapacityPerUnit();
    }
}
