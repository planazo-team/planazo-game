package com.planazo.minijuego.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServidoPorFilterTest {

    @Test
    void idReplicaUsaRailwayLuegoHostnameYQuedaCortoYSeguro() {
        assertThat(ServidoPorFilter.idReplica(Map.of("RAILWAY_REPLICA_ID", "a1b2c3d4-e5f6-7890"))).isEqualTo("a1b2c3d4");
        assertThat(ServidoPorFilter.idReplica(Map.of("HOSTNAME", "cont_01"))).isEqualTo("cont01");
        assertThat(ServidoPorFilter.idReplica(Map.of("RAILWAY_REPLICA_ID", " ", "HOSTNAME", "h1"))).isEqualTo("h1");
        assertThat(ServidoPorFilter.idReplica(Map.of())).isEqualTo("local");
    }

    @Test
    void ponXServedByEnLaRespuestaYSigueLaCadena() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();

        new ServidoPorFilter().doFilter(req, res, cadena);

        assertThat(res.getHeader("x-served-by")).startsWith("game@");
        assertThat(cadena.getRequest()).isSameAs(req);
    }
}
