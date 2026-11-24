package com.horsecontrol.eloquestre.config;

import com.horsecontrol.eloquestre.realtime.ChatWebSocketHandler;
import com.horsecontrol.eloquestre.realtime.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** WebSocket do chat em /ws?token=<jwt>. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler handler;
    private final WebSocketAuthInterceptor auth;

    public WebSocketConfig(ChatWebSocketHandler handler, WebSocketAuthInterceptor auth) {
        this.handler = handler;
        this.auth = auth;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Qualquer origem: a autenticação é o JWT (não há cookie), e o app nativo
        // envia como Origin o próprio endereço da API.
        registry.addHandler(handler, "/ws").addInterceptors(auth).setAllowedOriginPatterns("*");
    }
}
