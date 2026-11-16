package com.planazo.minijuego.domain.model;

import com.planazo.minijuego.domain.exception.JugadorNoEnSalaException;
import com.planazo.minijuego.domain.exception.JugadorYaEnSalaException;
import com.planazo.minijuego.domain.exception.PartidaNoEnCursoException;
import com.planazo.minijuego.domain.exception.PartidaYaIniciadaException;
import com.planazo.minijuego.domain.exception.SalaLlenaException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Agregado raíz del Minijuego: una sala física (pre-existente, de cupo
 * fijo — SNK-01/SNK-02/SNK-03) y, mientras tiene jugadores, la partida de
 * Snake que corre dentro de ella (SNK-04 a SNK-10).
 *
 * <p>Reglas de esta variante de Snake:
 * <ul>
 *   <li>La partida solo arranca con la sala <b>llena</b>, tras una cuenta
 *       regresiva de {@link #SEGUNDOS_CUENTA_REGRESIVA} segundos.</li>
 *   <li>La serpiente nunca avanza sola: cada paso requiere un comando de
 *       movimiento nuevo del jugador ({@link #moverJugador}), que gira y
 *       avanza una celda en el mismo gesto. Sin tecla nueva se queda quieta.
 *       Las congeladas por desconexión no se pueden mover, pero siguen
 *       siendo obstáculo.</li>
 *   <li>Como cada jugador avanza a su propio ritmo (no hay paso simultáneo
 *       de todas a la vez), dos cabezas solo "chocan de frente" si una se
 *       mueve sobre la celda que la otra ya ocupa en ese instante.</li>
 *   <li>Si la cabeza de una serpiente toca un borde o cualquier cuerpo
 *       (el suyo o el de otra), esa serpiente muere. Gana la última en pie.</li>
 * </ul>
 *
 * <p><b>Contrato de concurrencia (léelo antes de tocar esta clase):</b> esta
 * clase asume que un único hilo la posee en un instante dado —
 * {@code infrastructure.adapter.out.runtime.SalaRuntime} es ese hilo, uno
 * por sala, y es el único punto del sistema autorizado a llamar
 * {@link #tick()}, {@link #unirse}, {@link #salir}, {@link #moverJugador},
 * etc. Por eso ningún método de aquí es {@code synchronized}: el diseño ya
 * elimina la carrera moviendo el acceso concurrente afuera (a la cola de
 * comandos del runtime), no metiéndolo para adentro. Esta clase en sí misma
 * NO es segura para llamarse desde varios hilos a la vez.
 */
public class Sala {

    public static final int ANCHO_TABLERO = 24;
    public static final int ALTO_TABLERO = 18;
    public static final int SEGUNDOS_CONGELACION_DESCONEXION = 15;
    public static final int SEGUNDOS_LIMITE_PARTIDA = 180;
    public static final int SEGUNDOS_CUENTA_REGRESIVA = 3;

    private final String id;
    private final String codigo;
    private final int capacidad;
    private final Map<String, Serpiente> serpientes = new LinkedHashMap<>();
    private final Clock clock;
    private EstadoSala estado;
    private Posicion comida;
    private Instant iniciadaEn;
    private Instant cuentaRegresivaTerminaEn;
    private long pasosDados;

    public Sala(String id, String codigo, int capacidad) {
        this(id, codigo, capacidad, Clock.systemUTC());
    }

    /**
     * Constructor con {@link Clock} inyectable — existe solo para que
     * {@code SalaTest} pueda probar la cuenta regresiva y la condición de
     * victoria "se agotó el tiempo" (SNK-09) sin esperar en tiempo real. En
     * producción siempre se usa el constructor de arriba, con el reloj
     * del sistema.
     */
    public Sala(String id, String codigo, int capacidad, Clock clock) {
        if (capacidad < 2 || capacidad > 6) {
            throw new IllegalArgumentException("La capacidad de una sala de Snake debe estar entre 2 y 6 jugadores.");
        }
        this.id = id;
        this.codigo = codigo;
        this.capacidad = capacidad;
        this.clock = clock;
        this.estado = EstadoSala.ESPERANDO_JUGADORES;
    }

    // ---------------------------------------------------------------
    // SNK-01 / SNK-02 / SNK-03 / SNK-05: sala de espera y arranque
    // ---------------------------------------------------------------

    /**
     * Une un jugador a la sala de espera en un punto de aparición fijo. Con
     * el último cupo ocupado arranca sola la cuenta regresiva (SNK-05);
     * antes de eso nadie se mueve.
     */
    public void unirse(String jugadorId, String jugadorNombre) {
        if (serpientes.containsKey(jugadorId)) {
            throw new JugadorYaEnSalaException(jugadorId);
        }
        if (estado == EstadoSala.EN_CURSO || estado == EstadoSala.FINALIZADA) {
            throw new PartidaYaIniciadaException(codigo);
        }
        if (estaLlena()) {
            throw new SalaLlenaException(codigo, capacidad);
        }
        PuntoDeAparicion aparicion = PuntoDeAparicion.paraIndice(serpientes.size());
        serpientes.put(jugadorId, new Serpiente(jugadorId, jugadorNombre, aparicion.posicion(), aparicion.direccion()));
        evaluarCuentaRegresiva(SEGUNDOS_CUENTA_REGRESIVA);
    }

    /** Sala llena en espera: arranca la cuenta. Si alguien se va durante la cuenta: se cancela. */
    private void evaluarCuentaRegresiva(int segundos) {
        if (estado == EstadoSala.ESPERANDO_JUGADORES && estaLlena()) {
            this.estado = EstadoSala.CUENTA_REGRESIVA;
            this.cuentaRegresivaTerminaEn = Instant.now(clock).plusSeconds(segundos);
        } else if (estado == EstadoSala.CUENTA_REGRESIVA && !estaLlena()) {
            this.estado = EstadoSala.ESPERANDO_JUGADORES;
            this.cuentaRegresivaTerminaEn = null;
        }
    }

    /** Todos reaparecen en su punto de spawn según el orden actual: sin huecos aunque alguien haya salido. */
    private void iniciarPartida() {
        reubicarEnPuntosDeAparicion();
        this.comida = generarPosicionComidaLibre();
        this.cuentaRegresivaTerminaEn = null;
        this.estado = EstadoSala.EN_CURSO;
        this.iniciadaEn = Instant.now(clock);
    }

    private void reubicarEnPuntosDeAparicion() {
        int indice = 0;
        for (Serpiente serpiente : serpientes.values()) {
            PuntoDeAparicion aparicion = PuntoDeAparicion.paraIndice(indice++);
            serpiente.reaparecer(aparicion.posicion(), aparicion.direccion());
        }
    }

    /**
     * SNK-06 ("Salir de la sala o de la partida sin romperla"): el jugador
     * desaparece del todo (a diferencia de una desconexión, que congela).
     * Si tras la salida la partida se queda sin motivo para continuar, se
     * resuelve aquí mismo.
     */
    public void salir(String jugadorId) {
        Serpiente serpiente = obtenerOLanzar(jugadorId);
        serpiente.eliminar();
        serpientes.remove(jugadorId);
        if (estado == EstadoSala.EN_CURSO) {
            resolverSiPartidaDebeTerminar(false);
        } else {
            evaluarCuentaRegresiva(SEGUNDOS_CUENTA_REGRESIVA);
        }
    }

    /**
     * SNK-07: al perder el socket en plena partida, la serpiente se congela
     * — sigue siendo un obstáculo pero nadie la controla. Fuera de partida
     * (sala de espera o cuenta regresiva) no hay nada que congelar: el
     * jugador se va de la sala, para que el cupo y el contador reflejen
     * solo a quien de verdad sigue ahí.
     */
    public void marcarDesconectado(String jugadorId) {
        if (estado == EstadoSala.EN_CURSO) {
            obtenerOLanzar(jugadorId).congelarPorDesconexion();
        } else {
            salir(jugadorId);
        }
    }

    /** SNK-07: si reconecta dentro de la ventana de gracia, sigue exactamente donde iba. */
    public void reconectar(String jugadorId) {
        obtenerOLanzar(jugadorId).reconectar();
    }

    // ---------------------------------------------------------------
    // SNK-04 / SNK-08: mover, comer, chocar
    // ---------------------------------------------------------------

    /**
     * SNK-04: el jugador pide que su serpiente gire hacia {@code direccion} y
     * avance una celda — en el mismo gesto, de inmediato. No hay tick
     * automático de posición: sin un comando nuevo la serpiente se queda
     * quieta (esta es justo la diferencia con el Snake clásico de avance
     * solo). Dar media vuelta sobre el propio cuello se ignora en silencio
     * y no cuenta como paso.
     *
     * @return true si con este movimiento la partida terminó.
     */
    public boolean moverJugador(String jugadorId, Direccion direccion) {
        if (estado != EstadoSala.EN_CURSO) {
            throw new PartidaNoEnCursoException(id);
        }
        Serpiente serpiente = obtenerOLanzar(jugadorId);
        if (!serpiente.esControlablePorJugador() || !serpiente.cambiarDireccion(direccion)) {
            return false;
        }
        return avanzarUnaCelda(serpiente);
    }

    /**
     * Avanza solo a {@code serpiente} una celda en su dirección actual.
     * Las demás serpientes no se tocan: cada jugador avanza a su propio
     * ritmo, así que el choque contra el cuerpo de otra (incluida su
     * cabeza, si está quieta en ese instante) se resuelve contra el estado
     * actual del tablero, no contra un paso simultáneo de todas.
     *
     * @return true si con este paso la partida terminó.
     */
    private boolean avanzarUnaCelda(Serpiente serpiente) {
        pasosDados++;
        Posicion cabeza = serpiente.proximaCabeza();
        boolean come = cabeza.equals(comida);
        if (!cabeza.dentroDe(ANCHO_TABLERO, ALTO_TABLERO) || chocaConCuerpo(serpiente, cabeza, come)) {
            serpiente.eliminar();
        } else {
            serpiente.avanzar(cabeza, come);
            if (come) {
                comida = generarPosicionComidaLibre();
            }
        }
        return resolverSiPartidaDebeTerminar(false);
    }

    /**
     * ¿La celda {@code cabeza} cae sobre el cuerpo de alguna serpiente viva?
     * Solo la cola de la propia serpiente que se mueve se libera cuando no
     * come (el resto de serpientes está quieta en este instante, así que su
     * cuerpo entero — cabeza incluida — sigue siendo obstáculo).
     */
    private boolean chocaConCuerpo(Serpiente moviendose, Posicion cabeza, boolean come) {
        for (Serpiente otra : serpientes.values()) {
            if (!otra.estaViva()) {
                continue; // el cuerpo de una serpiente muerta deja de ser obstáculo
            }
            List<Posicion> cuerpo = otra.cuerpoInmutable();
            boolean liberaCola = otra == moviendose && !come;
            int limite = liberaCola ? cuerpo.size() - 1 : cuerpo.size();
            for (int i = 0; i < limite; i++) {
                if (cuerpo.get(i).equals(cabeza)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------
    // El tick: cuenta regresiva, desconexiones y reloj de la partida
    // ---------------------------------------------------------------

    /**
     * Llamado periódicamente por {@code SalaRuntime}. Resuelve todo lo que
     * depende del tiempo — no de un comando de jugador: el fin de la cuenta
     * regresiva, la expiración de congelamientos por desconexión (SNK-07) y
     * el tiempo límite (SNK-09). El avance de las serpientes NO vive aquí
     * desde que dejó de ser automático: ver {@link #moverJugador}.
     *
     * @return true si la partida terminó en este tick (por tiempo agotado).
     */
    public boolean tick() {
        if (estado == EstadoSala.CUENTA_REGRESIVA && !Instant.now(clock).isBefore(cuentaRegresivaTerminaEn)) {
            iniciarPartida();
            return false;
        }
        if (estado != EstadoSala.EN_CURSO) {
            return false;
        }
        eliminarCongelacionesExpiradas();
        boolean tiempoAgotado = tiempoTranscurridoSegundos() >= SEGUNDOS_LIMITE_PARTIDA;
        return resolverSiPartidaDebeTerminar(tiempoAgotado);
    }

    /** Cuántos pasos lleva la partida: el runtime lo usa para saber si hay algo nuevo que difundir. */
    public long pasosDados() {
        return pasosDados;
    }

    private void eliminarCongelacionesExpiradas() {
        for (Serpiente serpiente : serpientes.values()) {
            if (serpiente.segundosCongeladaSuperan(SEGUNDOS_CONGELACION_DESCONEXION)) {
                serpiente.eliminar();
            }
        }
    }

    /**
     * SNK-09: condición de victoria híbrida. Termina la partida si queda
     * un único sobreviviente (o ninguno), o si se agotó el tiempo límite —
     * en ese caso gana quien tenga mayor puntaje, no quien sobrevivió.
     */
    private boolean resolverSiPartidaDebeTerminar(boolean tiempoAgotado) {
        if (estado != EstadoSala.EN_CURSO) {
            return false;
        }
        long vivos = serpientes.values().stream().filter(Serpiente::estaViva).count();
        if (vivos > 1 && !tiempoAgotado) {
            return false;
        }
        this.estado = EstadoSala.FINALIZADA;
        return true;
    }

    /**
     * Tras una partida la sala se vacía y vuelve a la espera: no hay
     * revancha automática. Cada partida tiene un solo resultado (y, si hay
     * bonos de ganadores, un solo podio); quien quiera seguir jugando entra
     * otra vez a una sala, como cualquiera.
     */
    public void cerrarPartida() {
        serpientes.clear();
        comida = null;
        iniciadaEn = null;
        cuentaRegresivaTerminaEn = null;
        this.estado = EstadoSala.ESPERANDO_JUGADORES;
    }

    // ---------------------------------------------------------------
    // Resultado final (SNK-10, y fuente de SNK-08's leaderboard)
    // ---------------------------------------------------------------

    public ResultadoPartida construirResultado() {
        List<Serpiente> ranking = new ArrayList<>(serpientes.values());
        long sobrevivientes = ranking.stream().filter(Serpiente::estaViva).count();
        Comparator<Serpiente> porPuntaje = Comparator.comparingInt(Serpiente::puntaje).reversed();
        // Último en pie: gana quien sobrevivió aunque tenga menos puntos. Por
        // tiempo o empate: manda el puntaje (SNK-09).
        ranking.sort(sobrevivientes == 1
                ? Comparator.comparing(Serpiente::estaViva).reversed().thenComparing(porPuntaje)
                : porPuntaje);

        List<PuntajeJugador> puntajes = new ArrayList<>();
        for (int i = 0; i < ranking.size(); i++) {
            Serpiente serpiente = ranking.get(i);
            puntajes.add(new PuntajeJugador(
                    serpiente.jugadorId(),
                    serpiente.jugadorNombre(),
                    serpiente.puntaje(),
                    i + 1,
                    serpiente.estaViva()
            ));
        }

        long vivos = ranking.stream().filter(Serpiente::estaViva).count();
        MotivoVictoria motivo;
        if (ranking.isEmpty()) {
            motivo = MotivoVictoria.EMPATE_SIN_JUGADORES;
        } else if (vivos == 1) {
            motivo = MotivoVictoria.ULTIMO_EN_PIE;
        } else if (vivos == 0) {
            motivo = MotivoVictoria.EMPATE_TODOS_ELIMINADOS;
        } else {
            motivo = MotivoVictoria.MAYOR_PUNTAJE_POR_TIEMPO;
        }

        Serpiente ganador = ranking.isEmpty() ? null : ranking.get(0);
        return new ResultadoPartida(
                id,
                codigo,
                ganador == null ? null : ganador.jugadorId(),
                ganador == null ? null : ganador.jugadorNombre(),
                motivo,
                puntajes,
                iniciadaEn,
                Instant.now(clock)
        );
    }

    // ---------------------------------------------------------------
    // Consultas / snapshot para difundir por WebSocket
    // ---------------------------------------------------------------

    public boolean estaLlena() {
        return serpientes.size() >= capacidad;
    }

    public boolean estaVacia() {
        return serpientes.isEmpty();
    }

    public int cantidadJugadores() {
        return serpientes.size();
    }

    public boolean tieneJugador(String jugadorId) {
        return serpientes.containsKey(jugadorId);
    }

    public long tiempoTranscurridoSegundos() {
        if (iniciadaEn == null) {
            return 0;
        }
        return Instant.now(clock).getEpochSecond() - iniciadaEn.getEpochSecond();
    }

    public long tiempoRestanteSegundos() {
        return Math.max(0, SEGUNDOS_LIMITE_PARTIDA - tiempoTranscurridoSegundos());
    }

    /** Segundos (redondeados hacia arriba) que faltan para arrancar; 0 si no hay cuenta regresiva. */
    public long segundosCuentaRegresiva() {
        if (estado != EstadoSala.CUENTA_REGRESIVA || cuentaRegresivaTerminaEn == null) {
            return 0;
        }
        long ms = cuentaRegresivaTerminaEn.toEpochMilli() - Instant.now(clock).toEpochMilli();
        return Math.max(0, (ms + 999) / 1000);
    }

    public List<Serpiente> serpientes() {
        return List.copyOf(serpientes.values());
    }

    public Posicion comida() {
        return comida;
    }

    public String id() {
        return id;
    }

    public String codigo() {
        return codigo;
    }

    public int capacidad() {
        return capacidad;
    }

    public EstadoSala estado() {
        return estado;
    }

    private Serpiente obtenerOLanzar(String jugadorId) {
        Serpiente serpiente = serpientes.get(jugadorId);
        if (serpiente == null) {
            throw new JugadorNoEnSalaException(jugadorId);
        }
        return serpiente;
    }

    private Posicion generarPosicionComidaLibre() {
        for (int intento = 0; intento < 200; intento++) {
            Posicion candidata = new Posicion(
                    ThreadLocalRandom.current().nextInt(ANCHO_TABLERO),
                    ThreadLocalRandom.current().nextInt(ALTO_TABLERO)
            );
            boolean ocupada = serpientes.values().stream().anyMatch(s -> s.ocupa(candidata));
            if (!ocupada) {
                return candidata;
            }
        }
        // Tablero casi lleno: no hay celda perfectamente libre, se acepta cualquiera.
        return new Posicion(ThreadLocalRandom.current().nextInt(ANCHO_TABLERO), ThreadLocalRandom.current().nextInt(ALTO_TABLERO));
    }

    /**
     * Los 6 puntos de aparición fijos (tablero de 24x18), cada uno mirando
     * hacia el interior y con espacio detrás para los
     * {@link Serpiente#LONGITUD_INICIAL} segmentos del cuerpo. Ningún
     * cuerpo inicial se cruza con otro.
     */
    private record PuntoDeAparicion(Posicion posicion, Direccion direccion) {
        static PuntoDeAparicion paraIndice(int indice) {
            return switch (indice % 6) {
                case 0 -> new PuntoDeAparicion(new Posicion(4, 2), Direccion.DERECHA);    // cuerpo en x 1..4
                case 1 -> new PuntoDeAparicion(new Posicion(19, 15), Direccion.IZQUIERDA); // cuerpo en x 19..22
                case 2 -> new PuntoDeAparicion(new Posicion(21, 4), Direccion.ABAJO);     // cuerpo en y 1..4
                case 3 -> new PuntoDeAparicion(new Posicion(2, 13), Direccion.ARRIBA);    // cuerpo en y 13..16
                case 4 -> new PuntoDeAparicion(new Posicion(12, 4), Direccion.ABAJO);
                default -> new PuntoDeAparicion(new Posicion(11, 13), Direccion.ARRIBA);
            };
        }
    }
}
