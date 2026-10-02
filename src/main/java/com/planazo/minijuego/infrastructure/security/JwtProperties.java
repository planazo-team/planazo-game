package com.planazo.minijuego.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * {@code planazo.seguridad.jwt.secreto} DEBE sobrescribirse fuera del
 * repositorio (variable de entorno {@code JWT_SECRETO}, ver
 * {@code .env.example}) en cualquier ambiente que no sea un laptop de
 * desarrollo — el valor por defecto de {@code application.yml} es
 * intencionalmente obvio para que nadie lo use en producción por
 * descuido.
 */
@Component
@ConfigurationProperties(prefix = "planazo.seguridad.jwt")
public class JwtProperties {

    private String secreto;
    private long expiracionMinutosDevToken = 120;

    public String getSecreto() {
        return secreto;
    }

    public void setSecreto(String secreto) {
        this.secreto = secreto;
    }

    public long getExpiracionMinutosDevToken() {
        return expiracionMinutosDevToken;
    }

    public void setExpiracionMinutosDevToken(long expiracionMinutosDevToken) {
        this.expiracionMinutosDevToken = expiracionMinutosDevToken;
    }
}
