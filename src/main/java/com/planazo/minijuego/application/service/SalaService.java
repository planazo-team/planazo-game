package com.planazo.minijuego.application.service;

import com.planazo.minijuego.domain.exception.SalaNoEncontradaException;
import com.planazo.minijuego.domain.model.ResumenSala;
import com.planazo.minijuego.domain.port.in.GestionarSalasUseCase;
import com.planazo.minijuego.domain.port.out.GestorDeSalasEnVivoPort;
import org.springframework.stereotype.Service;

import java.util.List;

/** SNK-01, SNK-02, SNK-03. */
@Service
public class SalaService implements GestionarSalasUseCase {

    private final GestorDeSalasEnVivoPort salasEnVivo;

    public SalaService(GestorDeSalasEnVivoPort salasEnVivo) {
        this.salasEnVivo = salasEnVivo;
    }

    @Override
    public List<ResumenSala> listarSalas() {
        return salasEnVivo.listarSalas();
    }

    @Override
    public ResumenSala obtenerSala(String salaId) {
        ResumenSala resumen = salasEnVivo.obtenerSala(salaId);
        if (resumen == null) {
            throw new SalaNoEncontradaException(salaId);
        }
        return resumen;
    }

    @Override
    public ResumenSala unirseASala(String salaId, String jugadorId, String jugadorNombre) {
        return salasEnVivo.unirse(salaId, jugadorId, jugadorNombre);
    }

    @Override
    public void salirDeSala(String salaId, String jugadorId) {
        salasEnVivo.salir(salaId, jugadorId);
    }
}
