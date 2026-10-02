package com.planazo.minijuego.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Este servicio, en su primera pasada, solo PUBLICA en el exchange
 * {@code planazo.events} (evento {@code PartidaFinalizada}) — no declara
 * ninguna cola propia ni consume nada todavía, así que no hay
 * dead-letter-exchange que configurar aquí (ver el porqué en
 * {@code adapter.out.messaging.event.package-info}). El exchange es
 * {@code durable} porque lo comparten varios microservicios de Planazo;
 * si este servicio se cae y reinicia, el exchange sigue existiendo.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_EVENTOS = "planazo.events";

    @Bean
    public TopicExchange exchangeEventos() {
        return new TopicExchange(EXCHANGE_EVENTOS, true, false);
    }

    /**
     * Se construye el {@code ObjectMapper} a mano, con
     * {@code JavaTimeModule} registrado explícitamente, en vez de confiar
     * en el constructor vacío de {@code Jackson2JsonMessageConverter}: el
     * evento {@code PartidaFinalizada} lleva campos {@code Instant}
     * ({@code iniciadaEn}/{@code finalizadaEn}) y depender de que la
     * detección automática de módulos de Jackson los encuentre es
     * exactamente el tipo de "funciona en mi máquina" que no queremos — así
     * queda garantizado sin importar la versión de Spring AMQP.
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
