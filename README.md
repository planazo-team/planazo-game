# minijuego-service

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
- Docker (para Postgres, Redis y RabbitMQ en local)

## Levantar en local

```bash
cp .env.example .env        # y ajusta los puertos si hace falta
docker compose up -d
mvn spring-boot:run
```

El servicio queda en `http://localhost:8082`.

- Swagger UI: `http://localhost:8082/swagger-ui.html`
- Salud: `http://localhost:8082/actuator/health`
- Panel de RabbitMQ: `http://localhost:15673` (usuario/clave: `guest`/`guest`)

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
    ├── adapter/out/messaging/      # RabbitMQ: event/ publisher/
    ├── config/                     # OpenAPI, WebSocket/STOMP, Redis, RabbitMQ
    └── security/                   # JWT stateless (HS256), dev-token, interceptor STOMP
```

## Usuarios de prueba (quemados — decisión de producto del MVP)

| jugadorId  | nombre  |
|------------|---------|
| j-ana      | Ana     |
| j-bruno    | Bruno   |
| j-camila   | Camila  |
| j-diego    | Diego   |
| j-elena    | Elena   |
| j-fabian   | Fabián  |

## De aquí a producción

- Cambiar `JWT_SECRETO` por un valor real y compartido de forma segura
  con el microservicio de Registro y autenticación (nunca el valor de
  `.env.example`).
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

- **Registro y autenticación** (Juan Melo): en producción emite el JWT
  real que este servicio valida (mismo secreto HS256 — ver
  `ARQUITECTURA.md`). Mientras no esté integrado, este servicio usa sus
  propios usuarios de prueba.
- **Mapa y descubrimiento / Panel del establecimiento** (Diego Rozo,
  repo hermano `mapa-descubrimiento-service`): no comparten base de
  datos ni código; si algún día necesitan comunicarse, es por evento
  (`planazo.events`) o por API pública.
