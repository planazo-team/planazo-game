/**
 * minijuego-service — microservicio de la épica Minijuego de Planazo
 * (Snake multijugador en tiempo real), dueño: Diego Rozo.
 *
 * <p>Arquitectura hexagonal: {@link com.planazo.minijuego.domain} no
 * depende de nada (ni Spring, ni JPA, ni Redis); {@link com.planazo.minijuego.application}
 * orquesta el dominio; {@link com.planazo.minijuego.infrastructure} es todo
 * lo reemplazable. Ver {@code ARQUITECTURA.md} en la raíz del proyecto
 * para el razonamiento completo, y el {@code package-info.java} de cada
 * paquete para las reglas específicas de esa capa.
 */
package com.planazo.minijuego;
