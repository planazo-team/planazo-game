package com.planazo.minijuego.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación de la API en {@code /swagger-ui.html}. El esquema
 * {@code bearer-jwt} es lo que permite probar los endpoints protegidos
 * directamente desde Swagger: se pega el token que devuelve
 * {@code POST /api/v1/auth/dev-token} en el botón "Authorize" y todas las
 * llamadas posteriores lo incluyen solas.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Minijuego (Snake multijugador) — Planazo",
                version = "v1",
                description = """
                        API del microservicio de la épica Minijuego de Planazo (dueño: Diego Rozo).
                        Cubre las 10 historias MVP (SNK-01 a SNK-10): salas pre-existentes de cupo
                        fijo, unirse/salir, movimiento y game loop en tiempo real (RT-3), condición
                        de victoria híbrida, y leaderboard persistente (CC-2).

                        Para pruebas funcionales sin depender del microservicio de Registro y
                        autenticación: usa POST /api/v1/auth/dev-token con uno de los usuarios de
                        prueba pre-seedeados (ver README) para obtener un JWT válido, autorízalo
                        con el botón "Authorize" de esta página, y ya puedes probar el resto de la
                        API. Para jugar de verdad (no solo probar el contrato REST), abre
                        /test-client.html en el navegador.
                        """,
                contact = @Contact(name = "Diego Rozo — épica Minijuego")
        )
)
@SecurityScheme(
        name = "bearer-jwt",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
