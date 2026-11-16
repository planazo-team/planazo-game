package com.planazo.minijuego.infrastructure.adapter.out.runtime;

import com.planazo.minijuego.application.service.SnapshotMapper;
import com.planazo.minijuego.domain.exception.SalaNoEncontradaException;
import com.planazo.minijuego.domain.model.DefinicionSala;
import com.planazo.minijuego.domain.model.Direccion;
import com.planazo.minijuego.domain.model.ResumenSala;
import com.planazo.minijuego.domain.model.Sala;
import com.planazo.minijuego.domain.port.in.FinalizarPartidaUseCase;
import com.planazo.minijuego.domain.port.out.GestorDeSalasEnVivoPort;
import com.planazo.minijuego.domain.port.out.SalaCatalogoPort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Implementación de {@link GestorDeSalasEnVivoPort}: crea un
 * {@link SalaRuntime} por cada fila del catálogo (SNK-01/02/03 — las
 * salas son pre-existentes, no las crea el usuario) y enruta cada
 * operación hacia el runtime correspondiente. Un único
 * {@code ScheduledExecutorService} con un hilo por sala ocupada atiende
 * los game loops; no hay un hilo por sala creado de antemano — solo se
 * gasta un hilo mientras esa sala tiene una partida en curso (ver
 * {@code SalaRuntime#iniciarLoop}).
 */
@Component
public class SalaRuntimeRegistry implements GestorDeSalasEnVivoPort {

    private static final Logger log = LoggerFactory.getLogger(SalaRuntimeRegistry.class);

    private final SalaCatalogoPort catalogo;
    private final FinalizarPartidaUseCase finalizarPartidaUseCase;
    private final SnapshotMapper snapshotMapper;
    private final GameBroadcaster broadcaster;
    private final MeterRegistry meterRegistry;
    private final Map<String, SalaRuntime> runtimesPorSalaId = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    public SalaRuntimeRegistry(SalaCatalogoPort catalogo,
                                FinalizarPartidaUseCase finalizarPartidaUseCase,
                                SnapshotMapper snapshotMapper,
                                GameBroadcaster broadcaster,
                                MeterRegistry meterRegistry) {
        this.catalogo = catalogo;
        this.finalizarPartidaUseCase = finalizarPartidaUseCase;
        this.snapshotMapper = snapshotMapper;
        this.broadcaster = broadcaster;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void cargarCatalogoYArrancarRuntimes() {
        List<DefinicionSala> definiciones = catalogo.listarTodas();
        this.scheduler = Executors.newScheduledThreadPool(
                Math.max(2, definiciones.size()),
                runnable -> {
                    Thread hilo = new Thread(runnable);
                    hilo.setName("sala-game-loop-" + hilo.threadId());
                    hilo.setDaemon(true);
                    return hilo;
                }
        );
        for (DefinicionSala definicion : definiciones) {
            Sala sala = new Sala(definicion.id(), definicion.codigo(), definicion.capacidad());
            SalaRuntime runtime = new SalaRuntime(sala, scheduler, finalizarPartidaUseCase, snapshotMapper, broadcaster, this::difundirSalas);
            runtimesPorSalaId.put(definicion.id(), runtime);
            // Gauge de negocio (ESC-07): jugadores activos por sala, leído en vivo del runtime.
            Gauge.builder("minijuego.jugadores.activos", runtime, r -> r.resumen().cantidadJugadores())
                    .tag("sala", definicion.codigo())
                    .register(meterRegistry);
        }
        log.info("Minijuego: {} salas cargadas desde el catálogo y listas para recibir jugadores.", runtimesPorSalaId.size());
    }

    @PreDestroy
    void apagarScheduler() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Override
    public List<ResumenSala> listarSalas() {
        return runtimesPorSalaId.values().stream()
                .map(SalaRuntime::resumen)
                .sorted(Comparator.comparing(ResumenSala::codigo))
                .toList();
    }

    /** Lobby en vivo: cada cambio de ocupación en cualquier sala se difunde a todos los que miran la lista. */
    private void difundirSalas() {
        broadcaster.difundirSalas(listarSalas());
    }

    @Override
    public ResumenSala obtenerSala(String salaId) {
        SalaRuntime runtime = runtimesPorSalaId.get(salaId);
        return runtime == null ? null : runtime.resumen();
    }

    @Override
    /** Un jugador está en una sola sala a la vez: entrar a otra lo saca de la anterior. */
    public ResumenSala unirse(String salaId, String jugadorId, String jugadorNombre) {
        SalaRuntime destino = runtimeOLanzar(salaId);
        runtimesPorSalaId.forEach((otraSalaId, runtime) -> {
            if (!otraSalaId.equals(salaId) && runtime.tieneJugador(jugadorId)) {
                runtime.salir(jugadorId);
            }
        });
        return destino.unirse(jugadorId, jugadorNombre);
    }


    @Override
    public void salir(String salaId, String jugadorId) {
        runtimeOLanzar(salaId).salir(jugadorId);
    }

    @Override
    public void encolarMovimiento(String salaId, String jugadorId, Direccion direccion) {
        SalaRuntime runtime = runtimesPorSalaId.get(salaId);
        if (runtime != null) {
            runtime.encolarMovimiento(jugadorId, direccion);
        }
        // Un comando de movimiento tarde (p.ej. llegó justo cuando la sala ya
        // no existe o el jugador ya salió) simplemente se ignora: es tráfico
        // de altísima frecuencia y de mejor esfuerzo, no una operación que
        // deba fallar ruidosamente.
    }

    @Override
    public void marcarDesconectado(String salaId, String jugadorId) {
        SalaRuntime runtime = runtimesPorSalaId.get(salaId);
        if (runtime != null) {
            runtime.marcarDesconectado(jugadorId);
        }
    }

    @Override
    public void marcarReconectado(String salaId, String jugadorId) {
        runtimeOLanzar(salaId).marcarReconectado(jugadorId);
    }

    @Override
    public void pausar(String salaId, String jugadorId) {
        SalaRuntime runtime = runtimesPorSalaId.get(salaId);
        if (runtime != null) {
            runtime.pausar(jugadorId);
        }
    }

    @Override
    public void reanudar(String salaId, String jugadorId) {
        SalaRuntime runtime = runtimesPorSalaId.get(salaId);
        if (runtime != null) {
            runtime.reanudar(jugadorId);
        }
    }

    @Override
    public Map<String, List<String>> jugadoresEnEspera() {
        Map<String, List<String>> enEspera = new java.util.HashMap<>();
        runtimesPorSalaId.forEach((salaId, runtime) -> {
            List<String> jugadores = runtime.jugadoresSiNoEstaEnPartida();
            if (!jugadores.isEmpty()) {
                enEspera.put(salaId, jugadores);
            }
        });
        return enEspera;
    }

    private SalaRuntime runtimeOLanzar(String salaId) {
        SalaRuntime runtime = runtimesPorSalaId.get(salaId);
        if (runtime == null) {
            throw new SalaNoEncontradaException(salaId);
        }
        return runtime;
    }
}
