# planazo-game (minijuego-service)

Microservicio de la épica **Minijuego** de Planazo, dueño: Diego Rozo. Está **en producción en Railway** (`https://planazo-game-production.up.railway.app`) e integrado en el frontend: la pestaña *Juego* de https://planazo-frontend.vercel.app es el cliente de este servicio (`test-client.html`) con el mismo diseño. Ver [Integración con Planazo](#integración-con-planazo).

Microservicio de la épica **Minijuego** de Planazo:
Snake multijugador en tiempo real. Implementa las 10 historias MVP
(SNK-01 a SNK-10, ver `Planazo-Minijuego-Backlog-Detallado.pdf`) sobre
Java 21 + Spring Boot 3, con salas pre-existentes de cupo fijo,
movimiento en tiempo real a 20 ticks/segundo (RT-3), condición de
victoria híbrida, y leaderboard persistente (CC-2).

El "por qué está organizado así" está en [ARQUITECTURA.md](./ARQUITECTURA.md)
— léelo antes de tocar el código, especialmente la sección de
concurrencia si vas a modificar `Sala` o `SalaRuntime`.

## Requisitos

- Java 21
- Maven 3.9+
- Docker (para Postgres y Redis en local)

## Levantar en local

```bash
cp .env.example .env        # y ajusta los puertos si hace falta
docker compose up -d
mvn spring-boot:run
```

El servicio queda en `http://localhost:8082`.

- Swagger UI: `http://localhost:8082/swagger-ui.html`
- Salud: `http://localhost:8082/actuator/health`
- Cliente de prueba: `http://localhost:8082/test-client.html` (ver la nota del camino 2)

## Cómo probar que funciona — dos caminos

### Camino 1: pruebas funcionales por Swagger (el contrato REST)

1. Abre `http://localhost:8082/swagger-ui.html`.
2. `GET /api/v1/auth/usuarios-de-prueba` — copia un `jugadorId` (p.ej. `j-ana`).
3. `POST /api/v1/auth/dev-token` con `{"jugadorId": "j-ana"}` — copia el `token` de la respuesta.
4. Botón **Authorize** (arriba a la derecha) → pega el token → Authorize.
5. `GET /api/v1/salas` — verás el catálogo de salas pre-seedeadas (SNK-01).
6. `POST /api/v1/salas/{salaId}/jugadores` — te une a la sala (SNK-01/03). Repite
   el flujo completo con un segundo `jugadorId` (p.ej. `j-bruno`) para que la
   partida arranque sola (SNK-05: hacen falta 2 jugadores).
7. `GET /api/v1/salas/{salaId}` — verás `estado: "EN_CURSO"` y a ambos jugadores.
8. Deja pasar 3 minutos (o revisa los logs: el tick corre solo) y vuelve a
   consultar `GET /api/v1/leaderboard` y `GET /api/v1/historico/mio` — verás
   el resultado ya persistido (SNK-08/09/10, CC-2), sin que tú hayas tenido
   que mover ninguna serpiente por REST — el movimiento es por WebSocket
   (ver el camino 2), esto solo prueba el contrato de salas/leaderboard/histórico.

### Camino 2: jugar de verdad

> **Nota (8 oct 2026):** desde que el estado de la sala se publica al bus (`RedisGameBroadcaster`) en vez de a los tópicos STOMP, `test-client.html` **ya no recibe el tablero** si no hay un `planazo-realtime` conectado al mismo Redis. Para jugar de verdad usa el frontend (`planazo-frontend`, pestaña *Juego*), que recibe el estado por realtime, o corre realtime en local. Los pasos de abajo describen el cliente de prueba tal como funcionaba con STOMP.

1. Con el servicio corriendo, abre `http://localhost:8082/test-client.html`
   en **dos pestañas o dos navegadores distintos** (dos jugadores).
2. En cada una: elige un usuario de prueba distinto → "Obtener JWT" → elige
   la misma sala en ambas → "Unirme".
3. En cuanto el segundo jugador se une, la partida arranca sola. Usa las
   flechas del teclado en cada pestaña para mover tu serpiente — verás a
   la otra serpiente moverse en tiempo real en tu propio tablero (RT-3).
4. Cierra una pestaña a mitad de partida: esa serpiente se congela (no
   desaparece) durante 15 segundos (SNK-07) — reábrela y reconecta antes
   de que se agote esa ventana para seguir exactamente donde iba.
5. Cuando quede un solo jugador vivo (o se agoten los 3 minutos), verás el
   resultado final directamente en la pantalla (SNK-10).

## Compilar y correr las pruebas

```bash
mvn verify
```

Corre `SalaTest` (las reglas de negocio del juego — unirse, moverse,
chocar, ganar — probadas sin Spring ni base de datos, en milisegundos) y
`ArchitectureRulesTest` (ArchUnit: falla el build si `domain` termina
importando Spring, JPA o `infrastructure`).

> **Nota de este entorno de generación de código:** este proyecto se
> escribió sin acceso a Maven Central (la sandbox donde se generó no
> tenía esa salida de red habilitada), así que `mvn verify` no se pudo
> ejecutar aquí para confirmarlo de punta a punta. Sí se verificó
> manualmente que cada `package` declarado coincide con su carpeta real,
> que las llaves de cada archivo Java están balanceadas, y que todo
> `.yml`/`.xml` es sintácticamente válido. El workflow de GitHub Actions
> (`.github/workflows/ci.yml`) corre `mvn verify` en cada push — la
> primera vez que subas esto a GitHub es cuando se confirma la
> compilación real. Si algo no compila, lo más probable es un import o
> una firma de método de una librería (Spring Security OAuth2, Nimbus
> JOSE, springdoc) que cambió de versión — revisa el mensaje del error de
> CI, casi siempre es autoexplicativo.

## Integración con Planazo

Así encaja este servicio en la arquitectura del proyecto (`planazo-frontend/docs/contexto.md`):

| Pieza | Cómo |
|---|---|
| **Entrada HTTP** | El gateway reenvía `/api/salas*`, `/api/leaderboard*` y `/api/historico*` a `/api/v1/...` de este servicio **con el `Authorization: Bearer`** del usuario. No usa `x-gateway-key`: la barrera es el JWT. |
| **JWT** | Lo firma el gateway (HS256) con los claims `sub` (`u1`…`u6`), `name`, `nombre`, `role`, `placeId`. Este servicio lo valida con `JWT_SECRETO`, que debe ser **idéntico** al `JWT_SECRET` del gateway. Lee `sub` y `nombre`. Los usuarios `j-ana`… solo existen para `POST /api/v1/auth/dev-token`. |
| **Comandos en vivo** | STOMP sobre `/ws` (SockJS; WebSocket nativo en `/ws/websocket`), con el JWT en el header `Authorization` del frame `CONNECT`: `/app/salas/{id}/registrar-sesion`, `/app/salas/{id}/mover`, errores en `/user/queue/errores`. |
| **Estado en vivo** | `RedisGameBroadcaster` publica al canal `planazo.events` el snapshot de cada tick (`GAME.STATE_UPDATE`, tópico `salas:<salaId>`) y la lista de salas (`LOBBY.ROOMS_UPDATE`, tópico `salas`); `PartidaEventPublisherAdapter` publica `GAME.PARTIDA_FINALIZADA` (tópico `global`). `planazo-realtime` los persiste y los reparte a los navegadores. |
| **Datos** | PostgreSQL, esquema `minijuego` (Flyway) en el Postgres del proyecto de Railway, compartido con realtime (esquema `public`). Redis db 1 para el leaderboard. |
| **Producción** | Railway, desde GitHub (`main`, builder Dockerfile), dominio público `planazo-game-production.up.railway.app`, puerto `8082`. Variables: `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, `REDISHOST`, `REDISPORT`, `REDISPASSWORD` (referencias al Postgres y Redis del proyecto) y `JWT_SECRETO`. El gateway lo alcanza por `GAME_URL=http://planazo-game.railway.internal:8082`; el frontend por `NEXT_PUBLIC_GAME_URL=wss://planazo-game-production.up.railway.app`. |

Probado en producción el 8 de octubre: cuatro usuarios del gateway entraron a `Sala Cartagena` desde el frontend, la cuenta regresiva arrancó al llenarse y la partida corrió con el estado llegando por realtime.

## Estructura de carpetas

```
src/main/java/com/planazo/minijuego/
├── domain/                         # reglas de negocio puras, sin frameworks
│   ├── model/                      # Sala (agregado raíz), Serpiente, value objects
│   ├── port/in/                    # casos de uso (interfaces)
│   ├── port/out/                   # lo que el dominio necesita del exterior (interfaces)
│   └── exception/
├── application/
│   ├── service/                    # implementaciones de domain.port.in
│   └── dto/                        # snapshots para difundir (EstadoJuegoDTO, etc.)
└── infrastructure/
    ├── adapter/in/rest/            # controladores REST (+ dto/ para lo puramente de transporte)
    ├── adapter/in/websocket/       # STOMP: RT-3, registro de sesión, desconexión
    ├── adapter/out/runtime/        # el game loop: SalaRuntime, SalaRuntimeRegistry
    ├── adapter/out/persistence/    # JPA: catálogo de salas + resultados de partida
    ├── adapter/out/leaderboard/    # Redis Sorted Set (CC-2)
    ├── adapter/out/messaging/      # Redis pub/sub (planazo.events): event/ publisher/
    ├── config/                     # OpenAPI, WebSocket/STOMP, Redis
    └── security/                   # JWT stateless (HS256), dev-token, interceptor STOMP
```

## Usuarios de prueba (solo para `dev-token`)

En Planazo los jugadores son los nueve usuarios semilla del gateway (`u1`…`u6` clientes, `b1`…`b3` negocios); su nombre viene en el claim `nombre` del JWT. Los de abajo solo sirven para probar el servicio solo, por Swagger.

| jugadorId  | nombre  |
|------------|---------|
| j-ana      | Ana     |
| j-bruno    | Bruno   |
| j-camila   | Camila  |
| j-diego    | Diego   |
| j-elena    | Elena   |
| j-fabian   | Fabián  |

## De aquí a producción

- ✅ `JWT_SECRETO` ya es el mismo secreto del gateway en Railway (hecho el 8 oct).
- Decidir si `AuthDevController` (el endpoint `/api/v1/auth/dev-token`)
  se apaga por completo en producción, o se deja detrás de un perfil
  Spring que nunca se active fuera de `dev`/`local`. Hoy está siempre
  activo porque el equipo necesita poder demostrar el servicio sin el
  microservicio de autenticación real corriendo.
- Restringir `SecurityConfig.corsConfigurationSource()` a los orígenes
  reales del Panel del establecimiento en vez de `*`.
- Revisar `Sala.ANCHO_TABLERO`/`ALTO_TABLERO`/`SEGUNDOS_LIMITE_PARTIDA`:
  hoy son constantes; la historia Should "rondas cronometradas" pediría
  hacerlas configurables por sala.

## Servicios relacionados

- **planazo-api-gateway** (Juan Diego): emite el JWT que este servicio
  valida (mismo secreto HS256, ver `ARQUITECTURA.md`) y le reenvía las
  rutas `/api/salas`, `/api/leaderboard` y `/api/historico`.
- **planazo-realtime** (Camilo): recibe por el bus `GAME.STATE_UPDATE`,
  `LOBBY.ROOMS_UPDATE` y `GAME.PARTIDA_FINALIZADA` y los reparte al
  frontend.
- **planazo-frontend** (Juan Diego): la pestaña *Juego* es
  `test-client.html` llevado a Next.js con el mismo diseño
  (`src/app/cliente/juego`, `src/components/TableroSnake.tsx`,
  `src/lib/game/client.ts`).
- Con **booking** y **promo** no comparte nada: ni base de datos ni código.
