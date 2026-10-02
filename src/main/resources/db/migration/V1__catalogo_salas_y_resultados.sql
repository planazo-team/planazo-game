-- Catálogo de salas pre-existentes de cupo fijo (SNK-01/02/03: las salas
-- no las crea el usuario, existen de antemano). SalaRuntimeRegistry lee
-- esta tabla una sola vez al arrancar para construir el estado en memoria.
CREATE TABLE salas (
    id            VARCHAR(64) PRIMARY KEY,
    codigo        VARCHAR(40) NOT NULL UNIQUE,
    capacidad     INTEGER NOT NULL CHECK (capacidad BETWEEN 2 AND 6)
);

INSERT INTO salas (id, codigo, capacidad) VALUES
    ('sala-001', 'Sala Bogotá',     6),
    ('sala-002', 'Sala Medellín',   6),
    ('sala-003', 'Sala Cali',       6),
    ('sala-004', 'Sala Cartagena',  4);

-- Resultado de cada partida terminada (SNK-08/09/10, CC-2: fuente de
-- verdad de "que mi puntaje quede registrado sin perderse"). Se escribe
-- una sola vez por partida; nunca se actualiza después.
CREATE TABLE resultados_partida (
    id                      VARCHAR(64) PRIMARY KEY,
    sala_id                 VARCHAR(64) NOT NULL,
    sala_codigo             VARCHAR(40) NOT NULL,
    ganador_jugador_id      VARCHAR(64),
    ganador_jugador_nombre  VARCHAR(120),
    motivo_victoria         VARCHAR(40) NOT NULL,
    iniciada_en             TIMESTAMPTZ,
    finalizada_en           TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_resultados_partida_finalizada_en ON resultados_partida (finalizada_en DESC);

CREATE TABLE puntajes_jugador (
    id                      BIGSERIAL PRIMARY KEY,
    resultado_partida_id    VARCHAR(64) NOT NULL REFERENCES resultados_partida (id) ON DELETE CASCADE,
    jugador_id              VARCHAR(64) NOT NULL,
    jugador_nombre          VARCHAR(120) NOT NULL,
    puntaje                 INTEGER NOT NULL,
    posicion_final          INTEGER NOT NULL,
    sobrevivio              BOOLEAN NOT NULL
);

CREATE INDEX idx_puntajes_jugador_jugador_id ON puntajes_jugador (jugador_id);
CREATE INDEX idx_puntajes_jugador_resultado ON puntajes_jugador (resultado_partida_id);
