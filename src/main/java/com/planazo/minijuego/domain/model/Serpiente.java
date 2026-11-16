package com.planazo.minijuego.domain.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Estado mutable de un jugador dentro de una {@link Sala}.
 *
 * <p>Snake clásico: la serpiente avanza sola una celda en cada paso de la
 * partida (ver {@link Sala#tick()}); el jugador solo elige hacia dónde gira
 * ({@link #girarHacia}). Nace con
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
    /** Giros que se recuerdan entre pasos: "arriba + izquierda" rápido entra en dos pasos seguidos. */
    public static final int MAX_GIROS_PENDIENTES = 2;
    /** Pasos que la serpiente se queda quieta al volver de una pausa o desconexión, para que el jugador se ubique. */
    public static final int PASOS_DE_RESPIRO_AL_VOLVER = 4;

    private final String jugadorId;
    private final String jugadorNombre;
    private final Deque<Posicion> cuerpo = new ArrayDeque<>();
    private Direccion direccion;
    private final Deque<Direccion> girosPendientes = new ArrayDeque<>();
    private EstadoSerpiente estado;
    private int puntaje;
    private long ultimoCambioEstado;
    private boolean conectado = true;
    private boolean pausada;
    private int pasosDeRespiro;

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
        this.girosPendientes.clear();
        this.estado = EstadoSerpiente.VIVA;
        this.puntaje = 0;
        this.pasosDeRespiro = 0;
        this.ultimoCambioEstado = System.currentTimeMillis();
        if (pausada || !conectado) {
            // Arranca la partida sin el jugador frente a la pantalla: nace quieta.
            this.estado = EstadoSerpiente.DESCONECTADA_CONGELADA;
        }
    }

    /**
     * Una serpiente nunca puede dar media vuelta sobre su propio cuello:
     * moverse en la dirección opuesta la haría chocar consigo misma de
     * forma artificial. Esa tecla simplemente se ignora.
     */
    public boolean puedeMoverseHacia(Direccion nueva) {
        return nueva != null && !(cuerpo.size() > 1 && nueva.esOpuesta(direccion));
    }

    /**
     * El jugador pide girar. El giro se aplica en el próximo paso; se valida
     * contra el último giro pendiente (o la dirección actual), así que dar
     * media vuelta sobre el cuello o repetir la misma dirección se ignora.
     *
     * @return true si el giro quedó en cola.
     */
    public boolean girarHacia(Direccion nueva) {
        if (nueva == null || girosPendientes.size() >= MAX_GIROS_PENDIENTES) {
            return false;
        }
        Direccion referencia = girosPendientes.isEmpty() ? direccion : girosPendientes.peekLast();
        if (nueva == referencia || (cuerpo.size() > 1 && nueva.esOpuesta(referencia))) {
            return false;
        }
        girosPendientes.addLast(nueva);
        return true;
    }

    /** Dirección del próximo paso: el siguiente giro pendiente, o seguir derecho. No muta nada. */
    Direccion proximaDireccion() {
        Direccion giro = girosPendientes.peekFirst();
        return giro != null ? giro : direccion;
    }

    /** Consume el giro que se acaba de usar en este paso. */
    void consumirGiro() {
        girosPendientes.pollFirst();
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

    /** Volvió el socket. Si además pausó (la pestaña sigue oculta), sigue quieta hasta {@link #reanudar()}. */
    public void reconectar() {
        this.conectado = true;
        descongelarSiCorresponde();
    }

    /**
     * El jugador dejó la pantalla (cambió de pestaña o de app) con el socket
     * vivo: la serpiente se queda quieta donde está, igual que congelada,
     * hasta que vuelva ({@link #reanudar()}).
     */
    public void pausar() {
        this.pausada = true;
        this.girosPendientes.clear();
        if (estado == EstadoSerpiente.VIVA) {
            this.estado = EstadoSerpiente.DESCONECTADA_CONGELADA;
            this.ultimoCambioEstado = System.currentTimeMillis();
        }
    }

    /** El jugador volvió a la pantalla: retoma exactamente donde iba, tras un respiro corto. */
    public void reanudar() {
        this.pausada = false;
        descongelarSiCorresponde();
    }

    private void descongelarSiCorresponde() {
        if (estado == EstadoSerpiente.DESCONECTADA_CONGELADA && conectado && !pausada) {
            this.estado = EstadoSerpiente.VIVA;
            this.pasosDeRespiro = PASOS_DE_RESPIRO_AL_VOLVER;
            this.ultimoCambioEstado = System.currentTimeMillis();
        }
    }

    public boolean estaPausada() {
        return pausada;
    }

    /**
     * ¿Se queda quieta en este paso? Recién vuelta de una pausa, la serpiente
     * espera {@link #PASOS_DE_RESPIRO_AL_VOLVER} pasos antes de avanzar. Cada
     * llamada consume uno.
     */
    boolean tomarRespiro() {
        if (pasosDeRespiro <= 0) {
            return false;
        }
        pasosDeRespiro--;
        return true;
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
