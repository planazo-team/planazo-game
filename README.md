# planazo-game

**Snake multijugador.** Salas, simulación de la partida y marcador. El servicio con el perfil de ejecución más distinto de todos: corre un bucle a 20 Hz, no responde peticiones sueltas.

| | |
|---|---|
| **Dueño** | Diego Rozo |
| **Retos** | **RT-3** (leaderboard en vivo) · **CC-2** (leaderboard concurrente) |
| **Datos** | Memoria (partidas) + Redis (marcador) — **nada en PostgreSQL** |
| **Puerto local sugerido** | `3003` HTTP · `8082` WebSocket |
| **Contexto general** | [planazo-frontend/docs/contexto.md](https://github.com/planazo-team/planazo-frontend/blob/main/docs/contexto.md) |

Es la épica más pesada del MVP: **10 de las historias** están aquí.

---

## Contrato con el gateway

El HTTP entra por [`planazo-api-gateway`](https://github.com/planazo-team/planazo-api-gateway), **pero el WebSocket no**: el frontend se conecta directo a este servicio (`NEXT_PUBLIC_GAME_URL`), igual que hace con `realtime`. Un gateway HTTP en medio de un canal de 20 Hz solo agrega latencia.

1. **Las rutas HTTP aquí NO llevan el prefijo `/api`.** El gateway recibe `/api/rooms` y reenvía `/rooms`.
2. **No valides el JWT en las rutas HTTP** — el gateway te pasa `x-user-id`, `x-user-role`, `x-user-place-id`.
3. **En el WebSocket sí te toca a ti:** el primer mensaje del cliente debe traer el token y tú lo verificas con el mismo `JWT_SECRET` del gateway. **Nunca aceptes el token por query string** (queda en logs); va en el primer mensaje del socket.

---

## Datos

```
Memoria   rooms[code] = { players[], snakes[], food[], tickId }
Redis     leaderboard:{roomId}      sorted set
```

**Nada en PostgreSQL, y es deliberado.** El estado es efímero: si el proceso se reinicia se pierden las partidas en curso y al negocio no le pasa nada. Meter una base de datos aquí sería pagar latencia por datos que no le importan a nadie en 5 minutos.

---

## API HTTP

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/rooms` | Crea la sala y devuelve un código de 4 letras |
| `POST` | `/rooms/:code/join` | Entra y recibe la lista de quién está dentro |
| `POST` | `/rooms/:code/start` | Arranca si hay al menos 2 jugadores |
| `GET` | `/rooms/hall-of-fame` | Mejores puntajes históricos |

Si la sala ya está llena, `join` responde **`409` con mensaje claro** ("la sala ya se llenó") — es criterio de aceptación de la historia, no un error genérico.

## WebSocket

```
WS /rooms/:code/play
→ { type: "AUTH", token }                 primer mensaje, siempre
→ { type: "INTENT", dir: "up"|"down"|"left"|"right" }
← { type: "STATE", snakes, food, scores, tick }
← { type: "DEATH", player_id }
← { type: "ROUND_END", winner, scores }
```

---

## Mecanismo de RT-3 — servidor autoritativo

```js
setInterval(() => {
  for (const room of activeRooms) {
    room.applyIntents();   // solo direcciones, nunca posiciones
    room.step();           // mover, comer, detectar colisiones
    broadcast(room.code, room.serialize());
  }
}, 50);                    // 20 Hz
```

El cliente envía **intenciones de movimiento, no posiciones**. Así nadie manipula la partida desde la consola del navegador: el servidor es el único que sabe dónde está cada serpiente.

**Un solo `setInterval` para todas las salas**, no uno por sala. Con un timer por sala, 20 salas son 20 timers compitiendo y el jitter se nota.

---

## Mecanismo de CC-2 — escritura atómica sobre el marcador

```
ZADD      leaderboard:{roomId} GT {score} player:{id}
ZREVRANGE leaderboard:{roomId} 0 9 WITHSCORES
```

Nunca se lee y reescribe la tabla completa, así que **no existe el *lost update*** (dos jugadores anotando a la vez y uno pisando al otro). El flag `GT` hace que solo suba, nunca baje.

### Desempate determinista

```
score = puntos × 1.000.000 + (1.000.000 − segundos_desde_inicio)
```

A igual puntaje gana quien llegó primero **según el reloj del servidor** — nunca según el del cliente. Todos los navegadores ven exactamente el mismo orden, que es lo que hace demostrable el reto.

---

## Eventos que emite

| Evento | Payload mínimo | Quién lo recibe |
|---|---|---|
| `ROOM.JOIN` | `{room_code, player_id, players[]}` | la sala |
| `SNAKE.SCORE` | `{room_code, player_id, score}` | la sala |
| `SNAKE.DEATH` | `{room_code, player_id}` | la sala |
| `ROUND.END` | `{room_code, winner, scores[]}` | la sala |

Estos van al bus para que `realtime` los persista (un jugador que se reconecta necesita saber qué se perdió). El estado de la partida a 20 Hz **no** va al bus: ese se difunde directo por el socket de este servicio.

---

## Lo que NO hace

- No guarda nada en PostgreSQL.
- No sabe qué es un cupo ni una promoción.
- No reparte premios — el "bote de créditos al ganador" quedó fuera del MVP (Release 3).

---

## Variables de entorno

```bash
PORT=3003
WS_PORT=8082
REDIS_URL=redis://localhost:6379
JWT_SECRET=<el mismo del gateway>      # para verificar el token en el WS
BUS_URL=redis://localhost:6379
```

`JWT_SECRET` tiene que ser **idéntico** al del gateway o los sockets rechazarán a todo el mundo.

---

## Definition of Done

1. **Recurso finito / concurrencia:** prueba con N jugadores anotando simultáneamente. Criterio: el marcador final coincide con la suma real, **cero puntajes perdidos**.
2. **Tiempo real:** probar desconectando y reconectando a mitad de partida — el jugador debe poder volver sin romper la sala (historia de R2, pero el canal tiene que aguantarlo desde el MVP).
3. La sala llena responde `409` legible, no un error genérico.

---

## Despliegue en Railway

1. New Project → Deploy from GitHub repo → `planazo-team/planazo-game`.
2. Agrega **Redis**; Railway inyecta `REDIS_URL`.
3. Railway soporta WebSockets sin configuración extra, pero **el proceso tiene que ser uno solo** por ahora: con varias instancias, las salas en memoria quedarían repartidas y un jugador podría caer en la instancia equivocada. Si en algún momento hace falta escalar, el paso es mover el estado de sala a Redis — no antes.
4. Pásale las dos URLs a Juan Diego: la HTTP es el `GAME_URL` del gateway, la WS es el `NEXT_PUBLIC_GAME_URL` del frontend.
