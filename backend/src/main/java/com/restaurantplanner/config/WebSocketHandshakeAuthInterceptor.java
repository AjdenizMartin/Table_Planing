package com.restaurantplanner.config;

import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.auth.security.JwtTokenService;
import com.restaurantplanner.auth.service.AuthenticatedUserFactory;
import com.restaurantplanner.user.domain.User;
import com.restaurantplanner.user.domain.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class WebSocketHandshakeAuthInterceptor implements HandshakeInterceptor {

    static final String AUTHENTICATED_USER_ATTRIBUTE = "authenticatedUser";

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;
    private final AuthenticatedUserFactory authenticatedUserFactory;

    public WebSocketHandshakeAuthInterceptor(
        JwtTokenService jwtTokenService,
        UserRepository userRepository,
        AuthenticatedUserFactory authenticatedUserFactory
    ) {
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
        this.authenticatedUserFactory = authenticatedUserFactory;
    }

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) {
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Claims claims = jwtTokenService.parseAccessToken(token);
            Number userIdValue = claims.get("user_id", Number.class);
            Long userId = userIdValue == null ? null : userIdValue.longValue();
            if (userId == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            User user = userRepository.findWithRoleAssignmentsById(userId).orElse(null);
            if (user == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            AuthenticatedUser authenticatedUser = authenticatedUserFactory.fromUser(user);
            attributes.put(AUTHENTICATED_USER_ATTRIBUTE, authenticatedUser);
            return true;
        } catch (JwtException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception
    ) {
        // no-op
    }

    private String extractToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }

        List<String> accessTokenValues = UriComponentsBuilder.fromUri(request.getURI())
            .build()
            .getQueryParams()
            .get("access_token");
        if (accessTokenValues == null || accessTokenValues.isEmpty()) {
            return null;
        }
        return accessTokenValues.get(0);
    }
}
