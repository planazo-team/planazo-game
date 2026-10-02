package com.planazo.minijuego.infrastructure.adapter.out.persistence;

import com.planazo.minijuego.domain.model.DefinicionSala;
import com.planazo.minijuego.domain.port.out.SalaCatalogoPort;
import com.planazo.minijuego.infrastructure.adapter.out.persistence.entity.SalaEntity;
import com.planazo.minijuego.infrastructure.adapter.out.persistence.repository.SalaJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JpaSalaCatalogoAdapter implements SalaCatalogoPort {

    private final SalaJpaRepository repository;

    public JpaSalaCatalogoAdapter(SalaJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<DefinicionSala> listarTodas() {
        return repository.findAll().stream()
                .map(e -> new DefinicionSala(e.getId(), e.getCodigo(), e.getCapacidad()))
                .toList();
    }
}
