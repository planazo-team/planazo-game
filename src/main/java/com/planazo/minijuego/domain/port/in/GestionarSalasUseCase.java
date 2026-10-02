package com.planazo.minijuego.domain.port.in;

import com.planazo.minijuego.domain.model.ResumenSala;

import java.util.List;

/**
 * SNK-01, SNK-02, SNK-03: ver las salas pre-existentes con su cupo, unirse
 * a una con espacio disponible, y salir de ella.
 *
 * <p>Devuelve {@link ResumenSala} (una foto inmutable), nunca el agregado
 * {@code Sala} en vivo — ver el porqué en el javadoc de {@code ResumenSala}.
 */
public interface GestionarSalasUseCase {

    List<ResumenSala> listarSalas();

    ResumenSala obtenerSala(String salaId);

    ResumenSala unirseASala(String salaId, String jugadorId, String jugadorNombre);

    void salirDeSala(String salaId, String jugadorId);
}
