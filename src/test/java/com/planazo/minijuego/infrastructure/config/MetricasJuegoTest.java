package com.planazo.minijuego.infrastructure.config;

import com.planazo.minijuego.domain.model.ResumenSala;
import com.planazo.minijuego.domain.port.out.GestorDeSalasEnVivoPort;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricasJuegoTest {

    private final GestorDeSalasEnVivoPort salas = mock(GestorDeSalasEnVivoPort.class);
    private final PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    MetricasJuegoTest() {
        when(salas.listarSalas()).thenReturn(List.of(
                new ResumenSala("s1", "SALA-01", 2, 2, "EN_CURSO", List.of("Ana", "Beto"), List.of("j1", "j2")),
                new ResumenSala("s2", "SALA-02", 2, 1, "ESPERANDO_JUGADORES", List.of("Caro"), List.of("j3")),
                new ResumenSala("s3", "SALA-03", 2, 0, "ESPERANDO_JUGADORES", List.of(), List.of())));
    }

    @Test
    void mideLasSalasYLosJugadoresEnVivo() {
        new MetricasJuego(prometheus, prometheus, salas, 0);
        assertThat(prometheus.get("planazo.salas.en_curso").gauge().value()).isEqualTo(1);
        assertThat(prometheus.get("planazo.salas.ocupadas").gauge().value()).isEqualTo(2);
        assertThat(prometheus.get("planazo.jugadores.en_salas").gauge().value()).isEqualTo(3);
    }

    @Test
    void conPuertoCeroNoAbreServidor() throws Exception {
        MetricasJuego metricas = new MetricasJuego(prometheus, prometheus, salas, 0);
        metricas.iniciar();
        assertThat(metricas.puerto()).isEqualTo(-1);
        metricas.detener();
    }

    @Test
    void publicaLasMetricasEnFormatoPrometheus() throws Exception {
        int libre;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            libre = socket.getLocalPort();
        }
        MetricasJuego metricas = new MetricasJuego(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT), prometheus, salas, libre);
        new MetricasJuego(prometheus, prometheus, salas, 0); // registra los medidores en el registro que se publica
        metricas.iniciar();
        try {
            HttpResponse<String> r = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + metricas.puerto() + "/metrics")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(r.statusCode()).isEqualTo(200);
            assertThat(r.body()).contains("planazo_salas_en_curso");
        } finally {
            metricas.detener();
        }
    }
}
