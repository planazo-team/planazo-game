package com.planazo.minijuego.infrastructure.adapter.in.rest;

import com.planazo.minijuego.domain.model.ResumenSala;
import com.planazo.minijuego.domain.port.in.GestionarSalasUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SNK-01, SNK-02, SNK-03. Recursos en sustantivo plural
 * ({@code /salas}, sub-recurso {@code /salas/{id}/jugadores}) y
 * versionado explícito ({@code /api/v1}), como exige
 * {@code ARQUITECTURA.md}.
 */
@RestController
@RequestMapping("/api/v1/salas")
@Tag(name = "Salas", description = "SNK-01/02/03 — salas pre-existentes de cupo fijo")
@SecurityRequirement(name = "bearer-jwt")
public class SalaController {

    private final GestionarSalasUseCase gestionarSalasUseCase;

    public SalaController(GestionarSalasUseCase gestionarSalasUseCase) {
        this.gestionarSalasUseCase = gestionarSalasUseCase;
    }

    @GetMapping
    @Operation(summary = "SNK-01: listar las salas pre-existentes con su cupo disponible")
    public ApiResponse<List<ResumenSala>> listar() {
        return ApiResponse.de(gestionarSalasUseCase.listarSalas());
    }

    @GetMapping("/{salaId}")
    @Operation(summary = "SNK-02: ver el detalle de una sala, incluyendo quién más está en ella")
    public ApiResponse<ResumenSala> obtener(@PathVariable String salaId) {
        return ApiResponse.de(gestionarSalasUseCase.obtenerSala(salaId));
    }

    @PostMapping("/{salaId}/jugadores")
    @Operation(summary = "SNK-01/03: unirse a la sala. 409 SALA_LLENA si ya no hay cupo (rechazo claro).")
    public ResponseEntity<ApiResponse<ResumenSala>> unirse(@PathVariable String salaId, @AuthenticationPrincipal Jwt jwt) {
        String jugadorId = jwt.getSubject();
        String jugadorNombre = jwt.getClaimAsString("nombre");
        ResumenSala resumen = gestionarSalasUseCase.unirseASala(salaId, jugadorId, jugadorNombre == null ? jugadorId : jugadorNombre);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.de(resumen));
    }

    @DeleteMapping("/{salaId}/jugadores/{jugadorId}")
    @Operation(summary = "SNK-06: salir de la sala o de la partida sin romperla")
    public ResponseEntity<Void> salir(@PathVariable String salaId, @PathVariable String jugadorId, @AuthenticationPrincipal Jwt jwt) {
        if (!jugadorId.equals(jwt.getSubject())) {
            throw new AccessDeniedException("No puedes hacer salir a otro jugador de la sala.");
        }
        gestionarSalasUseCase.salirDeSala(salaId, jugadorId);
        return ResponseEntity.noContent().build();
    }
}
