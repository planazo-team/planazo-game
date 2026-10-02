package com.planazo.minijuego.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "puntajes_jugador")
public class PuntajeJugadorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "jugador_id", nullable = false, length = 64)
    private String jugadorId;

    @Column(name = "jugador_nombre", nullable = false, length = 120)
    private String jugadorNombre;

    @Column(name = "puntaje", nullable = false)
    private int puntaje;

    @Column(name = "posicion_final", nullable = false)
    private int posicionFinal;

    @Column(name = "sobrevivio", nullable = false)
    private boolean sobrevivio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resultado_partida_id", nullable = false)
    private ResultadoPartidaEntity resultadoPartida;

    protected PuntajeJugadorEntity() {
        // Requerido por JPA.
    }

    public PuntajeJugadorEntity(String jugadorId, String jugadorNombre, int puntaje, int posicionFinal, boolean sobrevivio) {
        this.jugadorId = jugadorId;
        this.jugadorNombre = jugadorNombre;
        this.puntaje = puntaje;
        this.posicionFinal = posicionFinal;
        this.sobrevivio = sobrevivio;
    }

    void asignarResultado(ResultadoPartidaEntity resultadoPartida) {
        this.resultadoPartida = resultadoPartida;
    }

    public String getJugadorId() {
        return jugadorId;
    }

    public String getJugadorNombre() {
        return jugadorNombre;
    }

    public int getPuntaje() {
        return puntaje;
    }

    public int getPosicionFinal() {
        return posicionFinal;
    }

    public boolean isSobrevivio() {
        return sobrevivio;
    }
}
