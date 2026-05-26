package com.restaurantplanner.auth.security;

import com.restaurantplanner.auth.config.JwtProperties;
import com.restaurantplanner.auth.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(AuthenticatedUser authenticatedUser) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.getAccessTokenTtl());

        return Jwts.builder()
            .subject(authenticatedUser.email())
            .issuer(jwtProperties.getIssuer())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .claim("user_id", authenticatedUser.userId())
            .claim("email", authenticatedUser.email())
            .claim("roles", rolesToStrings(authenticatedUser.roles()))
            .claim("restaurant_ids", authenticatedUser.restaurantIds())
            .signWith(secretKey, SignatureAlgorithm.HS256)
            .compact();
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public Instant accessTokenExpiresAt() {
        return Instant.now().plus(jwtProperties.getAccessTokenTtl());
    }

    public Instant refreshTokenExpiresAt() {
        return Instant.now().plus(jwtProperties.getRefreshTokenTtl());
    }

    public long accessTokenExpiresInSeconds() {
        return jwtProperties.getAccessTokenTtl().toSeconds();
    }

    private List<String> rolesToStrings(Collection<Role> roles) {
        return roles.stream()
            .map(Role::name)
            .sorted()
            .toList();
    }
}
