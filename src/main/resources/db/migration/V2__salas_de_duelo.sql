-- Salas pequeñas para que dos o tres personas puedan jugar sin esperar a
-- que se llene una sala de 4 o 6. Las promociones "solo ganadores" de
-- planazo-promo premian el podio de cualquier sala.
INSERT INTO salas (id, codigo, capacidad) VALUES
    ('sala-005', 'Duelo Zona G', 2),
    ('sala-006', 'Duelo Zona T', 2),
    ('sala-007', 'Tierra-616',   3)
ON CONFLICT (id) DO NOTHING;
