package com.planazo.minijuego.domain.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Estado mutable de un jugador dentro de una {@link Sala}.
 *
 * <p>La serpiente no avanza sola: cada comando de movimiento del jugador
 * la mueve exactamente una celda (ver {@link Sala#moverJugador}). Nace con
 * {@link #LONGITUD_INICIAL} segmentos para que su cuerpo sea un obstáculo
 * real contra el que los demás pueden chocar.
 *
 * <p><b>Sobre thread-safety:</b> esta clase NO es thread-safe a propósito, y
 * no necesita serlo. Una {@code Serpiente} solo existe dentro del mapa de
 * serpientes de una {@link Sala}, y una {@code Sala} completa solo es tocada
 * por el hilo dueño de esa sala ({@code SalaRuntime}, un hilo, una sala, sin
 * excepciones). Envolver cada campo en algo atómico aquí sería ruido: el
 * lock real es "eres el único hilo que puede llamar estos métodos", no un
 * {@code synchronized} — ver {@code ARQUITECTURA.md}, sección de
 * concurrencia, regla 4 (evitar locks cuando el diseño ya evita la carrera).
 */
public class Serpiente {

    public static final int LONGITUD_INICIAL = 4;

    private final String jugadorId;
    private final String jugadorNombre;
    private final Deque<Posicion> cuerpo = new ArrayDeque<>();
    private Direccion direccion;
    private EstadoSerpiente estado;
    private int puntaje;
    private long ultimoCambioEstado;
    private boolean conectado = true;

    public Serpiente(String jugadorId, String jugadorNombre, Posicion cabeza, Direccion direccionInicial) {
        this.jugadorId = jugadorId;
        this.jugadorNombre = jugadorNombre;
        reaparecer(cabeza, direccionInicial);
    }

    /**
     * Deja la serpiente como recién nacida: cabeza en {@code cabeza}
     * mirando hacia {@code direccionInicial} y el resto del cuerpo detrás,
     * en línea recta. Se usa al nacer, al arrancar cada partida (los que
     * salieron en la sala de espera dejan huecos en los spawns) y en la
     * revancha.
     */
    public void reaparecer(Posicion cabeza, Direccion direccionInicial) {
        cuerpo.clear();
        Posicion segmento = cabeza;
        for (int i = 0; i < LONGITUD_INICIAL; i++) {
            cuerpo.addLast(segmento);
            segmento = new Posicion(segmento.x() - direccionInicial.deltaX(), segmento.y() - direccionInicial.deltaY());
        }
        this.direccion = direccionInicial;
        this.estado = EstadoSerpiente.VIVA;
        this.puntaje = 0;
        this.ultimoCambioEstado = System.currentTimeMillis();
    }

    /**
     * Una serpiente nunca puede dar media vuelta sobre su propio cuello:
     * moverse en la dirección opuesta la haría chocar consigo misma de
     * forma artificial. Esa tecla simplemente se ignora.
     */
    public boolean puedeMoverseHacia(Direccion nueva) {
        return nueva != null && !(cuerpo.size() > 1 && nueva.esOpuesta(direccion));
    }

    /** La celda a la que llegaría la cabeza moviéndose una vez hacia {@code hacia}, sin mutar nada todavía. */
    Posicion proximaCabeza(Direccion hacia) {
        return cabeza().trasladar(hacia);
    }

    void avanzar(Direccion hacia, Posicion nuevaCabeza, boolean comio) {
        this.direccion = hacia;
        cuerpo.addFirst(nuevaCabeza);
        if (!comio) {
            cuerpo.removeLast();
        } else {
            puntaje += 10;
        }
    }

    public boolean estaConectado() {
        return conectado;
    }

    public void congelarPorDesconexion() {
        this.conectado = false;
        if (estado == EstadoSerpiente.VIVA) {
            this.estado = EstadoSerpiente.DESCONECTADA_CONGELADA;
            this.ultimoCambioEstado = System.currentTimeMillis();
        }
    }

    public void reconectar() {
        this.conectado = true;
        if (estado == EstadoSerpiente.DESCONECTADA_CONGELADA) {
            this.estado = EstadoSerpiente.VIVA;
            this.ultimoCambioEstado = System.currentTimeMillis();
        }
    }

    public void eliminar() {
        this.estado = EstadoSerpiente.ELIMINADA;
        this.ultimoCambioEstado = System.currentTimeMillis();
    }

    public boolean segundosCongeladaSuperan(int segundosLimite) {
        if (estado != EstadoSerpiente.DESCONECTADA_CONGELADA) {
            return false;
        }
        return System.currentTimeMillis() - ultimoCambioEstado >= segundosLimite * 1000L;
    }

    public boolean ocupa(Posicion posicion) {
        return cuerpo.contains(posicion);
    }

    public boolean estaViva() {
        return estado == EstadoSerpiente.VIVA || estado == EstadoSerpiente.DESCONECTADA_CONGELADA;
    }

    public boolean esControlablePorJugador() {
        return estado == EstadoSerpiente.VIVA;
    }

    public Posicion cabeza() {
        return cuerpo.peekFirst();
    }

    public List<Posicion> cuerpoInmutable() {
        return List.copyOf(cuerpo);
    }

    public String jugadorId() {
        return jugadorId;
    }

    public String jugadorNombre() {
        return jugadorNombre;
    }

    public Direccion direccion() {
        return direccion;
    }

    public EstadoSerpiente estado() {
        return estado;
    }

    public int puntaje() {
        return puntaje;
    }

    public int longitud() {
        return cuerpo.size();
    }

    public static String nuevoId() {
        return UUID.randomUUID().toString();
    }
}
