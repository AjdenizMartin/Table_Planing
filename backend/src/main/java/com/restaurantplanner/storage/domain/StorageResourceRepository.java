package com.restaurantplanner.storage.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageResourceRepository extends JpaRepository<StorageResource, Long> {

    List<StorageResource> findByRestaurantIdOrderByResourceTypeAscNameAscIdAsc(Long restaurantId);

    List<StorageResource> findByRestaurantIdAndResourceTypeOrderByResourceTypeAscNameAscIdAsc(
        Long restaurantId,
        StorageResourceType resourceType
    );

    List<StorageResource> findByRestaurantIdAndActiveOrderByResourceTypeAscNameAscIdAsc(
        Long restaurantId,
        boolean active
    );

    List<StorageResource> findByRestaurantIdAndResourceTypeAndActiveOrderByResourceTypeAscNameAscIdAsc(
        Long restaurantId,
        StorageResourceType resourceType,
        boolean active
    );

    Optional<StorageResource> findByIdAndRestaurantId(Long id, Long restaurantId);
}
