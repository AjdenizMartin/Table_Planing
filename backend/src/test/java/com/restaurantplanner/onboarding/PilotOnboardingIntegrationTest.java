package com.restaurantplanner.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantplanner.audit.domain.AuditLogRepository;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignment;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.restaurant.domain.RestaurantStatus;
import com.restaurantplanner.user.domain.User;
import com.restaurantplanner.user.domain.UserRepository;
import com.restaurantplanner.user.domain.UserStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PilotOnboardingIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PilotOnboardingService onboardingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @TempDir
    Path tempDir;

    @Test
    void createsAuditedPilotUsersAndRerunsWithoutDuplicatesOrPasswordFiles() throws Exception {
        Path ownerPassword = passwordFile("owner-password", "OwnerPilot123!");
        Path managerPassword = passwordFile("manager-password", "ManagerPilot123!");
        Path waiterPassword = passwordFile("waiter-password", "WaiterPilot123!");
        Path manifest = writeManifest(List.of(
            user("Pilot Owner", "owner@pilot.test", Role.RESTAURANT_OWNER, ownerPassword),
            user("Pilot Manager", "manager@pilot.test", Role.MANAGER, managerPassword),
            user("Pilot Staff", "staff@pilot.test", "STAFF", waiterPassword)
        ));

        PilotOnboardingService.PilotOnboardingResult first = onboardingService.onboard(manifest);

        assertThat(first.restaurantCreated()).isTrue();
        assertThat(first.createdUsers()).isEqualTo(3);
        assertThat(first.verifiedUsers()).isZero();
        assertThat(userRepository.count()).isEqualTo(3);
        assertThat(roleAssignmentRepository.count()).isEqualTo(3);
        User staff = userRepository.findByEmailIgnoreCase("staff@pilot.test").orElseThrow();
        assertThat(roleAssignmentRepository.findByUserId(staff.getId()))
            .extracting(RoleAssignment::getRole)
            .containsExactly(Role.WAITER);
        assertThat(passwordEncoder.matches(
            "ManagerPilot123!",
            userRepository.findByEmailIgnoreCase("manager@pilot.test").orElseThrow().getPasswordHash()
        )).isTrue();

        Files.delete(ownerPassword);
        Files.delete(managerPassword);
        Files.delete(waiterPassword);
        Path verificationManifest = writeManifest(List.of(
            user("Pilot Owner", "owner@pilot.test", Role.RESTAURANT_OWNER, null),
            user("Pilot Manager", "manager@pilot.test", Role.MANAGER, null),
            user("Pilot Staff", "staff@pilot.test", Role.WAITER, null)
        ));

        PilotOnboardingService.PilotOnboardingResult second = onboardingService.onboard(verificationManifest);

        assertThat(second.restaurantCreated()).isFalse();
        assertThat(second.createdUsers()).isZero();
        assertThat(second.verifiedUsers()).isEqualTo(3);
        assertThat(userRepository.count()).isEqualTo(3);
        assertThat(roleAssignmentRepository.count()).isEqualTo(3);
        assertThat(auditLogRepository.findByRestaurantIdOrderByCreatedAtDesc(
            first.restaurantId(),
            PageRequest.of(0, 20)
        )).extracting("action").contains(
            "pilot.restaurant.onboarded",
            "pilot.user.onboarded",
            "pilot.onboarding.completed"
        );
    }

    @Test
    void rollsBackRestaurantWhenAnEmailBelongsToAnotherRestaurant() throws Exception {
        Restaurant existingRestaurant = restaurant("Existing", "existing");
        User existingOwner = userRepository.save(user("Existing Owner", "owner@pilot.test", "ExistingPilot123!"));
        roleAssignmentRepository.save(assignment(existingOwner, existingRestaurant, Role.RESTAURANT_OWNER));

        Path manifest = writeManifest(List.of(
            user("Existing Owner", "owner@pilot.test", Role.RESTAURANT_OWNER, null)
        ));

        assertThatThrownBy(() -> onboardingService.onboard(manifest))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("incompatible restaurant or role");

        assertThat(restaurantRepository.findBySlugIgnoreCase("pilot-restaurant")).isEmpty();
        assertThat(restaurantRepository.count()).isEqualTo(1);
        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    void rejectsPasswordFilesThatAreNotOwnerOnly() throws Exception {
        Path password = tempDir.resolve("insecure-password");
        Files.writeString(password, "OwnerPilot123!\n");
        Files.setPosixFilePermissions(password, PosixFilePermissions.fromString("rw-r--r--"));
        Path manifest = writeManifest(List.of(
            user("Pilot Owner", "owner@pilot.test", Role.RESTAURANT_OWNER, password)
        ));

        assertThatThrownBy(() -> onboardingService.onboard(manifest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("permissions 0600");

        assertThat(restaurantRepository.count()).isZero();
        assertThat(userRepository.count()).isZero();
    }

    private Path writeManifest(List<PilotOnboardingManifest.UserInput> users) throws Exception {
        Path manifest = tempDir.resolve("manifest-" + System.nanoTime() + ".json");
        objectMapper.writeValue(manifest.toFile(), new PilotOnboardingManifest(
            new PilotOnboardingManifest.RestaurantInput(
                "Pilot Restaurant",
                "pilot-restaurant",
                "Europe/Dublin",
                "+35310000000"
            ),
            users
        ));
        return manifest.toAbsolutePath();
    }

    private PilotOnboardingManifest.UserInput user(String name, String email, Role role, Path passwordFile) {
        return user(name, email, role.name(), passwordFile);
    }

    private PilotOnboardingManifest.UserInput user(String name, String email, String role, Path passwordFile) {
        return new PilotOnboardingManifest.UserInput(
            name,
            email,
            role,
            passwordFile == null ? null : passwordFile.toAbsolutePath().toString()
        );
    }

    private Path passwordFile(String name, String password) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, password + "\n");
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        return path;
    }

    private Restaurant restaurant(String name, String slug) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(name);
        restaurant.setSlug(slug);
        restaurant.setTimezone("Europe/Dublin");
        restaurant.setStatus(RestaurantStatus.ACTIVE);
        restaurant.setSettingsJson("{}");
        return restaurantRepository.save(restaurant);
    }

    private User user(String name, String email, String password) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private RoleAssignment assignment(User user, Restaurant restaurant, Role role) {
        RoleAssignment assignment = new RoleAssignment();
        assignment.setUser(user);
        assignment.setRestaurant(restaurant);
        assignment.setRole(role);
        return assignment;
    }
}
