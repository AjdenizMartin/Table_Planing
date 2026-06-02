package com.restaurantplanner.auth.service;

import com.restaurantplanner.audit.AuditService;
import com.restaurantplanner.auth.api.AuthMapper;
import com.restaurantplanner.auth.api.AuthResponse;
import com.restaurantplanner.auth.api.LoginRequest;
import com.restaurantplanner.auth.api.MeResponse;
import com.restaurantplanner.auth.api.RefreshRequest;
import com.restaurantplanner.auth.api.RegisterRequest;
import com.restaurantplanner.auth.domain.RefreshToken;
import com.restaurantplanner.auth.domain.RefreshTokenRepository;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignment;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.auth.security.JwtTokenService;
import com.restaurantplanner.common.api.ConflictException;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.restaurant.domain.RestaurantStatus;
import com.restaurantplanner.user.domain.User;
import com.restaurantplanner.user.domain.UserRepository;
import com.restaurantplanner.user.domain.UserStatus;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthenticatedUserFactory authenticatedUserFactory;
    private final RestaurantAccessService restaurantAccessService;
    private final AuthMapper authMapper;
    private final RestaurantRepository restaurantRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final AuditService auditService;

    public AuthService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenService jwtTokenService,
        AuthenticatedUserFactory authenticatedUserFactory,
        RestaurantAccessService restaurantAccessService,
        AuthMapper authMapper,
        RestaurantRepository restaurantRepository,
        RoleAssignmentRepository roleAssignmentRepository,
        AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.authenticatedUserFactory = authenticatedUserFactory;
        this.restaurantAccessService = restaurantAccessService;
        this.authMapper = authMapper;
        this.restaurantRepository = restaurantRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
            .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        user.setLastLoginAt(Instant.now());

        return buildAuthResponse(user, createRefreshToken(user));
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new ConflictException("Email already registered");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        String slug = generateSlug(request.restaurantName());
        Restaurant restaurant = new Restaurant();
        restaurant.setName(request.restaurantName().trim());
        restaurant.setSlug(slug);
        restaurant.setTimezone("UTC");
        restaurant.setStatus(RestaurantStatus.ACTIVE);
        restaurant.setSettingsJson("{}");
        restaurantRepository.save(restaurant);

        RoleAssignment ownerAssignment = new RoleAssignment();
        ownerAssignment.setUser(user);
        ownerAssignment.setRestaurant(restaurant);
        ownerAssignment.setRole(Role.RESTAURANT_OWNER);
        roleAssignmentRepository.save(ownerAssignment);
        user.getRoleAssignments().add(ownerAssignment);

        auditService.record(restaurant.getId(), "Restaurant", restaurant.getId(), "restaurant.created", user.getId(), "{\"name\":\"" + request.restaurantName().replace("\"", "'") + "\"}");
        auditService.record(restaurant.getId(), "User", user.getId(), "user.registered", user.getId(), "{\"email\":\"" + normalizedEmail + "\"}");

        return buildAuthResponse(user, createRefreshToken(user));
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
            .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        Instant now = Instant.now();
        if (!refreshToken.isActive(now)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        refreshToken.setRevokedAt(now);
        User user = userRepository.findWithRoleAssignmentsById(refreshToken.getUser().getId())
            .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        return buildAuthResponse(user, createRefreshToken(user));
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(refreshToken -> {
            if (refreshToken.getRevokedAt() == null) {
                refreshToken.setRevokedAt(Instant.now());
            }
        });
    }

    @Transactional(readOnly = true)
    public MeResponse me(AuthenticatedUser authenticatedUser, Long requestedRestaurantId) {
        User user = userRepository.findWithRoleAssignmentsById(authenticatedUser.userId())
            .orElseThrow(() -> new AccessDeniedException("Authenticated user no longer exists"));

        restaurantAccessService.assertCanAccessRestaurant(authenticatedUser, requestedRestaurantId);
        return authMapper.toMeResponse(user, requestedRestaurantId);
    }

    private AuthResponse buildAuthResponse(User user, RefreshToken refreshToken) {
        AuthenticatedUser authenticatedUser = authenticatedUserFactory.fromUser(user);
        String accessToken = jwtTokenService.generateAccessToken(authenticatedUser);
        return authMapper.toAuthResponse(
            user,
            accessToken,
            refreshToken.getToken(),
            jwtTokenService.accessTokenExpiresInSeconds()
        );
    }

    private RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(jwtTokenService.refreshTokenExpiresAt());
        return refreshTokenRepository.save(refreshToken);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateSlug(String restaurantName) {
        String base = restaurantName.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        if (base.isEmpty()) {
            base = "restaurant";
        }
        String slug = base;
        while (restaurantRepository.existsBySlugIgnoreCase(slug)) {
            String suffix = UUID.randomUUID().toString().substring(0, 4);
            slug = base + "-" + suffix;
        }
        return slug;
    }
}
