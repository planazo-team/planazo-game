package com.planazo.minijuego.domain.port.out;

import com.planazo.minijuego.domain.model.Direccion;
import com.planazo.minijuego.domain.model.ResumenSala;

import java.util.List;
import java.util.Map;

/**
 * Lo que la capa de aplicación necesita del estado de juego "en vivo": las
 * salas y sus partidas en curso, que viven en memoria dentro de
 * {@code infrastructure.adapter.out.runtime.SalaRuntimeRegistry} (una
 * instancia de {@code SalaRuntime}, con su propio hilo de game loop, por
 * cada sala). Es un puerto de salida igual que un repositorio — la única
 * diferencia es que lo que hay "afuera" no es una base de datos sino
 * estado en memoria con concurrencia propia.
 *
 * <p>Todo lo que devuelve son {@link ResumenSala} — fotos inmutables —
 * nunca el agregado {@code Sala} en vivo, que solo su hilo dueño puede
 * tocar. Las operaciones de unión/salida son síncronas a propósito
 * (SNK-03 necesita un rechazo inmediato y claro si la sala está llena); el
 * movimiento es asíncrono (se encola y lo aplica el hilo dueño de la sala
 * en su próximo tick) porque es de altísima frecuencia (RT-3, ~20/seg) y
 * no necesita — ni debe — bloquear al hilo que lo origina.
 */
public interface GestorDeSalasEnVivoPort {

    List<ResumenSala> listarSalas();

    ResumenSala obtenerSala(String salaId);

    ResumenSala unirse(String salaId, String jugadorId, String jugadorNombre);

    void salir(String salaId, String jugadorId);

    void encolarMovimiento(String salaId, String jugadorId, Direccion direccion);

    void marcarDesconectado(String salaId, String jugadorId);

    void marcarReconectado(String salaId, String jugadorId);

    /** Jugadores de cada sala que todavía no está en partida (espera o cuenta regresiva): salaId → jugadorIds. */
    Map<String, List<String>> jugadoresEnEspera();
}
