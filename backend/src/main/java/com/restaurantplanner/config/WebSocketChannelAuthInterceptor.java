package com.restaurantplanner.config;

import com.restaurantplanner.auth.security.AuthenticatedUser;
import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class WebSocketChannelAuthInterceptor implements ChannelInterceptor {

    private static final Pattern RESTAURANT_TOPIC_PATTERN = Pattern.compile("^/topic/restaurants/(\\d+)/(planning|reservations|notifications|ai)$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Object userAttribute = accessor.getSessionAttributes() == null
                ? null
                : accessor.getSessionAttributes().get(WebSocketHandshakeAuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE);
            if (!(userAttribute instanceof AuthenticatedUser authenticatedUser)) {
                throw new AccessDeniedException("WebSocket authentication is required");
            }

            accessor.setUser(toPrincipal(authenticatedUser));
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            AuthenticatedUser authenticatedUser = extractAuthenticatedUser(accessor.getUser());
            String destination = accessor.getDestination();
            Matcher matcher = destination == null ? null : RESTAURANT_TOPIC_PATTERN.matcher(destination);
            if (matcher != null && matcher.matches()) {
                Long restaurantId = Long.valueOf(matcher.group(1));
                if (!authenticatedUser.canAccessRestaurant(restaurantId)) {
                    throw new AccessDeniedException("Cannot subscribe to another restaurant");
                }
            }
        }

        return message;
    }

    private Principal toPrincipal(AuthenticatedUser authenticatedUser) {
        return new UsernamePasswordAuthenticationToken(
            authenticatedUser,
            null,
            authenticatedUser.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList()
        );
    }

    private AuthenticatedUser extractAuthenticatedUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken authentication
            && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }
        throw new AccessDeniedException("Missing authenticated WebSocket user");
    }
}
