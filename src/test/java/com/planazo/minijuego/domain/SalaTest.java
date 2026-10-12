package com.planazo.minijuego.domain;

import com.planazo.minijuego.domain.exception.JugadorYaEnSalaException;
import com.planazo.minijuego.domain.exception.PartidaYaIniciadaException;
import com.planazo.minijuego.domain.exception.SalaLlenaException;
import com.planazo.minijuego.domain.model.Direccion;
import com.planazo.minijuego.domain.model.EstadoSala;
import com.planazo.minijuego.domain.model.EstadoSerpiente;
import com.planazo.minijuego.domain.model.MotivoVictoria;
import com.planazo.minijuego.domain.model.Posicion;
import com.planazo.minijuego.domain.model.ResultadoPartida;
import com.planazo.minijuego.domain.model.Sala;
import com.planazo.minijuego.domain.model.Serpiente;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas puras de dominio — sin Spring, sin base de datos, sin red. Es
 * a propósito: la regla de negocio más importante de este microservicio
 * (cómo se juega una partida de Snake) tiene que poder probarse en
 * milisegundos, sin levantar nada. Esto es lo que hace que
 * {@code Sala} sea el "núcleo" de la arquitectura hexagonal y no un
 * detalle técnico más.
 *
 * <p>Cubre las historias MVP SNK-01, SNK-03, SNK-04, SNK-05, SNK-06,
 * SNK-07, SNK-08 y SNK-09/SNK-10 (condición de victoria híbrida, con sus
 * dos ramas: último en pie y mayor puntaje por tiempo).
 *
 * <p>Puntos de aparición usados abajo: j1 nace en (4,2) mirando a la
 * DERECHA con el cuerpo en x 1..4; j2 en (19,15) mirando a la IZQUIERDA.
 * Snake clásico: las serpientes avanzan solas cada
 * {@link Sala#MILISEGUNDOS_POR_PASO} ms del reloj mutable.
 */
class SalaTest {

    private final MutableClock reloj = MutableClock.en(Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void laPartidaNoArranca_hastaQueLaSalaEstaLlena() { // SNK-05
        Sala sala = nuevaSala(4);
        sala.unirse("j1", "Ana");
        sala.unirse("j2", "Beto");
        sala.unirse("j3", "Camila");
        reloj.avanzar(Duration.ofMinutes(5));
        sala.tick();
        assertThat(sala.estado()).isEqualTo(EstadoSala.ESPERANDO_JUGADORES);

        sala.unirse("j4", "Diego");
        assertThat(sala.estado()).isEqualTo(EstadoSala.CUENTA_REGRESIVA);
        assertThat(sala.segundosCuentaRegresiva()).isEqualTo(Sala.SEGUNDOS_CUENTA_REGRESIVA);

        sala.tick(); // la cuenta aún no termina
        assertThat(sala.estado()).isEqualTo(EstadoSala.CUENTA_REGRESIVA);

        reloj.avanzar(Duration.ofSeconds(Sala.SEGUNDOS_CUENTA_REGRESIVA));
        sala.tick();
        assertThat(sala.estado()).isEqualTo(EstadoSala.EN_CURSO);
        assertThat(sala.comida()).isNotNull();
    }

    @Test
    void cuentaRegresiva_seCancela_siAlguienSale() {
        Sala sala = nuevaSala(2);
        sala.unirse("j1", "Ana");
        sala.unirse("j2", "Beto");
        assertThat(sala.estado()).isEqualTo(EstadoSala.CUENTA_REGRESIVA);

        sala.salir("j2");

        assertThat(sala.estado()).isEqualTo(EstadoSala.ESPERANDO_JUGADORES);
    }

    @Test
    void lasSerpientesNacenConCuerpo_noComoUnPunto() {
        Sala sala = nuevaSala(4);
        sala.unirse("j1", "Ana");

        Serpiente j1 = buscar(sala, "j1");
        assertThat(j1.longitud()).isEqualTo(Serpiente.LONGITUD_INICIAL);
        assertThat(j1.cuerpoInmutable()).containsExactly(
                new Posicion(4, 2), new Posicion(3, 2), new Posicion(2, 2), new Posicion(1, 2));
    }

    @Test
    void unirse_lanzaSalaLlenaException_cuandoYaNoHayCupo() {
        Sala sala = nuevaSala(2);
        sala.unirse("j1", "Ana");
        sala.unirse("j2", "Beto");

        assertThrows(SalaLlenaException.class, () -> sala.unirse("j3", "Camila")); // SNK-03
    }

    @Test
    void unirse_lanzaJugadorYaEnSalaException_siYaEstaAdentro() {
        Sala sala = nuevaSala(4);
        sala.unirse("j1", "Ana");

        assertThrows(JugadorYaEnSalaException.class, () -> sala.unirse("j1", "Ana (de nuevo)"));
    }

    @Test
    void unirse_conLaPartidaEnCurso_esRechazado() {
        Sala sala = nuevaSala(3);
        sala.unirse("j1", "Ana");
        sala.unirse("j2", "Beto");
        sala.unirse("j3", "Camila");
        arrancar(sala);
        sala.salir("j3"); // queda un cupo libre, pero la partida sigue con j1 y j2

        assertThrows(PartidaYaIniciadaException.class, () -> sala.unirse("j4", "Diego"));
    }

    @Test
    void antesDelPrimerPaso_nadieSeMueve_yLuegoAvanzanSolas() { // SNK-04, Snake clásico
        Sala sala = partidaDeDos();
        var cuerpoAntes = buscar(sala, "j1").cuerpoInmutable();

        sala.tick(); // aún dentro del respiro inicial
        assertThat(buscar(sala, "j1").cuerpoInmutable()).isEqualTo(cuerpoAntes);

        primerPaso(sala);

        // startsWith y no containsExactly: si la comida cayó justo ahí, la serpiente creció.
        assertThat(buscar(sala, "j1").cuerpoInmutable()).startsWith(
                new Posicion(5, 2), new Posicion(4, 2), new Posicion(3, 2), new Posicion(2, 2));
        assertThat(buscar(sala, "j2").cabeza()).isEqualTo(new Posicion(18, 15));
    }

    @Test
    void sinTocarNada_avanzaUnaCeldaPorPaso() {
        Sala sala = partidaDeDos();
        primerPaso(sala);
        paso(sala);
        paso(sala);

        assertThat(buscar(sala, "j1").cabeza()).isEqualTo(new Posicion(7, 2));
        assertThat(sala.pasosDados()).isEqualTo(3);
    }

    @Test
    void unTickAntesDeTiempo_noDaPaso() {
        Sala sala = partidaDeDos();
        primerPaso(sala);
        reloj.avanzar(Duration.ofMillis(Sala.MILISEGUNDOS_POR_PASO - 10));
        sala.tick();

        assertThat(buscar(sala, "j1").cabeza()).isEqualTo(new Posicion(5, 2));
    }

    @Test
    void girar_cambiaLaDireccionEnElProximoPaso() { // SNK-04
        Sala sala = partidaDeDos();

        sala.moverJugador("j1", Direccion.ABAJO);
        assertThat(buscar(sala, "j1").cabeza()).isEqualTo(new Posicion(4, 2)); // girar no mueve

        primerPaso(sala);
        Serpiente j1 = buscar(sala, "j1");
        assertThat(j1.cuerpoInmutable()).startsWith(
                new Posicion(4, 3), new Posicion(4, 2), new Posicion(3, 2), new Posicion(2, 2));
        paso(sala);
        assertThat(buscar(sala, "j1").cabeza()).isEqualTo(new Posicion(4, 4)); // sigue hacia abajo
    }

    @Test
    void dosGirosRapidos_seAplicanEnPasosSeguidos() {
        Sala sala = partidaDeDos();

        sala.moverJugador("j1", Direccion.ABAJO);
        sala.moverJugador("j1", Direccion.IZQUIERDA); // válido después de ABAJO

        primerPaso(sala);
        assertThat(buscar(sala, "j1").cabeza()).isEqualTo(new Posicion(4, 3));
        paso(sala);
        assertThat(buscar(sala, "j1").cabeza()).isEqualTo(new Posicion(3, 3));
    }

    @Test
    void darMediaVueltaSobreElCuello_seIgnora() {
        Sala sala = partidaDeDos();

        sala.moverJugador("j1", Direccion.IZQUIERDA); // j1 mira a la derecha
        primerPaso(sala);

        assertThat(buscar(sala, "j1").cabeza()).isEqualTo(new Posicion(5, 2)); // siguió derecho
        assertThat(buscar(sala, "j1").estado()).isEqualTo(EstadoSerpiente.VIVA);
    }

    @Test
    void cabezaContraElCuerpoDeOtra_muere_yGanaLaUltimaEnPie() { // SNK-08 + SNK-09/10
        Sala sala = partidaDeDos();
        // j1 baja por la columna 4; j2 viene por la fila 15 hacia la izquierda y
        // su cabeza llega a (4,15) cuando esa celda ya es cuerpo de j1.
        sala.moverJugador("j1", Direccion.ABAJO);

        boolean terminada = primerPaso(sala);
        for (int i = 0; i < 20 && !terminada; i++) {
            terminada = paso(sala);
        }

        assertThat(terminada).isTrue();
        assertThat(buscar(sala, "j2").estado()).isEqualTo(EstadoSerpiente.ELIMINADA);
        ResultadoPartida resultado = sala.construirResultado();
        assertThat(resultado.motivoVictoria()).isEqualTo(MotivoVictoria.ULTIMO_EN_PIE);
        assertThat(resultado.ganadorJugadorId()).isEqualTo("j1");
    }

    @Test
    void dosCabezasEnLaMismaCelda_muerenLasDos() {
        Sala sala = partidaDeDos();
        // j1 da un paso a la derecha y baja por la columna 5; j2 va por la fila 15
        // hacia la izquierda: en el paso 14 las dos cabezas llegan a (5,15).
        primerPaso(sala);
        sala.moverJugador("j1", Direccion.ABAJO);
        boolean terminada = false;
        for (int i = 0; i < 20 && !terminada; i++) {
            terminada = paso(sala);
        }

        assertThat(terminada).isTrue();
        assertThat(buscar(sala, "j1").estado()).isEqualTo(EstadoSerpiente.ELIMINADA);
        assertThat(buscar(sala, "j2").estado()).isEqualTo(EstadoSerpiente.ELIMINADA);
        assertThat(sala.construirResultado().motivoVictoria()).isEqualTo(MotivoVictoria.EMPATE_TODOS_ELIMINADOS);
    }

    @Test
    void chocarConElBorde_eliminaSoloAEsaSerpiente_conTresJugadores() { // SNK-08
        Sala sala = nuevaSala(3);
        sala.unirse("j1", "Ana");
        sala.unirse("j2", "Beto");
        sala.unirse("j3", "Camila");
        arrancar(sala);

        sala.moverJugador("j1", Direccion.ARRIBA);
        primerPaso(sala); // (4,1)
        paso(sala);       // (4,0)
        paso(sala);       // borde

        assertThat(buscar(sala, "j1").estado()).isEqualTo(EstadoSerpiente.ELIMINADA);
        assertThat(sala.estado()).isEqualTo(EstadoSala.EN_CURSO); // j2 y j3 siguen vivos
    }

    @Test
    void ultimoEnPie_ganaElSobreviviente_aunqueElEliminadoFueraPrimeroEnLaLista() { // SNK-09/10
        Sala sala = partidaDeDos();

        sala.moverJugador("j1", Direccion.ARRIBA);
        primerPaso(sala);
        paso(sala);
        boolean terminada = paso(sala);

        assertThat(terminada).isTrue();
        ResultadoPartida resultado = sala.construirResultado();
        assertThat(resultado.ganadorJugadorId()).isEqualTo("j2");
        assertThat(resultado.puntajes().get(0).jugadorId()).isEqualTo("j2");
    }

    @Test
    void unaSerpienteCongelada_noAvanza_peroSigueSiendoObstaculo() { // SNK-07
        Sala sala = partidaDeDos();
        sala.marcarDesconectado("j1");
        var cuerpo = buscar(sala, "j1").cuerpoInmutable();

        primerPaso(sala);
        paso(sala);

        assertThat(buscar(sala, "j1").cuerpoInmutable()).isEqualTo(cuerpo);
        assertThat(buscar(sala, "j2").cabeza()).isEqualTo(new Posicion(17, 15)); // la otra sí avanza
    }

    @Test
    void salir_conUnSoloSobrevivienteRestante_terminaLaPartidaComoUltimoEnPie() { // SNK-06 + SNK-09/10
        Sala sala = partidaDeDos();

        sala.salir("j1");

        assertThat(sala.estado()).isEqualTo(EstadoSala.FINALIZADA);
        ResultadoPartida resultado = sala.construirResultado();
        assertThat(resultado.motivoVictoria()).isEqualTo(MotivoVictoria.ULTIMO_EN_PIE);
        assertThat(resultado.ganadorJugadorId()).isEqualTo("j2");
        assertThat(resultado.puntajes()).hasSize(1);
    }

    @Test
    void tick_agotarElTiempoConVariosVivos_terminaPorMayorPuntaje() { // SNK-09, rama "tiempo agotado"
        Sala sala = partidaDeDos();

        reloj.avanzar(Duration.ofSeconds(Sala.SEGUNDOS_LIMITE_PARTIDA + 1));
        boolean terminada = sala.tick();

        assertThat(terminada).isTrue();
        assertThat(sala.estado()).isEqualTo(EstadoSala.FINALIZADA);
        assertThat(sala.construirResultado().motivoVictoria()).isEqualTo(MotivoVictoria.MAYOR_PUNTAJE_POR_TIEMPO);
    }

    @Test
    void marcarDesconectado_enPartida_congelaLaSerpiente_sinEliminarlaInmediatamente() { // SNK-07
        Sala sala = partidaDeDos();

        sala.marcarDesconectado("j1");

        Serpiente j1 = buscar(sala, "j1");
        assertThat(j1.estado()).isEqualTo(EstadoSerpiente.DESCONECTADA_CONGELADA);
        assertThat(j1.estaViva()).isTrue(); // sigue siendo un obstáculo, no desaparece
        assertThat(j1.esControlablePorJugador()).isFalse(); // pero ya no se puede mover

        sala.reconectar("j1");
        assertThat(buscar(sala, "j1").estado()).isEqualTo(EstadoSerpiente.VIVA);
    }

    @Test
    void marcarDesconectado_enSalaDeEspera_liberaElCupo() {
        Sala sala = nuevaSala(3);
        sala.unirse("j1", "Ana");
        sala.unirse("j2", "Beto");

        sala.marcarDesconectado("j1");

        assertThat(sala.cantidadJugadores()).isEqualTo(1);
        assertThat(sala.tieneJugador("j1")).isFalse();
    }

    @Test
    void reiniciarParaNuevaPartida_conservaALosConectados_yLiberaLosDesconectados() { // SNK-11
        Sala sala = nuevaSala(3);
        sala.unirse("j1", "Ana");
        sala.unirse("j2", "Beto");
        sala.unirse("j3", "Camila");
        arrancar(sala);
        sala.moverJugador("j1", Direccion.ABAJO);
        primerPaso(sala);
        sala.marcarDesconectado("j3");
        sala.salir("j2"); // j1 viva + j3 congelada (cuenta como viva) -> sigue
        reloj.avanzar(Duration.ofSeconds(Sala.SEGUNDOS_LIMITE_PARTIDA + 1));
        assertThat(sala.tick()).isTrue();

        sala.reiniciarParaNuevaPartida();

        assertThat(sala.estado()).isEqualTo(EstadoSala.ESPERANDO_JUGADORES); // ya no está llena
        assertThat(sala.tieneJugador("j3")).isFalse(); // desconectada: libera su cupo
        assertThat(buscar(sala, "j1").cabeza()).isEqualTo(new Posicion(4, 2)); // de vuelta en su spawn
    }

    @Test
    void reiniciarParaNuevaPartida_conLaSalaLlena_programaLaRevancha() {
        Sala sala = partidaDeDos();
        reloj.avanzar(Duration.ofSeconds(Sala.SEGUNDOS_LIMITE_PARTIDA + 1));
        sala.tick();

        sala.reiniciarParaNuevaPartida();

        assertThat(sala.estado()).isEqualTo(EstadoSala.CUENTA_REGRESIVA);
        assertThat(sala.segundosCuentaRegresiva()).isEqualTo(Sala.SEGUNDOS_ANTES_DE_REVANCHA);
    }

    private Sala nuevaSala(int capacidad) {
        return new Sala("s1", "Sala Test", capacidad, reloj);
    }

    private Sala partidaDeDos() {
        Sala sala = nuevaSala(2);
        sala.unirse("j1", "Ana");
        sala.unirse("j2", "Beto");
        arrancar(sala);
        return sala;
    }

    /** Sala llena + fin de la cuenta regresiva: la partida queda EN_CURSO. */
    private void arrancar(Sala sala) {
        assertThat(sala.estado()).isEqualTo(EstadoSala.CUENTA_REGRESIVA);
        reloj.avanzar(Duration.ofSeconds(Sala.SEGUNDOS_CUENTA_REGRESIVA));
        sala.tick();
        assertThat(sala.estado()).isEqualTo(EstadoSala.EN_CURSO);
    }

    /** Termina el respiro inicial y da el primer paso. */
    private boolean primerPaso(Sala sala) {
        reloj.avanzar(Duration.ofMillis(Sala.MILISEGUNDOS_ANTES_DEL_PRIMER_PASO));
        return sala.tick();
    }

    /** Avanza el reloj lo justo para un paso más. */
    private boolean paso(Sala sala) {
        reloj.avanzar(Duration.ofMillis(Sala.MILISEGUNDOS_POR_PASO));
        return sala.tick();
    }

    private static Serpiente buscar(Sala sala, String jugadorId) {
        Optional<Serpiente> encontrada = List.copyOf(sala.serpientes()).stream()
                .filter(s -> s.jugadorId().equals(jugadorId))
                .findFirst();
        return encontrada.orElseThrow(() -> new AssertionError("No se encontró al jugador " + jugadorId + " en la sala."));
    }

    /** Reloj mutable de solo-prueba: permite simular el paso del tiempo sin esperarlo de verdad. */
    static final class MutableClock extends Clock {
        private Instant instante;

        private MutableClock(Instant instante) {
            this.instante = instante;
        }

        static MutableClock en(Instant instante) {
            return new MutableClock(instante);
        }

        void avanzar(Duration duracion) {
            this.instante = this.instante.plus(duracion);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instante;
        }
    }
}
