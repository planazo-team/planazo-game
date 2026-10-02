package com.planazo.minijuego.infrastructure.adapter.in.rest;

import com.planazo.minijuego.domain.model.EntradaLeaderboard;
import com.planazo.minijuego.domain.port.in.ConsultarLeaderboardUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** SNK-08 / RT-3 / CC-2. */
@RestController
@RequestMapping("/api/v1/leaderboard")
@Tag(name = "Leaderboard", description = "SNK-08 / CC-2 — marcador global respaldado por Redis Sorted Set")
@SecurityRequirement(name = "bearer-jwt")
public class LeaderboardController {

    private final ConsultarLeaderboardUseCase consultarLeaderboardUseCase;

    public LeaderboardController(ConsultarLeaderboardUseCase consultarLeaderboardUseCase) {
        this.consultarLeaderboardUseCase = consultarLeaderboardUseCase;
    }

    @GetMapping
    @Operation(summary = "SNK-08: top N del marcador global, de mayor a menor puntaje acumulado")
    public ApiResponse<List<EntradaLeaderboard>> top(@RequestParam(defaultValue = "10") int limite) {
        return ApiResponse.de(consultarLeaderboardUseCase.topGlobal(limite));
    }
}
