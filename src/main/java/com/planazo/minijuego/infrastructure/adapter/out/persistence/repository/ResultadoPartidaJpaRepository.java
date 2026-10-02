package com.planazo.minijuego.infrastructure.adapter.out.persistence.repository;

import com.planazo.minijuego.infrastructure.adapter.out.persistence.entity.ResultadoPartidaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResultadoPartidaJpaRepository extends JpaRepository<ResultadoPartidaEntity, String> {

    @Query("""
            SELECT DISTINCT r FROM ResultadoPartidaEntity r
            JOIN r.puntajes p
            WHERE p.jugadorId = :jugadorId
            ORDER BY r.finalizadaEn DESC
            """)
    List<ResultadoPartidaEntity> buscarPorJugador(@Param("jugadorId") String jugadorId);
}
