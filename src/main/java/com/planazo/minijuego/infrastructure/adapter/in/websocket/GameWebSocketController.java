package com.planazo.minijuego.infrastructure.adapter.in.websocket;

import com.planazo.minijuego.domain.exception.DomainException;
import com.planazo.minijuego.domain.model.Direccion;
import com.planazo.minijuego.infrastructure.adapter.in.rest.ApiError;
import com.planazo.minijuego.domain.port.in.JugarPartidaUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * SNK-04 (mover) y la parte de entrada de SNK-07 (reconexión). El
 * {@link Principal} viene resuelto por {@code StompAuthChannelInterceptor}
 * a partir del JWT del frame CONNECT — este controlador nunca confía en
 * un {@code jugadorId} que venga en el payload del mensaje, siempre usa
 * el del token.
 */
@Controller
public class GameWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketController.class);

    private final JugarPartidaUseCase jugarPartidaUseCase;
    private final SesionJuegoRegistry sesionRegistry;

    public GameWebSocketController(JugarPartidaUseCase jugarPartidaUseCase, SesionJuegoRegistry sesionRegistry) {
        this.jugarPartidaUseCase = jugarPartidaUseCase;
        this.sesionRegistry = sesionRegistry;
    }

    /**
     * El cliente manda esto justo después de conectar y suscribirse.
     * Si el jugador estaba {@code DESCONECTADA_CONGELADA} en esa sala
     * (perdió el socket y volvió dentro de la ventana de gracia), esto
     * mismo dispara SNK-07 ("reconectarme a una partida en curso") — es
     * intencional que registrar la sesión y reconectar sean la misma
     * operación desde el punto de vista del cliente.
     */
    @MessageMapping("/salas/{salaId}/registrar-sesion")
    public void registrarSesion(@DestinationVariable String salaId, Principal principal, SimpMessageHeaderAccessor accessor) {
        String jugadorId = principal.getName();
        sesionRegistry.registrar(accessor.getSessionId(), salaId, jugadorId);
        jugarPartidaUseCase.manejarReconexion(salaId, jugadorId);
        log.debug("Sesión {} registrada para jugador {} en sala {}", accessor.getSessionId(), jugadorId, salaId);
    }

    /** Un error de dominio (p.ej. moverse sin estar en la sala) vuelve solo a quien lo causó. */
    @MessageExceptionHandler(DomainException.class)
    @SendToUser(destinations = "/queue/errores", broadcast = false)
    public ApiError manejarError(DomainException ex) {
        return ApiError.de(ex.codigo(), ex.getMessage());
    }

    @MessageMapping("/salas/{salaId}/mover")
    public void mover(@DestinationVariable String salaId, MoverRequest request, Principal principal) {
        Direccion direccion = parsearDireccion(request.direccion());
        if (direccion != null) {
            jugarPartidaUseCase.moverSerpiente(salaId, principal.getName(), direccion);
        }
    }

    /** Devuelve el error solo a quien lo causó — nunca se difunde un error de un jugador a toda la sala. */
    @MessageMapping("/salas/{salaId}/ping")
    @SendToUser("/queue/pong")
    public String ping(@DestinationVariable String salaId) {
        return "pong";
    }

    private Direccion parsearDireccion(String texto) {
        try {
            return Direccion.valueOf(texto);
        } catch (Exception ex) {
            log.debug("Dirección inválida recibida por WebSocket: {}", texto);
            return null;
        }
    }
}
