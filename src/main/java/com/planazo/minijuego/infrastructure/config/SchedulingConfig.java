package com.planazo.minijuego.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las tareas periódicas de infraestructura, como
 * {@code LimpiadorDeSalas}. Los game loops NO usan esto: cada sala tiene su
 * propio hilo en {@code SalaRuntimeRegistry}.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
