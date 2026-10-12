package com.planazo.minijuego.infrastructure.config;

import com.planazo.minijuego.infrastructure.security.StompAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * RT-3: tiempo real del Minijuego sobre STOMP. {@code /topic/salas/{id}}
 * es donde se difunde el snapshot de cada tick (broadcast, muchos
 * suscriptores); {@code /app/salas/{id}/...} es donde el cliente manda
 * sus propios comandos (mover, registrar su sesión). SockJS es el
 * fallback para navegadores o redes que bloqueen WebSocket nativo — el
 * cliente de prueba (`test-client.html`) lo usa por eso, no porque haga
 * falta en general.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketStompConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final List<String> origenesPermitidos;

    public WebSocketStompConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor,
                                @Value("${planazo.cors.origenes}") List<String> origenesPermitidos) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.origenesPermitidos = origenesPermitidos;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // Los mismos orígenes que el CORS de la API (planazo.cors.origenes).
                .setAllowedOriginPatterns(origenesPermitidos.stream().map(String::trim).filter(o -> !o.isEmpty()).toArray(String[]::new))
                .withSockJS();
        // Procesa los frames de cada cliente en el orden en que llegaron: sin esto,
        // "suscribirse" y "registrar-sesion" pueden procesarse al revés y el
        // cliente se pierde el primer estado de la sala.
        registry.setPreserveReceiveOrder(true);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
