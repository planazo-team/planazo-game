package com.planazo.minijuego.infrastructure.adapter.in.websocket;

import com.planazo.minijuego.domain.exception.SalaLlenaException;
import com.planazo.minijuego.domain.model.Direccion;
import com.planazo.minijuego.domain.port.in.JugarPartidaUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptadoresWebSocketTest {

    private final JugarPartidaUseCase jugar = mock(JugarPartidaUseCase.class);
    private final SesionJuegoRegistry sesiones = new SesionJuegoRegistry();
    private final Principal ana = () -> "j1";

    @Test
    void elControlador_registraLaSesion_yEnrutaLosComandos() {
        GameWebSocketController c = new GameWebSocketController(jugar, sesiones);
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId("sess-1");

        c.registrarSesion("s1", ana, accessor);
        assertThat(sesiones.obtener("sess-1")).isEqualTo(new SesionJuegoRegistry.SesionInfo("s1", "j1"));
        verify(jugar).manejarReconexion("s1", "j1");

        c.mover("s1", new MoverRequest("ARRIBA"), ana);
        c.mover("s1", new MoverRequest("DIAGONAL"), ana); // dirección inválida: se ignora
        c.mover("s1", new MoverRequest(null), ana);
        verify(jugar).moverSerpiente("s1", "j1", Direccion.ARRIBA);

        c.pausar("s1", ana);
        c.reanudar("s1", ana);
        verify(jugar).pausarSerpiente("s1", "j1");
        verify(jugar).reanudarSerpiente("s1", "j1");
        assertThat(c.ping("s1")).isEqualTo("pong");
        assertThat(c.manejarError(new SalaLlenaException("SALA-01", 2)).codigo()).isNotBlank();
    }

    private static SessionDisconnectEvent desconexion(String sesion) {
        SessionDisconnectEvent e = mock(SessionDisconnectEvent.class);
        when(e.getSessionId()).thenReturn(sesion);
        return e;
    }

    @Test
    void alCaerseElSocket_congelaAlJugador_salvoQueTengaOtraSesionAbierta() {
        WebSocketEventListener listener = new WebSocketEventListener(sesiones, jugar);
        sesiones.registrar("a", "s1", "j1");
        sesiones.registrar("b", "s1", "j1");

        listener.alDesconectar(desconexion("a")); // le queda la sesión b
        verify(jugar, never()).manejarDesconexion(any(), any());

        listener.alDesconectar(desconexion("b"));
        verify(jugar).manejarDesconexion("s1", "j1");

        listener.alDesconectar(desconexion("desconocida")); // sin registro: nada
    }

    @Test
    void siProcesarLaDesconexionFalla_noRevientaElListener() {
        doThrow(new IllegalStateException("sala cerrada")).when(jugar).manejarDesconexion("s1", "j1");
        sesiones.registrar("a", "s1", "j1");
        new WebSocketEventListener(sesiones, jugar).alDesconectar(desconexion("a"));
        verify(jugar).manejarDesconexion("s1", "j1");
    }
}
