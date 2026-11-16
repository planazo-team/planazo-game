package com.planazo.minijuego.infrastructure.config;

import com.planazo.minijuego.domain.model.ResumenSala;
import com.planazo.minijuego.domain.port.out.GestorDeSalasEnVivoPort;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Métricas del Minijuego para Prometheus (Feature 10.4, Observabilidad).
 *
 * <p>Igual que en los servicios NestJS, se publican en un puerto aparte
 * ({@code METRICS_PORT}, 9464) que solo alcanza la red privada de Railway:
 * el dominio público de game nunca sirve métricas, y {@code /actuator/health}
 * sigue en el puerto principal para Railway y el gateway.
 */
@Component
public class MetricasJuego {

    private static final Logger log = LoggerFactory.getLogger(MetricasJuego.class);

    private final PrometheusMeterRegistry prometheus;
    private final int puerto;
    private HttpServer servidor;

    public MetricasJuego(MeterRegistry registry,
                         PrometheusMeterRegistry prometheus,
                         GestorDeSalasEnVivoPort salas,
                         @Value("${METRICS_PORT:9464}") int puerto) {
        this.prometheus = prometheus;
        this.puerto = puerto;
        registrarMedidores(registry, salas);
    }

    /** Salas en partida, salas con gente y jugadores dentro de alguna sala, leídos en vivo del registro. */
    static void registrarMedidores(MeterRegistry registry, GestorDeSalasEnVivoPort salas) {
        medidor(registry, salas, "planazo.salas.en_curso", "Salas con una partida en curso",
                lista -> lista.stream().filter(s -> "EN_CURSO".equals(s.estado())).count());
        medidor(registry, salas, "planazo.salas.ocupadas", "Salas con al menos un jugador",
                lista -> lista.stream().filter(s -> s.cantidadJugadores() > 0).count());
        medidor(registry, salas, "planazo.jugadores.en_salas", "Jugadores dentro de alguna sala",
                lista -> lista.stream().mapToInt(ResumenSala::cantidadJugadores).sum());
    }

    private static void medidor(MeterRegistry registry, GestorDeSalasEnVivoPort salas, String nombre, String ayuda,
                                ToDoubleFunction<List<ResumenSala>> valor) {
        Gauge.builder(nombre, salas, s -> valor.applyAsDouble(s.listarSalas()))
                .description(ayuda)
                .register(registry);
    }

    @PostConstruct
    void iniciar() throws IOException {
        if (puerto <= 0) {
            return; // 0 o negativo: sin servidor de métricas (pruebas)
        }
        servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
        servidor.createContext("/metrics", intercambio -> {
            byte[] cuerpo = prometheus.scrape().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            intercambio.sendResponseHeaders(200, cuerpo.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        servidor.start();
        log.info("Métricas de Prometheus en :{}/metrics (red privada)", puerto);
    }

    @PreDestroy
    void detener() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    int puerto() {
        return servidor == null ? -1 : servidor.getAddress().getPort();
    }
}
