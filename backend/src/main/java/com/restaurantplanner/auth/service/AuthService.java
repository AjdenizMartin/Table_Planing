package com.restaurantplanner.auth.service;

import com.restaurantplanner.auth.api.AuthMapper;
import com.restaurantplanner.auth.api.AuthResponse;
import com.restaurantplanner.auth.api.LoginRequest;
import com.restaurantplanner.auth.api.MeResponse;
import com.restaurantplanner.auth.api.RefreshRequest;
import com.restaurantplanner.auth.domain.RefreshToken;
import com.restaurantplanner.auth.domain.RefreshTokenRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.auth.security.JwtTokenService;
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

    public AuthService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenService jwtTokenService,
        AuthenticatedUserFactory authenticatedUserFactory,
        RestaurantAccessService restaurantAccessService,
        AuthMapper authMapper
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.authenticatedUserFactory = authenticatedUserFactory;
        this.restaurantAccessService = restaurantAccessService;
        this.authMapper = authMapper;
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
}
