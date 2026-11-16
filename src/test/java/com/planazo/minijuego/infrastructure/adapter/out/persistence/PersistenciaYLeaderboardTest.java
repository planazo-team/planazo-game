package com.planazo.minijuego.infrastructure.adapter.out.persistence;

import com.planazo.minijuego.domain.model.DefinicionSala;
import com.planazo.minijuego.domain.model.EntradaLeaderboard;
import com.planazo.minijuego.domain.model.MotivoVictoria;
import com.planazo.minijuego.domain.model.PuntajeJugador;
import com.planazo.minijuego.domain.model.ResultadoPartida;
import com.planazo.minijuego.infrastructure.adapter.out.leaderboard.RedisLeaderboardAdapter;
import com.planazo.minijuego.infrastructure.adapter.out.persistence.entity.ResultadoPartidaEntity;
import com.planazo.minijuego.infrastructure.adapter.out.persistence.entity.SalaEntity;
import com.planazo.minijuego.infrastructure.adapter.out.persistence.mapper.ResultadoPartidaMapper;
import com.planazo.minijuego.infrastructure.adapter.out.persistence.repository.ResultadoPartidaJpaRepository;
import com.planazo.minijuego.infrastructure.adapter.out.persistence.repository.SalaJpaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistenciaYLeaderboardTest {

    private static final Instant INICIO = Instant.parse("2026-11-16T10:00:00Z");
    private static final Instant FIN = Instant.parse("2026-11-16T10:03:00Z");

    static ResultadoPartida resultado() {
        return new ResultadoPartida("s1", "SALA-01", "j1", "Ana", MotivoVictoria.ULTIMO_EN_PIE,
                List.of(new PuntajeJugador("j1", "Ana", 7, 1, true), new PuntajeJugador("j2", "Beto", 3, 2, false)),
                INICIO, FIN);
    }

    private final ResultadoPartidaMapper mapper = new ResultadoPartidaMapper();

    @Test
    void elMapper_vaYVuelveSinPerderNada() {
        ResultadoPartidaEntity entidad = mapper.aEntidad(resultado());
        assertThat(entidad.getSalaCodigo()).isEqualTo("SALA-01");
        assertThat(entidad.getMotivoVictoria()).isEqualTo("ULTIMO_EN_PIE");
        assertThat(entidad.getPuntajes()).hasSize(2);

        ResultadoPartida deVuelta = mapper.aDominio(entidad);
        assertThat(deVuelta).isEqualTo(resultado());
    }

    @Test
    void elAdaptadorJpa_guardaYBuscaPorJugador() {
        ResultadoPartidaJpaRepository repo = mock(ResultadoPartidaJpaRepository.class);
        JpaResultadoPartidaRepositoryAdapter adaptador = new JpaResultadoPartidaRepositoryAdapter(repo, mapper);
        adaptador.guardar(resultado());
        ArgumentCaptor<ResultadoPartidaEntity> guardada = ArgumentCaptor.forClass(ResultadoPartidaEntity.class);
        verify(repo).save(guardada.capture());
        assertThat(guardada.getValue().getGanadorJugadorNombre()).isEqualTo("Ana");
        assertThat(guardada.getValue().getId()).isNotBlank();

        when(repo.buscarPorJugador("j1")).thenReturn(List.of(mapper.aEntidad(resultado())));
        assertThat(adaptador.buscarPorJugador("j1")).containsExactly(resultado());
    }

    @Test
    void elCatalogoJpa_convierteLasFilasEnDefiniciones() {
        SalaJpaRepository repo = mock(SalaJpaRepository.class);
        when(repo.findAll()).thenReturn(List.of(new SalaEntity("s1", "SALA-01", 4)));
        assertThat(new JpaSalaCatalogoAdapter(repo).listarTodas()).containsExactly(new DefinicionSala("s1", "SALA-01", 4));
    }

    @Test
    @SuppressWarnings("unchecked")
    void elLeaderboardEnRedis_sumaPuntos_yArmaElTopConNombres() {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        when(redis.<Object, Object>opsForHash()).thenReturn(hash);
        RedisLeaderboardAdapter leaderboard = new RedisLeaderboardAdapter(redis);

        leaderboard.registrarPuntaje("j1", "Ana", 5);
        verify(zset).incrementScore("minijuego:leaderboard:puntajes", "j1", 5);
        verify(hash).put("minijuego:leaderboard:nombres", "j1", "Ana");

        Set<ZSetOperations.TypedTuple<String>> top = new LinkedHashSet<>();
        top.add(new DefaultTypedTuple<>("j1", 12.0));
        top.add(new DefaultTypedTuple<>("j2", null));
        when(zset.reverseRangeWithScores("minijuego:leaderboard:puntajes", 0, 1)).thenReturn(top);
        when(hash.get("minijuego:leaderboard:nombres", "j1")).thenReturn("Ana");
        assertThat(leaderboard.topN(2)).containsExactly(
                new EntradaLeaderboard(1, "j1", "Ana", 12),
                new EntradaLeaderboard(2, "j2", "j2", 0));

        when(zset.reverseRangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(null);
        assertThat(leaderboard.topN(0)).isEmpty();
    }
}
