package com.planazo.minijuego.infrastructure.adapter.in.websocket;

import com.planazo.minijuego.domain.port.in.JugarPartidaUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * SNK-07: cuando el socket se cae — cierre de pestaña, pérdida de red,
 * lo que sea — esto es lo único que se entera. Traduce ese evento de
 * bajo nivel de Spring en la operación de dominio que corresponde
 * ("congelar a este jugador en esta sala"), usando
 * {@link SesionJuegoRegistry} para saber cuál era su sala.
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final SesionJuegoRegistry sesionRegistry;
    private final JugarPartidaUseCase jugarPartidaUseCase;

    public WebSocketEventListener(SesionJuegoRegistry sesionRegistry, JugarPartidaUseCase jugarPartidaUseCase) {
        this.sesionRegistry = sesionRegistry;
        this.jugarPartidaUseCase = jugarPartidaUseCase;
    }

    @EventListener
    public void alDesconectar(SessionDisconnectEvent event) {
        SesionJuegoRegistry.SesionInfo info = sesionRegistry.remover(event.getSessionId());
        if (info == null || sesionRegistry.tieneOtraSesionEn(info.salaId(), info.jugadorId())) {
            return;
        }
        try {
            jugarPartidaUseCase.manejarDesconexion(info.salaId(), info.jugadorId());
            log.debug("Jugador {} congelado en sala {} por desconexión de WebSocket.", info.jugadorId(), info.salaId());
        } catch (Exception ex) {
            log.warn("No se pudo procesar la desconexión del jugador {} en sala {}: {}",
                    info.jugadorId(), info.salaId(), ex.getMessage());
        }
    }
}
