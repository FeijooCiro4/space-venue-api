package com.utn.space.venueaapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Getter;
import lombok.Setter;

/** Destinos de retorno utilizados al crear preferencias de Mercado Pago. */
@Component
@ConfigurationProperties(prefix = "app.client")
@Getter
@Setter
public class ClientRedirectProperties {
    private String paymentSuccessUrl;
    private String paymentFailureUrl;
    private String paymentPendingUrl;
}
