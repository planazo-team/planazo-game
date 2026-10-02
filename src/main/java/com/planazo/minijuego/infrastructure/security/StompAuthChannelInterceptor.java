package com.planazo.minijuego.infrastructure.security;

import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * El handshake HTTP inicial de SockJS (`/ws`) no protege el resto de la
 * sesión de WebSocket: una vez abierto el socket, cualquiera podría
 * mandar frames STOMP. Por eso el JWT se valida de nuevo aquí, en el
 * frame {@code CONNECT}, exactamente como exige el reto RT-3 combinado
 * con la seguridad stateless del servicio. Si el token falta o es
 * inválido, se lanza una excepción que cierra la conexión — nunca se deja
 * pasar un CONNECT sin autenticar.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extraerToken(accessor);
            Jwt jwt = jwtDecoder.decode(token);
            Principal principal = new JugadorPrincipal(jwt.getSubject(), jwt.getClaimAsString("nombre"));
            accessor.setUser(principal);
        }
        return message;
    }

    private String extraerToken(StompHeaderAccessor accessor) {
        List<String> autorizacion = accessor.getNativeHeader("Authorization");
        String valor = (autorizacion == null || autorizacion.isEmpty()) ? null : autorizacion.get(0);
        if (valor == null) {
            List<String> tokenHeader = accessor.getNativeHeader("token");
            valor = (tokenHeader == null || tokenHeader.isEmpty()) ? null : tokenHeader.get(0);
        }
        if (valor == null) {
            throw new IllegalArgumentException("Falta el JWT en el frame CONNECT (header 'Authorization: Bearer <token>' o 'token').");
        }
        return valor.startsWith("Bearer ") ? valor.substring("Bearer ".length()) : valor;
    }

    /** Principal mínimo: el id del jugador como nombre, su nombre para mostrar como detalle. */
    public record JugadorPrincipal(String jugadorId, String nombre) implements Principal {
        @Override
        public String getName() {
            return jugadorId;
        }
    }
}
