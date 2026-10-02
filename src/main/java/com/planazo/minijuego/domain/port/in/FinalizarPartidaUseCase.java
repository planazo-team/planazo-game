package com.planazo.minijuego.domain.port.in;

import com.planazo.minijuego.domain.model.ResultadoPartida;

/**
 * Se dispara cuando el game loop ({@code SalaRuntime}) detecta que una
 * partida terminó (SNK-09). No lo invoca un humano directamente, lo invoca
 * infraestructura — pero sigue siendo un caso de uso del dominio: "cuando
 * una partida termina, esto es lo que tiene que pasar" (persistir,
 * actualizar leaderboard, publicar el evento) es una regla de negocio, no
 * un detalle técnico, así que vive detrás de una interfaz igual que los
 * demás casos de uso.
 */
public interface FinalizarPartidaUseCase {

    void finalizarPartida(ResultadoPartida resultado);
}
