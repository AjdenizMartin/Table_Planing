package com.restaurantplanner.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketHandshakeAuthInterceptor webSocketHandshakeAuthInterceptor;
    private final WebSocketChannelAuthInterceptor webSocketChannelAuthInterceptor;

    public WebSocketConfig(
        WebSocketHandshakeAuthInterceptor webSocketHandshakeAuthInterceptor,
        WebSocketChannelAuthInterceptor webSocketChannelAuthInterceptor
    ) {
        this.webSocketHandshakeAuthInterceptor = webSocketHandshakeAuthInterceptor;
        this.webSocketChannelAuthInterceptor = webSocketChannelAuthInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .addInterceptors(webSocketHandshakeAuthInterceptor);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketChannelAuthInterceptor);
    }
}
