package com.restaurantplanner.onboarding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantplanner.audit.domain.AuditLog;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PilotOnboardingService {

    private static final Set<Role> PILOT_ROLES = EnumSet.of(
        Role.RESTAURANT_OWNER,
        Role.MANAGER,
        Role.WAITER
    );
    private static final Set<PosixFilePermission> REQUIRED_PASSWORD_PERMISSIONS =
        PosixFilePermissions.fromString("rw-------");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final AuditLogRepository auditLogRepository;

    public PilotOnboardingService(
        ObjectMapper objectMapper,
        PasswordEncoder passwordEncoder,
        RestaurantRepository restaurantRepository,
        UserRepository userRepository,
        RoleAssignmentRepository roleAssignmentRepository,
        AuditLogRepository auditLogRepository
    ) {
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.restaurantRepository = restaurantRepository;
        this.userRepository = userRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public PilotOnboardingResult onboard(Path manifestPath) {
        NormalizedManifest manifest = normalize(readManifest(manifestPath));
        RestaurantResolution restaurantResolution = resolveRestaurant(manifest.restaurant());
        Restaurant restaurant = restaurantResolution.restaurant();

        Map<String, UserResolution> usersByEmail = new LinkedHashMap<>();
        for (NormalizedUser requestedUser : manifest.users()) {
            usersByEmail.put(requestedUser.email(), resolveUser(requestedUser, restaurant));
        }

        User owner = usersByEmail.values().stream()
            .filter(resolution -> resolution.role() == Role.RESTAURANT_OWNER)
            .map(UserResolution::user)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Pilot onboarding requires an owner"));

        List<AuditLog> auditLogs = new ArrayList<>();
        if (restaurantResolution.created()) {
            auditLogs.add(new AuditLog(
                restaurant.getId(),
                "Restaurant",
                restaurant.getId(),
                "pilot.restaurant.onboarded",
                owner.getId(),
                metadata(Map.of("slug", restaurant.getSlug(), "source", "pilot-onboarding"))
            ));
        }

        int createdUsers = 0;
        for (UserResolution resolution : usersByEmail.values()) {
            if (!resolution.created()) {
                continue;
            }
            createdUsers++;
            auditLogs.add(new AuditLog(
                restaurant.getId(),
                "User",
                resolution.user().getId(),
                "pilot.user.onboarded",
                owner.getId(),
                metadata(Map.of(
                    "email", resolution.user().getEmail(),
                    "role", resolution.role().name(),
                    "source", "pilot-onboarding"
                ))
            ));
        }

        auditLogs.add(new AuditLog(
            restaurant.getId(),
            "Restaurant",
            restaurant.getId(),
            "pilot.onboarding.completed",
            owner.getId(),
            metadata(Map.of(
                "createdUsers", createdUsers,
                "verifiedUsers", usersByEmail.size() - createdUsers,
                "source", "pilot-onboarding"
            ))
        ));
        auditLogRepository.saveAll(auditLogs);

        return new PilotOnboardingResult(
            restaurant.getId(),
            restaurantResolution.created(),
            createdUsers,
            usersByEmail.size() - createdUsers
        );
    }

    private PilotOnboardingManifest readManifest(Path manifestPath) {
        if (manifestPath == null || !manifestPath.isAbsolute()) {
            throw new IllegalArgumentException("Onboarding manifest path must be absolute");
        }
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(manifestPath)) {
            throw new IllegalArgumentException("Onboarding manifest is not a readable regular file: " + manifestPath);
        }
        try {
            return objectMapper.readValue(manifestPath.toFile(), PilotOnboardingManifest.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Onboarding manifest is not valid JSON", exception);
        }
    }

    private NormalizedManifest normalize(PilotOnboardingManifest manifest) {
        if (manifest == null || manifest.restaurant() == null) {
            throw new IllegalArgumentException("Onboarding manifest requires restaurant details");
        }
        if (manifest.users() == null || manifest.users().isEmpty()) {
            throw new IllegalArgumentException("Onboarding manifest requires at least one user");
        }

        PilotOnboardingManifest.RestaurantInput input = manifest.restaurant();
        String name = requiredText(input.name(), "restaurant.name", 160);
        String slug = requiredText(input.slug(), "restaurant.slug", 160).toLowerCase(Locale.ROOT);
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("restaurant.slug must use lowercase letters, numbers and hyphens");
        }
        String timezone = requiredText(input.timezone(), "restaurant.timezone", 80);
        try {
            timezone = ZoneId.of(timezone).getId();
        } catch (ZoneRulesException exception) {
            throw new IllegalArgumentException("restaurant.timezone is invalid: " + timezone, exception);
        }
        String phone = optionalText(input.phone(), "restaurant.phone", 40);

        List<NormalizedUser> users = new ArrayList<>();
        Set<String> emails = new HashSet<>();
        int ownerCount = 0;
        for (PilotOnboardingManifest.UserInput user : manifest.users()) {
            if (user == null) {
                throw new IllegalArgumentException("Onboarding users cannot contain null entries");
            }
            String userName = requiredText(user.name(), "user.name", 160);
            String email = requiredText(user.email(), "user.email", 255).toLowerCase(Locale.ROOT);
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new IllegalArgumentException("Invalid onboarding email: " + email);
            }
            if (!emails.add(email)) {
                throw new IllegalArgumentException("Duplicate onboarding email: " + email);
            }

            Role role = parseRole(user.role());
            if (role == Role.RESTAURANT_OWNER) {
                ownerCount++;
            }
            Path passwordFile = optionalPasswordPath(user.passwordFile(), email);
            users.add(new NormalizedUser(userName, email, role, passwordFile));
        }
        if (ownerCount != 1) {
            throw new IllegalArgumentException("Onboarding manifest must contain exactly one RESTAURANT_OWNER");
        }

        return new NormalizedManifest(new NormalizedRestaurant(name, slug, timezone, phone), List.copyOf(users));
    }

    private RestaurantResolution resolveRestaurant(NormalizedRestaurant requested) {
        return restaurantRepository.findBySlugIgnoreCase(requested.slug())
            .map(existing -> {
                if (!Objects.equals(existing.getName(), requested.name())
                    || !Objects.equals(existing.getTimezone(), requested.timezone())
                    || !Objects.equals(normalizeNullable(existing.getPhone()), requested.phone())
                    || existing.getStatus() != RestaurantStatus.ACTIVE) {
                    throw new IllegalStateException(
                        "Restaurant slug already exists with incompatible name, timezone, phone or status: "
                            + requested.slug()
                    );
                }
                return new RestaurantResolution(existing, false);
            })
            .orElseGet(() -> {
                Restaurant restaurant = new Restaurant();
                restaurant.setName(requested.name());
                restaurant.setSlug(requested.slug());
                restaurant.setTimezone(requested.timezone());
                restaurant.setPhone(requested.phone());
                restaurant.setStatus(RestaurantStatus.ACTIVE);
                restaurant.setSettingsJson("{}");
                return new RestaurantResolution(restaurantRepository.save(restaurant), true);
            });
    }

    private UserResolution resolveUser(NormalizedUser requested, Restaurant restaurant) {
        return userRepository.findByEmailIgnoreCase(requested.email())
            .map(existing -> verifyExistingUser(existing, requested, restaurant))
            .orElseGet(() -> createUser(requested, restaurant));
    }

    private UserResolution verifyExistingUser(User user, NormalizedUser requested, Restaurant restaurant) {
        if (!Objects.equals(user.getName(), requested.name()) || user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("User already exists with incompatible name or status: " + requested.email());
        }

        List<RoleAssignment> assignments = user.getRoleAssignments();
        boolean exactAssignment = assignments.size() == 1
            && Objects.equals(assignments.get(0).getRestaurant().getId(), restaurant.getId())
            && assignments.get(0).getRole() == requested.role();
        if (!exactAssignment) {
            throw new IllegalStateException("User already exists with incompatible restaurant or role: " + requested.email());
        }
        return new UserResolution(user, requested.role(), false);
    }

    private UserResolution createUser(NormalizedUser requested, Restaurant restaurant) {
        if (requested.passwordFile() == null) {
            throw new IllegalArgumentException("New onboarding user requires passwordFile: " + requested.email());
        }
        String password = readPassword(requested.passwordFile(), requested.email());

        User user = new User();
        user.setName(requested.name());
        user.setEmail(requested.email());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        RoleAssignment assignment = new RoleAssignment();
        assignment.setUser(user);
        assignment.setRestaurant(restaurant);
        assignment.setRole(requested.role());
        roleAssignmentRepository.save(assignment);
        user.getRoleAssignments().add(assignment);
        return new UserResolution(user, requested.role(), true);
    }

    private Path optionalPasswordPath(String value, String email) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Path path = Path.of(value.trim());
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("passwordFile must be absolute for user: " + email);
        }
        return path;
    }

    private String readPassword(Path path, String email) {
        if (Files.isSymbolicLink(path)
            || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
            || !Files.isReadable(path)) {
            throw new IllegalArgumentException("Password file is not a readable regular file for user: " + email);
        }
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            if (!permissions.equals(REQUIRED_PASSWORD_PERMISSIONS)) {
                throw new IllegalArgumentException("Password file must have permissions 0600 for user: " + email);
            }
            if (Files.size(path) > 1024) {
                throw new IllegalArgumentException("Password file is too large for user: " + email);
            }
            String password = Files.readString(path, StandardCharsets.UTF_8);
            if (password.endsWith("\r\n")) {
                password = password.substring(0, password.length() - 2);
            } else if (password.endsWith("\n")) {
                password = password.substring(0, password.length() - 1);
            }
            validatePassword(password, email);
            return password;
        } catch (UnsupportedOperationException exception) {
            throw new IllegalArgumentException("Password file permissions cannot be verified for user: " + email, exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Password file cannot be read for user: " + email, exception);
        }
    }

    private void validatePassword(String password, String email) {
        boolean strong = password.length() >= 12
            && password.length() <= 128
            && password.chars().anyMatch(Character::isUpperCase)
            && password.chars().anyMatch(Character::isLowerCase)
            && password.chars().anyMatch(Character::isDigit)
            && password.chars().anyMatch(character -> !Character.isLetterOrDigit(character));
        if (!strong || password.contains("\n") || password.contains("\r")) {
            throw new IllegalArgumentException(
                "Password must be 12-128 characters with upper, lower, number and symbol for user: " + email
            );
        }
    }

    private Role parseRole(String value) {
        String normalized = requiredText(value, "user.role", 32).toUpperCase(Locale.ROOT);
        if ("STAFF".equals(normalized)) {
            return Role.WAITER;
        }
        try {
            Role role = Role.valueOf(normalized);
            if (!PILOT_ROLES.contains(role)) {
                throw new IllegalArgumentException("Unsupported pilot role: " + normalized);
            }
            return role;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported pilot role: " + normalized, exception);
        }
    }

    private String requiredText(String value, String field, int maxLength) {
        String normalized = optionalText(value, field, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String metadata(Map<String, ?> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize onboarding audit metadata", exception);
        }
    }

    public record PilotOnboardingResult(
        Long restaurantId,
        boolean restaurantCreated,
        int createdUsers,
        int verifiedUsers
    ) {
    }

    private record NormalizedManifest(NormalizedRestaurant restaurant, List<NormalizedUser> users) {
    }

    private record NormalizedRestaurant(String name, String slug, String timezone, String phone) {
    }

    private record NormalizedUser(String name, String email, Role role, Path passwordFile) {
    }

    private record RestaurantResolution(Restaurant restaurant, boolean created) {
    }

    private record UserResolution(User user, Role role, boolean created) {
    }
}
