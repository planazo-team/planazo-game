package com.planazo.minijuego.infrastructure.adapter.out.runtime;

import com.planazo.minijuego.application.dto.EstadoJuegoDTO;
import com.planazo.minijuego.application.service.SnapshotMapper;
import com.planazo.minijuego.domain.exception.DomainException;
import com.planazo.minijuego.domain.model.Direccion;
import com.planazo.minijuego.domain.model.EstadoSala;
import com.planazo.minijuego.domain.model.ResultadoPartida;
import com.planazo.minijuego.domain.model.ResumenSala;
import com.planazo.minijuego.domain.model.Sala;
import com.planazo.minijuego.domain.port.in.FinalizarPartidaUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * El "actor" de una sola sala: posee un {@link Sala} y es el único
 * componente autorizado a mutarlo. No hay más de un {@code SalaRuntime}
 * por sala (lo garantiza {@link SalaRuntimeRegistry}), y toda mutación
 * pasa por el mismo {@link #lock} — unirse, salir, desconexión,
 * reconexión, y el propio tick del game loop. El movimiento es la única
 * ruta que no toma el lock para *encolarse* (altísima frecuencia, RT-3);
 * sí lo toma para *aplicarse*, dentro de {@link #ejecutarTick()}.
 *
 * <p>Por qué un lock y no el patrón de actor "puro" (una sola cola de
 * comandos también para unirse/salir): unirse y salir necesitan devolver
 * una respuesta síncrona inmediata (SNK-03: rechazo claro si está llena),
 * y ocurren órdenes de magnitud menos seguido que un tick (20/seg). Un
 * lock de grano fino, tomado siempre en el mismo orden y nunca anidado,
 * cumple la regla de concurrencia del servicio sin la complejidad de un
 * mecanismo de futuros/callbacks para algo que no lo necesita.
 *
 * <p>Toda difusión (a la sala y al lobby) ocurre con el lock suelto: el
 * callback del lobby consulta el resumen de todas las salas, y tomar el
 * lock de otra sala con el propio tomado sería un lock anidado.
 */
public class SalaRuntime {

    private static final Logger log = LoggerFactory.getLogger(SalaRuntime.class);
    private static final long PERIODO_TICK_MS = 50; // 20 ticks/segundo, ver RT-3
    /**
     * Las serpientes no avanzan solas: cada tecla encolada mueve una celda.
     * Cada jugador puede dar como máximo un paso cada 2 ticks (100 ms, 10
     * celdas/seg) — si mantiene la tecla presionada, la repetición del
     * teclado no lo lanza a 30 celdas/seg contra la pared.
     */
    private static final int TICKS_POR_PASO = 2;
    /** Teclas guardadas por jugador entre pasos: permite "arriba + izquierda" rápido sin acumular retraso. */
    private static final int MAX_PASOS_PENDIENTES = 2;

    private final Sala sala;
    private final ReentrantLock lock = new ReentrantLock();
    private final Queue<ComandoMovimiento> colaMovimientos = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler;
    private final FinalizarPartidaUseCase finalizarPartidaUseCase;
    private final SnapshotMapper snapshotMapper;
    private final GameBroadcaster broadcaster;
    private final Runnable alCambiarOcupacion;

    // Solo se tocan con el lock tomado.
    private ScheduledFuture<?> tickFuture;
    private int ticksDesdeUltimoPaso;
    private long ultimoSegundoCuentaDifundido = -1;
    private long ultimoSegundoRelojDifundido = -1;
    private final Map<String, Deque<Direccion>> pasosPendientes = new HashMap<>();

    public SalaRuntime(Sala sala,
                        ScheduledExecutorService scheduler,
                        FinalizarPartidaUseCase finalizarPartidaUseCase,
                        SnapshotMapper snapshotMapper,
                        GameBroadcaster broadcaster,
                        Runnable alCambiarOcupacion) {
        this.sala = sala;
        this.scheduler = scheduler;
        this.finalizarPartidaUseCase = finalizarPartidaUseCase;
        this.snapshotMapper = snapshotMapper;
        this.broadcaster = broadcaster;
        this.alCambiarOcupacion = alCambiarOcupacion;
    }

    /**
     * Idempotente: si el jugador ya estaba en la sala (recargó la página o
     * volvió a pulsar "Unirme") se le devuelve la sala tal cual en vez de
     * un 409, para que siempre pueda volver a entrar a su propia partida.
     */
    public ResumenSala unirse(String jugadorId, String jugadorNombre) {
        ResumenSala resumen;
        lock.lock();
        try {
            if (!sala.tieneJugador(jugadorId)) {
                sala.unirse(jugadorId, jugadorNombre);
            }
            sincronizarLoop();
            resumen = resumenSinLock();
        } finally {
            lock.unlock();
        }
        notificarCambio();
        return resumen;
    }

    /**
     * SNK-06. Si el jugador que se va deja a la sala sin motivo para
     * seguir (era el único que quedaba en pie), esto termina la partida
     * exactamente como lo haría un {@link #ejecutarTick()} que detecta lo
     * mismo — misma secuencia de finalizar-y-reiniciar, para no duplicar
     * ni olvidar un paso.
     */
    public void salir(String jugadorId) {
        ResultadoPartida resultado = null;
        lock.lock();
        try {
            boolean seguiaEnCurso = sala.estado() == EstadoSala.EN_CURSO;
            sala.salir(jugadorId);
            if (seguiaEnCurso && sala.estado() == EstadoSala.FINALIZADA) {
                resultado = sala.construirResultado();
            }
            sincronizarLoop();
        } finally {
            lock.unlock();
        }

        if (resultado != null) {
            finalizarYReiniciar(resultado);
        } else {
            notificarCambio();
        }
    }

    /** Tolerante: un socket que se cae después de que el jugador ya salió no es un error. */
    public void marcarDesconectado(String jugadorId) {
        lock.lock();
        try {
            if (!sala.tieneJugador(jugadorId)) {
                return;
            }
            sala.marcarDesconectado(jugadorId);
            sincronizarLoop();
        } finally {
            lock.unlock();
        }
        notificarCambio();
    }

    /**
     * Se invoca cada vez que un cliente registra su sesión de WebSocket.
     * Además de la reconexión de SNK-07, difunde el estado actual: así el
     * cliente que acaba de suscribirse ve la sala de inmediato, sin
     * esperar al próximo cambio.
     */
    public void marcarReconectado(String jugadorId) {
        lock.lock();
        try {
            if (sala.tieneJugador(jugadorId)) {
                sala.reconectar(jugadorId);
            }
        } finally {
            lock.unlock();
        }
        difundirEstado(null);
    }

    /** Sin lock: la cola es concurrente y el único consumidor es {@link #ejecutarTick()}. */
    public void encolarMovimiento(String jugadorId, Direccion direccion) {
        colaMovimientos.add(new ComandoMovimiento(jugadorId, direccion));
    }

    public ResumenSala resumen() {
        lock.lock();
        try {
            return resumenSinLock();
        } finally {
            lock.unlock();
        }
    }

    public boolean tieneJugador(String jugadorId) {
        lock.lock();
        try {
            return sala.tieneJugador(jugadorId);
        } finally {
            lock.unlock();
        }
    }

    private ResumenSala resumenSinLock() {
        List<String> nombres = sala.serpientes().stream().map(s -> s.jugadorNombre()).toList();
        return new ResumenSala(sala.id(), sala.codigo(), sala.capacidad(), sala.cantidadJugadores(),
                sala.estado().name(), nombres);
    }

    /** El loop solo corre mientras hay algo que avanzar: la cuenta regresiva o la partida. Requiere el lock. */
    private void sincronizarLoop() {
        EstadoSala estado = sala.estado();
        if (estado == EstadoSala.CUENTA_REGRESIVA || estado == EstadoSala.EN_CURSO) {
            iniciarLoop();
        } else {
            detenerLoop();
        }
    }

    private void iniciarLoop() {
        if (tickFuture != null && !tickFuture.isDone()) {
            return;
        }
        ticksDesdeUltimoPaso = 0;
        ultimoSegundoCuentaDifundido = -1;
        ultimoSegundoRelojDifundido = -1;
        colaMovimientos.clear();
        pasosPendientes.clear();
        tickFuture = scheduler.scheduleAtFixedRate(this::ejecutarTickSeguro, PERIODO_TICK_MS, PERIODO_TICK_MS, TimeUnit.MILLISECONDS);
    }

    private void detenerLoop() {
        if (tickFuture != null) {
            tickFuture.cancel(false);
            tickFuture = null;
        }
    }

    private void ejecutarTickSeguro() {
        try {
            ejecutarTick();
        } catch (Exception ex) {
            // Un tick nunca puede tumbar el hilo compartido del scheduler: si algo
            // sale mal en una sala, esa sala se detiene, pero no se lleva a las demás.
            log.error("Error inesperado en el tick de la sala {}: {}", sala.id(), ex.getMessage(), ex);
        }
    }

    private void ejecutarTick() {
        ResultadoPartida resultado = null;
        boolean difundir = false;
        boolean arrancoLaPartida = false;

        lock.lock();
        try {
            switch (sala.estado()) {
                case CUENTA_REGRESIVA -> {
                    colaMovimientos.clear(); // las teclas de antes del arranque no cuentan
                    sala.tick();
                    if (sala.estado() == EstadoSala.EN_CURSO) {
                        ticksDesdeUltimoPaso = 0;
                        difundir = true;
                        arrancoLaPartida = true;
                    } else if (sala.segundosCuentaRegresiva() != ultimoSegundoCuentaDifundido) {
                        ultimoSegundoCuentaDifundido = sala.segundosCuentaRegresiva();
                        difundir = true;
                    }
                }
                case EN_CURSO -> {
                    boolean terminada = false;
                    if (++ticksDesdeUltimoPaso >= TICKS_POR_PASO) {
                        ticksDesdeUltimoPaso = 0;
                        boolean huboPasos = darUnPasoPorJugador();
                        terminada = sala.estado() == EstadoSala.FINALIZADA;
                        difundir = huboPasos;
                    }
                    terminada = terminada || sala.tick(); // reloj de la partida y desconexiones
                    if (sala.tiempoRestanteSegundos() != ultimoSegundoRelojDifundido) {
                        ultimoSegundoRelojDifundido = sala.tiempoRestanteSegundos();
                        difundir = true;
                    }
                    if (terminada) {
                        resultado = sala.construirResultado();
                        detenerLoop();
                    }
                }
                default -> detenerLoop();
            }
        } finally {
            lock.unlock();
        }

        if (resultado != null) {
            finalizarYReiniciar(resultado);
        } else if (difundir) {
            difundirEstado(null);
            if (arrancoLaPartida) {
                notificarLobby();
            }
        }
    }

    /**
     * Pasa las teclas recibidas a la fila de cada jugador y aplica a lo sumo
     * un paso por jugador. Requiere el lock.
     *
     * @return true si alguna serpiente se movió (o murió al moverse).
     */
    private boolean darUnPasoPorJugador() {
        ComandoMovimiento comando;
        while ((comando = colaMovimientos.poll()) != null) {
            Deque<Direccion> fila = pasosPendientes.computeIfAbsent(comando.jugadorId(), k -> new ArrayDeque<>());
            if (fila.size() < MAX_PASOS_PENDIENTES) {
                fila.addLast(comando.direccion());
            }
        }
        boolean huboPasos = false;
        for (Map.Entry<String, Deque<Direccion>> entrada : pasosPendientes.entrySet()) {
            Direccion direccion = entrada.getValue().pollFirst();
            if (direccion == null || sala.estado() != EstadoSala.EN_CURSO) {
                continue;
            }
            try {
                sala.moverJugador(entrada.getKey(), direccion);
                huboPasos = true;
            } catch (DomainException ex) {
                log.debug("Movimiento descartado en sala {}: {}", sala.id(), ex.getMessage());
            }
        }
        pasosPendientes.values().removeIf(Deque::isEmpty);
        return huboPasos;
    }

    /**
     * Lo que tiene que pasar exactamente una vez que una partida termina,
     * sin importar si lo detectó un tick normal o un jugador saliéndose
     * a mitad de partida (SNK-06): difundir el resultado final (SNK-10),
     * persistirlo y actualizar el leaderboard
     * ({@link FinalizarPartidaUseCase}, CC-2), y dejar la sala lista para
     * la revancha con los mismos jugadores.
     */
    private void finalizarYReiniciar(ResultadoPartida resultado) {
        difundirEstado(resultado);
        try {
            finalizarPartidaUseCase.finalizarPartida(resultado);
        } catch (Exception ex) {
            // Si Postgres falla no podemos dejar la sala atascada en FINALIZADA para siempre.
            log.error("No se pudo registrar el resultado de la partida de la sala {}: {}", sala.id(), ex.getMessage(), ex);
        }
        lock.lock();
        try {
            sala.reiniciarParaNuevaPartida();
            sincronizarLoop();
        } finally {
            lock.unlock();
        }
        notificarCambio();
    }

    /** Toda mutación visible (unirse, salir, desconexión) se difunde a la sala y al lobby. */
    private void notificarCambio() {
        difundirEstado(null);
        notificarLobby();
    }

    private void notificarLobby() {
        try {
            alCambiarOcupacion.run();
        } catch (Exception ex) {
            log.warn("No se pudo difundir la lista de salas tras un cambio en la sala {}: {}", sala.id(), ex.getMessage());
        }
    }

    private void difundirEstado(ResultadoPartida resultadoSiTermino) {
        var dto = lockedSnapshot(resultadoSiTermino);
        try {
            broadcaster.difundirEstado(dto);
        } catch (Exception ex) {
            log.warn("No se pudo difundir el estado de la sala {} por WebSocket: {}", sala.id(), ex.getMessage());
        }
    }

    private EstadoJuegoDTO lockedSnapshot(ResultadoPartida resultadoSiTermino) {
        lock.lock();
        try {
            return snapshotMapper.aEstadoJuegoDTO(sala, resultadoSiTermino);
        } finally {
            lock.unlock();
        }
    }
}
