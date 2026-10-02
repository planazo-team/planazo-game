package com.planazo.minijuego.infrastructure.adapter.out.leaderboard;

import com.planazo.minijuego.domain.model.EntradaLeaderboard;
import com.planazo.minijuego.domain.port.out.LeaderboardPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CC-2: "atomic sorted-set leaderboard writes". {@code ZINCRBY} (aquí,
 * {@code incrementScore}) es atómico en Redis sin que este adaptador
 * tenga que hacer nada especial — ni transacción, ni lock, ni
 * read-modify-write manual. El puntaje y el nombre del jugador se guardan
 * en dos estructuras separadas ({@code ZSET} para el puntaje, {@code HASH}
 * para el nombre para mostrar) porque son dos preguntas distintas
 * ("¿cuánto lleva?" vs. "¿cómo se llama?") con patrones de acceso
 * distintos.
 *
 * <p>Esta clase es fail-soft desde la perspectiva del dominio: quien la
 * invoca ({@code FinalizarPartidaService.finalizarPartida}) ya envuelve sus
 * llamadas en un {@code try/catch}, así que aquí no se silencia ninguna
 * excepción — si Redis está caído, el error se propaga y es
 * responsabilidad de quien llama decidir que "esto no debe tumbar el
 * cierre de la partida".
 */
@Component
public class RedisLeaderboardAdapter implements LeaderboardPort {

    private static final String CLAVE_PUNTAJES = "minijuego:leaderboard:puntajes";
    private static final String CLAVE_NOMBRES = "minijuego:leaderboard:nombres";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisLeaderboardAdapter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void registrarPuntaje(String jugadorId, String jugadorNombre, int puntajeGanado) {
        redisTemplate.opsForZSet().incrementScore(CLAVE_PUNTAJES, jugadorId, puntajeGanado);
        redisTemplate.opsForHash().put(CLAVE_NOMBRES, jugadorId, jugadorNombre);
    }

    @Override
    public List<EntradaLeaderboard> topN(int n) {
        Set<ZSetOperations.TypedTuple<String>> top =
                redisTemplate.opsForZSet().reverseRangeWithScores(CLAVE_PUNTAJES, 0, Math.max(0, n - 1));
        List<EntradaLeaderboard> entradas = new ArrayList<>();
        if (top == null) {
            return entradas;
        }
        int posicion = 1;
        for (ZSetOperations.TypedTuple<String> tupla : top) {
            String jugadorId = tupla.getValue();
            long puntaje = tupla.getScore() == null ? 0L : tupla.getScore().longValue();
            Object nombre = redisTemplate.opsForHash().get(CLAVE_NOMBRES, jugadorId);
            entradas.add(new EntradaLeaderboard(posicion++, jugadorId, nombre == null ? jugadorId : nombre.toString(), puntaje));
        }
        return entradas;
    }
}
