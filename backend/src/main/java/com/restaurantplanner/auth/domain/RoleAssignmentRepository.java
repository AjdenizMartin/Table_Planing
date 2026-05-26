package com.restaurantplanner.auth.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, Long> {

    List<RoleAssignment> findByUserId(Long userId);
}

