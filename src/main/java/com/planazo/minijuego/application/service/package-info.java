/**
 * Implementaciones de los casos de uso ({@code domain.port.in}). Cada
 * servicio orquesta el dominio y los puertos de salida, pero no contiene
 * reglas de negocio propias — esas viven en {@code domain.model}. Si un
 * método de aquí empieza a tener {@code if}s sobre reglas del juego (no
 * sobre "a quién le pregunto"), esa lógica se mudó al lugar equivocado.
 */
package com.planazo.minijuego.application.service;
