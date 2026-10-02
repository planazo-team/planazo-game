/**
 * Seguridad stateless basada en JWT (principio SOA de ausencia de
 * estado). Dos particularidades de este servicio frente a
 * {@code mapa-descubrimiento-service}:
 *
 * <ul>
 *   <li>Valida el JWT con una clave HS256 compartida
 *       ({@code NimbusJwtDecoder.withSecretKey}) en vez de
 *       descubrimiento OIDC por {@code issuer-uri}. Así este servicio se
 *       puede levantar y probar solo, sin que el microservicio de
 *       Registro y autenticación esté corriendo — en producción, ambos
 *       servicios comparten el mismo secreto por configuración.</li>
 *   <li>{@code DevJwtTokenService} expone un atajo de solo-desarrollo
 *       para emitir un JWT válido para uno de los usuarios de prueba
 *       pre-seedeados ("vamos a quemar los usuarios"), sin implementar
 *       un login real — ese es trabajo del otro microservicio.</li>
 *   <li>{@code StompAuthChannelInterceptor} valida el mismo JWT en el
 *       frame STOMP {@code CONNECT}, porque el handshake HTTP inicial de
 *       SockJS no alcanza a proteger la sesión completa de WebSocket.</li>
 * </ul>
 */
package com.planazo.minijuego.infrastructure.security;
