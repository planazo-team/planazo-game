package com.planazo.minijuego.infrastructure.adapter.in.websocket;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asocia cada sesión de WebSocket (un {@code sessionId} de STOMP) con la
 * sala y el jugador a los que representa. Es la única pieza de estado
 * compartido de este paquete — por eso usa una {@code ConcurrentHashMap},
 * siguiendo la misma regla que {@code ZonaSubscriptionRegistry} en
 * {@code mapa-descubrimiento-service}: si el estado compartido es
 * inevitable, se usa una colección concurrente, nunca un mapa normal con
 * {@code synchronized} a mano.
 *
 * <p>Sin este registro, {@link WebSocketEventListener} no tendría forma
 * de saber, cuando un socket se cae, a qué sala y jugador avisarle
 * "esta serpiente se desconectó" (SNK-07) — {@code SessionDisconnectEvent}
 * solo trae el {@code sessionId}.
 */
@Component
public class SesionJuegoRegistry {

    private final Map<String, SesionInfo> sesiones = new ConcurrentHashMap<>();

    public void registrar(String sessionId, String salaId, String jugadorId) {
        sesiones.put(sessionId, new SesionInfo(salaId, jugadorId));
    }

    public SesionInfo remover(String sessionId) {
        return sesiones.remove(sessionId);
    }

    /**
     * El mismo jugador puede tener el juego abierto en dos pestañas: si se
     * cierra una pero la otra sigue conectada a la misma sala, el jugador
     * no se desconectó de verdad.
     */
    public boolean tieneOtraSesionEn(String salaId, String jugadorId) {
        return sesiones.values().stream()
                .anyMatch(s -> s.salaId().equals(salaId) && s.jugadorId().equals(jugadorId));
    }

    public SesionInfo obtener(String sessionId) {
        return sesiones.get(sessionId);
    }

    public record SesionInfo(String salaId, String jugadorId) {
    }
}
