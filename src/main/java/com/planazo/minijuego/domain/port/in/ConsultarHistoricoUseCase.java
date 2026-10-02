package com.planazo.minijuego.domain.port.in;

import com.planazo.minijuego.domain.model.ResultadoPartida;

import java.util.List;

/**
 * Historia Should "Ver el histórico de mis partidas". No es MVP, pero como
 * ya se persiste cada {@link ResultadoPartida} para satisfacer SNK-08 (CC-2,
 * que el puntaje no se pierda), exponer la consulta es casi gratis y ayuda
 * mucho a las pruebas funcionales por Swagger.
 */
public interface ConsultarHistoricoUseCase {

    List<ResultadoPartida> historicoDeJugador(String jugadorId);
}
