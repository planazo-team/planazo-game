package com.planazo.minijuego.infrastructure.adapter.in.rest;

import com.planazo.minijuego.domain.model.ResultadoPartida;
import com.planazo.minijuego.domain.port.in.ConsultarHistoricoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Historia Should "ver el histórico de mis partidas" — se incluye porque la persistencia ya existe por CC-2. */
@RestController
@RequestMapping("/api/v1/historico")
@Tag(name = "Histórico", description = "Historia Should — historial de partidas del jugador autenticado")
@SecurityRequirement(name = "bearer-jwt")
public class HistoricoController {

    private final ConsultarHistoricoUseCase consultarHistoricoUseCase;

    public HistoricoController(ConsultarHistoricoUseCase consultarHistoricoUseCase) {
        this.consultarHistoricoUseCase = consultarHistoricoUseCase;
    }

    @GetMapping("/mio")
    @Operation(summary = "Historial de partidas del jugador autenticado (por su JWT, nunca por un id ajeno)")
    public ApiResponse<List<ResultadoPartida>> misPartidas(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.de(consultarHistoricoUseCase.historicoDeJugador(jwt.getSubject()));
    }
}
