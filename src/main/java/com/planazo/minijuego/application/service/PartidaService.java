package com.planazo.minijuego.application.service;

import com.planazo.minijuego.domain.model.Direccion;
import com.planazo.minijuego.domain.port.in.JugarPartidaUseCase;
import com.planazo.minijuego.domain.port.out.GestorDeSalasEnVivoPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** SNK-04, SNK-07. El cierre de partida vive en {@link FinalizarPartidaService}. */
@Service
public class PartidaService implements JugarPartidaUseCase {

    private final GestorDeSalasEnVivoPort salasEnVivo;

    public PartidaService(GestorDeSalasEnVivoPort salasEnVivo) {
        this.salasEnVivo = salasEnVivo;
    }

    @Override
    public void moverSerpiente(String salaId, String jugadorId, Direccion direccion) {
        salasEnVivo.encolarMovimiento(salaId, jugadorId, direccion);
    }

    @Override
    public void manejarDesconexion(String salaId, String jugadorId) {
        salasEnVivo.marcarDesconectado(salaId, jugadorId);
    }

    @Override
    public void manejarReconexion(String salaId, String jugadorId) {
        salasEnVivo.marcarReconectado(salaId, jugadorId);
    }

    @Override
    public void pausarSerpiente(String salaId, String jugadorId) {
        salasEnVivo.pausar(salaId, jugadorId);
    }

    @Override
    public void reanudarSerpiente(String salaId, String jugadorId) {
        salasEnVivo.reanudar(salaId, jugadorId);
    }

    @Override
    public Map<String, List<String>> jugadoresEnSalaDeEspera() {
        return salasEnVivo.jugadoresEnEspera();
    }

    @Override
    public void retirarJugadorInactivo(String salaId, String jugadorId) {
        salasEnVivo.salir(salaId, jugadorId);
    }
}
