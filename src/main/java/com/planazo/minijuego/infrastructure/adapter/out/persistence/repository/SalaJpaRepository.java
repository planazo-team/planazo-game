package com.planazo.minijuego.infrastructure.adapter.out.persistence.repository;

import com.planazo.minijuego.infrastructure.adapter.out.persistence.entity.SalaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaJpaRepository extends JpaRepository<SalaEntity, String> {
}
