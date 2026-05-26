package com.restaurantplanner.user.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"roleAssignments", "roleAssignments.restaurant"})
    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"roleAssignments", "roleAssignments.restaurant"})
    Optional<User> findWithRoleAssignmentsById(Long id);
}
