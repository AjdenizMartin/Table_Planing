package com.restaurantplanner.storage.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

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

    List<StorageResource> findByRestaurantIdAndIdIn(Long restaurantId, List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select resource from StorageResource resource where resource.id = :id and resource.restaurant.id = :restaurantId")
    Optional<StorageResource> findByIdAndRestaurantIdForUpdate(
        @Param("id") Long id,
        @Param("restaurantId") Long restaurantId
    );
}
