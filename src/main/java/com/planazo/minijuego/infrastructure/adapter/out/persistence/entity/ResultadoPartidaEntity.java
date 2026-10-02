package com.planazo.minijuego.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * El resultado de una partida ya terminada. Se escribe una sola vez
 * (constructor + {@code agregarPuntaje}) y nunca se modifica después —
 * CC-2: "que mi puntaje quede registrado sin perderse" significa,
 * literalmente, que esta fila es inmutable desde el punto de vista del
 * negocio una vez guardada.
 */
@Entity
@Table(name = "resultados_partida")
public class ResultadoPartidaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 64)
    private String id;

    @Column(name = "sala_id", nullable = false, length = 64)
    private String salaId;

    @Column(name = "sala_codigo", nullable = false, length = 40)
    private String salaCodigo;

    @Column(name = "ganador_jugador_id", length = 64)
    private String ganadorJugadorId;

    @Column(name = "ganador_jugador_nombre", length = 120)
    private String ganadorJugadorNombre;

    @Column(name = "motivo_victoria", nullable = false, length = 40)
    private String motivoVictoria;

    @Column(name = "iniciada_en")
    private Instant iniciadaEn;

    @Column(name = "finalizada_en", nullable = false)
    private Instant finalizadaEn;

    @OneToMany(mappedBy = "resultadoPartida", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("posicionFinal ASC")
    private List<PuntajeJugadorEntity> puntajes = new ArrayList<>();

    protected ResultadoPartidaEntity() {
        // Requerido por JPA.
    }

    public ResultadoPartidaEntity(String salaId, String salaCodigo, String ganadorJugadorId,
                                   String ganadorJugadorNombre, String motivoVictoria,
                                   Instant iniciadaEn, Instant finalizadaEn) {
        this.id = UUID.randomUUID().toString();
        this.salaId = salaId;
        this.salaCodigo = salaCodigo;
        this.ganadorJugadorId = ganadorJugadorId;
        this.ganadorJugadorNombre = ganadorJugadorNombre;
        this.motivoVictoria = motivoVictoria;
        this.iniciadaEn = iniciadaEn;
        this.finalizadaEn = finalizadaEn;
    }

    public void agregarPuntaje(PuntajeJugadorEntity puntaje) {
        puntaje.asignarResultado(this);
        this.puntajes.add(puntaje);
    }

    public String getId() {
        return id;
    }

    public String getSalaId() {
        return salaId;
    }

    public String getSalaCodigo() {
        return salaCodigo;
    }

    public String getGanadorJugadorId() {
        return ganadorJugadorId;
    }

    public String getGanadorJugadorNombre() {
        return ganadorJugadorNombre;
    }

    public String getMotivoVictoria() {
        return motivoVictoria;
    }

    public Instant getIniciadaEn() {
        return iniciadaEn;
    }

    public Instant getFinalizadaEn() {
        return finalizadaEn;
    }

    public List<PuntajeJugadorEntity> getPuntajes() {
        return puntajes;
    }
}
