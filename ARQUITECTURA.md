# Arquitectura de minijuego-service

Arquitectura **hexagonal (puertos y adaptadores)**, igual que
`mapa-descubrimiento-service` — mismos seis principios de SOA, misma
razón para cada carpeta. Este documento se concentra en lo que es
**distinto** en este microservicio (el game loop en tiempo real) y
remite al servicio hermano para lo que es idéntico.

## Regla de oro

> El código se reescribe; la arquitectura se paga durante años.

## Los seis principios, aplicados aquí

**1. Bajo acoplamiento.** `domain` no importa Spring, JPA, Redis, AMQP ni
`infrastructure`. `application` no importa `infrastructure`. Lo verifica
`ArchitectureRulesTest` (ArchUnit) en cada `mvn verify`.

**2. Abstracción de servicios.** Lo único público hacia afuera del
microservicio es el contrato REST versionado (`/api/v1/...`), los
mensajes STOMP (`/topic/salas/{id}`, `/app/salas/{id}/...`) y el evento
`PartidaFinalizada` en `planazo.events`. Nadie fuera de este servicio ve
sus tablas ni sus clases internas — ni siquiera el estado en memoria del
game loop.

**3. Autonomía.** Su propia base de datos, su propio `pom.xml` sin
módulo padre compartido con los demás microservicios de Planazo, su
propio `Dockerfile`. Si necesita algo de otro servicio (p.ej. validar
que un usuario existe de verdad), lo pide por evento o por su API
pública — nunca leyendo su base de datos.

**4. Ausencia de estado — con una excepción documentada y deliberada.**
La seguridad es JWT stateless. Los casos de uso en `application.service`
no guardan nada entre peticiones. La excepción es, por diseño, **todo el
estado de una partida en curso**: qué tan larga es cada serpiente, hacia
dónde va, cuánta comida queda. Ese estado *tiene* que vivir en algún
lado mientras la partida corre — es lo que hace del Minijuego un
microservicio distinto a uno CRUD. La disciplina no está en evitar el
estado (sería imposible), está en **quién puede tocarlo y cómo**: ver la
sección de concurrencia más abajo.

**5. Facilidad de descubrimiento.** Cada paquete tiene su
`package-info.java`. Swagger UI documenta la API REST; el cliente de
prueba (`/test-client.html`) documenta, jugando, el protocolo STOMP.

**6. Compatibilidad con estándares.** REST + JSON + HTTP estándar; OAuth2
Resource Server / JWT para autenticación (con una particularidad: HS256
con secreto compartido en vez de descubrimiento OIDC — ver más abajo);
STOMP sobre WebSocket (protocolo estándar, no un formato inventado);
AMQP con el mismo sobre `DomainEvent` que el resto de Planazo.

## Por qué JWT HS256 con secreto compartido, y no OIDC por `issuer-uri`

`mapa-descubrimiento-service` valida contra el `issuer-uri` del
microservicio de Registro y autenticación (descubrimiento OIDC
estándar). Este servicio necesita poder levantarse y probarse **solo**,
sin que ese otro microservicio esté corriendo — así lo pidió el equipo
explícitamente para poder hacer pruebas funcionales por Swagger o
jugando de verdad sin depender de nadie más. Un `NimbusJwtDecoder` con
una clave HS256 compartida por configuración logra exactamente eso: en
producción, ambos servicios comparten el mismo secreto (`JWT_SECRETO`) y
un token real, emitido por el servicio de autenticación, es válido aquí
sin cambiar una línea de código — el día que ese servicio exista, este
no necesita ningún cambio, solo la variable de entorno correcta.

`DevJwtTokenService` + `POST /api/v1/auth/dev-token` son el atajo de
**solo desarrollo** que hace esto posible hoy: firman un JWT con la
misma clave para uno de los usuarios "quemados" (pre-seedeados) — la
decisión de producto explícita de que el MVP no tiene auto-registro para
jugar, hay una lista fija de usuarios reales.

## Concurrencia: el game loop en tiempo real (RT-3)

Esta es la parte que no tiene equivalente en `mapa-descubrimiento-service`
y merece su propia sección.

### El problema

Una partida de Snake con hasta 6 jugadores necesita: (a) aplicar
movimientos a ~20 ticks/segundo sin que se note lag, (b) que unirse o
salir de una sala dé una respuesta HTTP inmediata y correcta (SNK-03:
rechazo claro si está llena), y (c) que nunca dos hilos muten la misma
partida a la vez de forma que se corrompa (una serpiente con dos cabezas,
un jugador contado dos veces).

### La solución: un "actor" por sala

Cada sala pre-existente tiene exactamente un `SalaRuntime`
(`infrastructure.adapter.out.runtime`), dueño exclusivo de su agregado
`Sala`. Tres reglas, siempre las mismas, nunca una excepción:

1. **El movimiento nunca toma lock para encolarse.** Llega desde
   cualquier hilo de WebSocket a una `ConcurrentLinkedQueue` — estructura
   ya thread-safe por diseño, sin necesitar sincronización adicional. El
   único consumidor de esa cola es el hilo del game loop, que la drena al
   principio de cada uno de sus propios ticks.
2. **Todo lo demás (unirse, salir, desconexión, reconexión, y el tick
   mismo) toma el mismo `ReentrantLock`, siempre en el mismo orden,
   nunca anidado.** Unirse/salir necesitan devolver una respuesta
   síncrona inmediata (SNK-03), así que no pueden pasar por la cola de
   comandos como el movimiento — pero como ocurren órdenes de magnitud
   menos seguido que un tick (20/seg), la contención de ese lock es
   insignificante en la práctica.
3. **El tick corre en un hilo dedicado por sala, programado con
   `ScheduledExecutorService`, y solo mientras la sala tiene una partida
   en curso** — no hay 6 hilos consumiendo CPU esperando jugadores en
   salas vacías.

¿Por qué no un actor "puro" (una sola cola de comandos también para
unirse/salir, con futuros/callbacks para la respuesta)? Porque eso
resuelve un problema que aquí no existe: unirse/salir son de baja
frecuencia y necesitan respuesta inmediata; el lock de grano fino cumple
la regla de concurrencia del servicio (evitar locks anidados, mismo
orden siempre) sin la complejidad de un mecanismo de correlación
petición/respuesta para algo que no lo necesita. Ver el javadoc de
`SalaRuntime` para el razonamiento completo.

### Por qué el dominio (`Sala`) no tiene ningún `synchronized`

A propósito. `Sala` **asume** que solo su `SalaRuntime` dueño la toca —
el aislamiento de hilos es una garantía que se da *por fuera* (en
`SalaRuntime`), no *por dentro* del agregado. Meter locks dentro de
`Sala` sería redundante y, peor, tentaría a alguien a llamarla desde
cualquier lado "porque ya está protegida" — exactamente lo que el
contrato documentado en su javadoc prohíbe.

### Por qué nunca se expone el agregado `Sala` en vivo

Cualquier respuesta hacia afuera (REST, WebSocket) usa `ResumenSala` o
`EstadoJuegoDTO` — objetos de valor inmutables, construidos **dentro**
del lock de la sala y solo entonces pasados hacia afuera. Devolver la
referencia mutable de `Sala` a un hilo REST sería, literalmente, el tipo
de estado compartido sin protección que la regla de concurrencia 1 de
este servicio prohíbe.

## Buenas prácticas REST y eventos (EDA)

Idénticas a `mapa-descubrimiento-service` — recursos en plural,
sub-recursos para jerarquía, `GET` nunca muta, códigos HTTP estándar,
versionado explícito, sobre `DomainEvent` con metadatos de trazabilidad.
Una diferencia: este servicio, en esta primera pasada, solo **publica**
eventos (`PartidaFinalizada`), no consume ninguno — por eso no hay
todavía una tabla `processed_events` de idempotencia. Se agrega el día
que este servicio necesite reaccionar a un evento de otro microservicio
de Planazo, siguiendo el mismo patrón.

## Lo que este documento deliberadamente no decide todavía

- Revancha en la misma sala, modo espectador y créditos (historias
  Could) — documentadas en el backlog de Minijuego, no implementadas en
  esta pasada.
- Un consumidor de eventos de otros microservicios (p.ej. validar contra
  el servicio de Registro y autenticación en vez de usuarios quemados).
- Rondas cronometradas configurables por sala (historia Should) — hoy
  `SEGUNDOS_LIMITE_PARTIDA` es una constante única para todas las salas.
