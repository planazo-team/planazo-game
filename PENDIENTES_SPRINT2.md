# Pendientes para cerrar al 100% — planazo-game (Minijuego)

Checklist de lo que falta en este microservicio para cubrir los 8 escenarios
de calidad del Sprint 2 y lo que pide el PDF. Última revisión del código:
9 de noviembre de 2026.

## 1. Autorización por rol (Escenario de calidad: Autorización)

- [x] Resuelto para el alcance actual de Game.

`SecurityConfig.java` ya tiene `@EnableMethodSecurity` y un
`JwtAuthenticationConverter` que traduce el claim `roles` del JWT a
`GrantedAuthority` con prefijo `ROLE_`. Un futuro
`@PreAuthorize("hasRole('NEGOCIO')")` en cualquier controlador ya
funcionaría sin tocar esta clase, y devolvería 403 (no 401) si el rol no
alcanza. El propio código documenta, correctamente, que Game hoy no tiene
ningún endpoint que distinga por rol — todos los recursos del juego son
del jugador autenticado, sea cual sea su rol — así que no hace falta
aplicar `@PreAuthorize` en ningún lado todavía. Si en algún momento se
agrega un endpoint solo para `negocio` (o similar), ahí sí se usa.

## 2. Observabilidad — métricas (Escenario de calidad: Observabilidad)

- [x] Hecho en este repo.

`application.yml` ya expone `health,info,metrics,prometheus`, y el
`pom.xml` trae `micrometer-registry-prometheus`. Además hay métricas de
negocio reales instrumentadas (no solo las técnicas automáticas de
Micrometer):
- `minijuego.partidas.jugadas` (contador, tag `motivo` con el motivo de
  victoria) — en `FinalizarPartidaService`.
- `minijuego.puntajes.registrados` (contador) — mismo archivo.
- `minijuego.jugadores.activos` (gauge por sala) — en
  `SalaRuntimeRegistry`.

Pendiente, pero ya no es de este repo: construir los 2 dashboards de
Grafana a partir de `/actuator/prometheus` (eso vive en `planazo-infra`,
que hoy no tiene ni Prometheus ni Grafana en el `docker-compose.yml`).

## 3. Mantenibilidad — SonarCloud en CI (Escenario de calidad: Mantenibilidad)

- [x] Código listo (en los 7 repos, no solo en este).
- [ ] Falta el paso manual (no es código): crear el proyecto en
      sonarcloud.io y guardar `SONAR_TOKEN` (y opcionalmente
      `SONAR_ORGANIZATION` / `SONAR_PROJECT_KEY`) como secret del repo en
      GitHub.

`.github/workflows/ci.yml` ya tiene el paso de SonarCloud, condicionado a
que el secret `SONAR_TOKEN` exista (`if: ${{ secrets.SONAR_TOKEN != '' }}`)
para no romper el build mientras no esté configurado. Se agregó además el
plugin `jacoco-maven-plugin` al `pom.xml` (no existía) para que el paso de
Sonar tenga cobertura real que reportar (`target/site/jacoco/jacoco.xml`),
verificado con `mvn verify`. El mismo patrón (con lcov en vez de JaCoCo) se
replicó en api-gateway, booking, promo, realtime e infra — detalle completo
en `planazo-infra/ESCENARIO-05-MANTENIBILIDAD.md`, incluyendo los pasos
manuales exactos que faltan en sonarcloud.io/GitHub.

## 4. Disponibilidad — escalamiento horizontal (Escenario de calidad: Disponibilidad)

- [ ] Sigue pendiente.

No ha cambiado desde la última revisión: `docker-compose.yml` en
`planazo-infra` sigue corriendo una sola instancia por servicio, sin
`replicas` ni balanceador, y la decisión de diseño (sticky sessions vs.
Redis Pub/Sub para que el estado de una sala se pueda servir desde más de
una instancia de Game) sigue sin tomarse.

## 5. Rendimiento / Latencia (Escenario de calidad: Rendimiento)

- [ ] Sigue pendiente.

`k6/cc2-game-score.js` (en `planazo-infra`) sigue sin umbrales de
latencia: solo tiene un `Trend('score_final')` para el puntaje, nada que
mida el tiempo de respuesta del ciclo INTENT → STATE. Falta agregar un
`Trend` de latencia con thresholds p95/p99 antes del 13 de noviembre.

## 6. Jugabilidad — la serpiente no debe avanzar sola

- [x] Hecho y verificado en el código.

Confirmado end-to-end:
- `Serpiente.cambiarDireccion(nueva)` gira y deja lista la dirección; ya
  no hay avance implícito por tiempo.
- `Sala.moverJugador(jugadorId, direccion)` gira **y** avanza una celda
  en el mismo gesto (`avanzarUnaCelda`), de inmediato. Dar media vuelta
  sobre el propio cuello se ignora en silencio y no cuenta como paso.
- `Sala.tick()` ya NO mueve a ninguna serpiente — solo resuelve cuenta
  regresiva, expiración de congelamientos por desconexión (SNK-07) y el
  tiempo límite de la partida (SNK-09). El propio javadoc lo dice: *"El
  avance de las serpientes NO vive aquí desde que dejó de ser automático."*
- `SalaRuntime.aplicarComandosDeMovimiento()` (antes `aplicarGiros()`)
  aplica cada comando encolado uno por uno llamando a `moverJugador` — es
  la única vía por la que una serpiente se mueve. El tick de 50 ms sigue
  existiendo, pero ahora solo sirve para vaciar la cola de comandos y
  difundir el reloj/cuenta regresiva, no para mover a nadie.
- `SalaTest.java` ya tiene casos para esto:
  `cadaComandoValido_avanzaExactamenteUnaCeldaYNoMas` y
  `moverJugador_giraYAvanzaDeInmediatoEnElMismoComando`.

Nota: no pude correr `mvn verify` desde aquí porque esta terminal no
tiene Maven instalado — la verificación de arriba es por lectura directa
del código, no por tests corridos. Vale la pena que confirmes con
`mvn verify` en tu máquina (o revisando que el CI pase en el próximo
push) antes de darlo por cerrado del todo.

---

*Quedan 2 pendientes reales: el escalamiento horizontal (punto 4) y los
umbrales de latencia del k6 (punto 5). Los demás están resueltos o solo
les falta un paso manual fuera del código (el secret de SonarCloud).*
