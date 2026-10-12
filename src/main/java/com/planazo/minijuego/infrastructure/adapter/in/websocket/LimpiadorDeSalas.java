package com.planazo.minijuego.infrastructure.adapter.in.websocket;

import com.planazo.minijuego.domain.port.in.JugarPartidaUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saca de las salas a quien se fue sin avisar. {@link WebSocketEventListener}
 * se entera cuando un socket registrado se cae, pero hay casos que nunca
 * llegan ahí: alguien entra por REST ({@code POST /salas/{id}/jugadores}) y
 * cierra la app antes de registrar su sesión STOMP, o la registra en otra
 * sala. Ese jugador quedaba ocupando el cupo para siempre ("Esperando
 * jugadores 1/2").
 *
 * <p>Cada pocos segundos revisa las salas que aún no arrancan: si un jugador
 * lleva más de {@code planazo.salas.segundos-sin-conexion} sin ninguna sesión
 * de juego en esa sala, sale. Durante la partida no se toca a nadie: ahí
 * manda la congelación por desconexión (SNK-07).
 */
@Component
public class LimpiadorDeSalas {

    private static final Logger log = LoggerFactory.getLogger(LimpiadorDeSalas.class);

    private final JugarPartidaUseCase jugarPartidaUseCase;
    private final SesionJuegoRegistry sesiones;
    private final long graciaMs;
    /** "salaId|jugadorId" → desde cuándo está en la sala sin sesión. */
    private final Map<String, Long> sinSesionDesde = new ConcurrentHashMap<>();

    public LimpiadorDeSalas(JugarPartidaUseCase jugarPartidaUseCase,
                            SesionJuegoRegistry sesiones,
                            @Value("${planazo.salas.segundos-sin-conexion:20}") long segundosSinConexion) {
        this.jugarPartidaUseCase = jugarPartidaUseCase;
        this.sesiones = sesiones;
        this.graciaMs = segundosSinConexion * 1000;
    }

    @Scheduled(fixedDelayString = "${planazo.salas.revision-ms:5000}", initialDelayString = "${planazo.salas.revision-ms:5000}")
    public void revisar() {
        revisar(System.currentTimeMillis());
    }

    /** Separado del reloj para poder probarlo. */
    void revisar(long ahora) {
        Set<String> vistos = new HashSet<>();
        // Copias: retirar a alguien cambia las salas mientras se recorren.
        for (Map.Entry<String, List<String>> sala : Map.copyOf(jugarPartidaUseCase.jugadoresEnSalaDeEspera()).entrySet()) {
            for (String jugadorId : List.copyOf(sala.getValue())) {
                String clave = sala.getKey() + "|" + jugadorId;
                if (sesiones.tieneOtraSesionEn(sala.getKey(), jugadorId)) {
                    sinSesionDesde.remove(clave);
                    continue;
                }
                vistos.add(clave);
                long desde = sinSesionDesde.computeIfAbsent(clave, k -> ahora);
                if (ahora - desde >= graciaMs) {
                    sinSesionDesde.remove(clave);
                    try {
                        jugarPartidaUseCase.retirarJugadorInactivo(sala.getKey(), jugadorId);
                        log.info("Jugador {} retirado de la sala {}: {} s sin conexión de juego.", jugadorId, sala.getKey(), graciaMs / 1000);
                    } catch (Exception ex) {
                        log.debug("No se pudo retirar a {} de {}: {}", jugadorId, sala.getKey(), ex.getMessage());
                    }
                }
            }
        }
        // Quien ya no está en una sala de espera (salió, arrancó la partida) deja de contarse.
        sinSesionDesde.keySet().retainAll(vistos);
    }
}
