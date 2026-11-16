package com.planazo.minijuego.infrastructure.adapter.in.rest;

import com.planazo.minijuego.infrastructure.adapter.in.rest.dto.DevTokenRequest;
import com.planazo.minijuego.infrastructure.adapter.in.rest.dto.DevTokenResponse;
import com.planazo.minijuego.infrastructure.security.DevJwtTokenService;
import com.planazo.minijuego.infrastructure.security.UsuarioDePrueba;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SOLO DESARROLLO. Emite JWTs válidos para los usuarios "quemados" del
 * MVP sin depender del microservicio real de Registro y autenticación —
 * ver el porqué en {@code infrastructure.security.package-info}. Nunca
 * queda expuesto sin autenticación en un ambiente real: este servicio no
 * se despliega tal cual a producción sin apagar este controlador o
 * reemplazar el {@code JwtDecoder} por uno que valide contra el emisor
 * real (ver README, sección "De aquí a producción"). Solo existe si
 * {@code planazo.seguridad.dev-token.habilitado=true} (DEV_TOKEN_HABILITADO):
 * apagado por defecto, encendido en el perfil {@code dev}.
 */
@RestController
@ConditionalOnProperty(name = "planazo.seguridad.dev-token.habilitado", havingValue = "true")
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth (solo desarrollo)", description = "Emisión de JWT de prueba para los usuarios pre-seedeados")
public class AuthDevController {

    private final DevJwtTokenService devJwtTokenService;

    public AuthDevController(DevJwtTokenService devJwtTokenService) {
        this.devJwtTokenService = devJwtTokenService;
    }

    @GetMapping("/usuarios-de-prueba")
    @Operation(summary = "Lista los usuarios pre-seedeados disponibles para pedir un token de desarrollo")
    public ApiResponse<List<UsuarioDePrueba>> usuariosDePrueba() {
        return ApiResponse.de(UsuarioDePrueba.TODOS);
    }

    @PostMapping("/dev-token")
    @Operation(summary = "Emite un JWT de desarrollo para uno de los usuarios pre-seedeados")
    public ResponseEntity<?> devToken(@RequestBody DevTokenRequest request) {
        UsuarioDePrueba usuario = UsuarioDePrueba.buscar(request.jugadorId());
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiError.de("USUARIO_DE_PRUEBA_NO_ENCONTRADO",
                            "'" + request.jugadorId() + "' no es uno de los usuarios de prueba. Consulta GET /api/v1/auth/usuarios-de-prueba."));
        }
        List<String> roles = (request.roles() == null || request.roles().isEmpty())
                ? List.of("jugador")
                : request.roles();
        String token = devJwtTokenService.generarToken(usuario.jugadorId(), usuario.nombre(), roles);
        return ResponseEntity.ok(ApiResponse.de(DevTokenResponse.de(token, usuario.jugadorId(), usuario.nombre())));
    }
}
