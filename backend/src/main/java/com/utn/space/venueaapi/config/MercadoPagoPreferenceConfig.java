package com.utn.space.venueaapi.config;

import com.mercadopago.MercadoPagoConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MercadoPagoPreferenceConfig {

    @Value("${mercadopago.access.token}")
    private String accessToken;

    @PostConstruct
    public void init() {
        MercadoPagoConfig.setAccessToken(accessToken);
        // Ayuda diagnóstica: advierte si el token parece de prueba o está vacío
        if (accessToken == null || accessToken.trim().isEmpty()) {
            System.err.println("[MercadoPago] ⚠️ access.token no configurado. Los pagos no funcionarán.");
        } else if (accessToken.startsWith("TEST-")) {
            System.out.println("[MercadoPago] ✅ Usando access.token en modo TEST (sandbox).");
        } else {
            System.out.println("[MercadoPago] ⚠️ access.token NO parece ser de TEST. Verificá que estés usando credenciales de Sandbox si corresponde.");
        }
    }
}
