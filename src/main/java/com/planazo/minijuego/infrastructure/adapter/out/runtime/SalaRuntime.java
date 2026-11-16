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

import java.util.List;
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
    private long ultimoPasoDifundido = -1;
    private long ultimoSegundoCuentaDifundido = -1;
    private long ultimoSegundoRelojDifundido = -1;

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
            // Tolerante: tras el fin de una partida la sala ya se vació y el
            // navegador puede pedir salir igual (al cerrar la pestaña).
            if (!sala.tieneJugador(jugadorId)) {
                return;
            }
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

    /** Tolerante: la pestaña puede ocultarse justo cuando la partida terminó y la sala se vació. */
    public void pausar(String jugadorId) {
        lock.lock();
        try {
            if (!sala.tieneJugador(jugadorId)) {
                return;
            }
            sala.pausarJugador(jugadorId);
        } finally {
            lock.unlock();
        }
        difundirEstado(null);
    }

    public void reanudar(String jugadorId) {
        lock.lock();
        try {
            if (!sala.tieneJugador(jugadorId)) {
                return;
            }
            sala.reanudarJugador(jugadorId);
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

    /** Los jugadores de la sala si todavía no está en partida; vacía si está jugando o no hay nadie. */
    public List<String> jugadoresSiNoEstaEnPartida() {
        lock.lock();
        try {
            EstadoSala estado = sala.estado();
            if (estado != EstadoSala.ESPERANDO_JUGADORES && estado != EstadoSala.CUENTA_REGRESIVA) {
                return List.of();
            }
            return sala.serpientes().stream().map(s -> s.jugadorId()).toList();
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
        List<String> ids = sala.serpientes().stream().map(s -> s.jugadorId()).toList();
        return new ResumenSala(sala.id(), sala.codigo(), sala.capacidad(), sala.cantidadJugadores(),
                sala.estado().name(), nombres, ids);
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
        ultimoPasoDifundido = -1;
        ultimoSegundoCuentaDifundido = -1;
        ultimoSegundoRelojDifundido = -1;
        colaMovimientos.clear();
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
                        difundir = true;
                        arrancoLaPartida = true;
                    } else if (sala.segundosCuentaRegresiva() != ultimoSegundoCuentaDifundido) {
                        ultimoSegundoCuentaDifundido = sala.segundosCuentaRegresiva();
                        difundir = true;
                    }
                }
                case EN_CURSO -> {
                    aplicarGiros();
                    boolean terminada = sala.tick(); // paso de las serpientes, reloj y desconexiones
                    if (sala.pasosDados() != ultimoPasoDifundido) {
                        ultimoPasoDifundido = sala.pasosDados();
                        difundir = true;
                    }
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
     * Pasa a la sala los giros que llegaron desde el último tick. Las
     * serpientes avanzan solas dentro de {@link Sala#tick()}; un giro solo
     * cambia hacia dónde irá el próximo paso. Requiere el lock.
     */
    private void aplicarGiros() {
        ComandoMovimiento comando;
        while ((comando = colaMovimientos.poll()) != null) {
            try {
                sala.moverJugador(comando.jugadorId(), comando.direccion());
            } catch (DomainException ex) {
                log.debug("Giro descartado en sala {}: {}", sala.id(), ex.getMessage());
            }
        }
    }

    /**
     * Lo que tiene que pasar exactamente una vez que una partida termina,
     * sin importar si lo detectó un tick normal o un jugador saliéndose
     * a mitad de partida (SNK-06): difundir el resultado final (SNK-10),
     * persistirlo y actualizar el leaderboard
     * ({@link FinalizarPartidaUseCase}, CC-2), y vaciar la sala: la
     * partida terminó para todos y no arranca otra sola.
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
            sala.cerrarPartida();
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
