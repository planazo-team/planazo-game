/**
 * Adaptador de Redis para el leaderboard global (SNK-08, RT-3, CC-2). Un
 * Sorted Set (`ZINCRBY`/`ZREVRANGE`) es exactamente la estructura de datos
 * pensada para "incrementar atómicamente el puntaje de alguien y leer el
 * top-N ordenado" — no hace falta ningún lock de aplicación ni
 * transacción: la atomicidad la da Redis mismo.
 */
package com.planazo.minijuego.infrastructure.adapter.out.leaderboard;
