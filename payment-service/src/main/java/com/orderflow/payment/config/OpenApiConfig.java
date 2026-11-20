package com.orderflow.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI paymentOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Orderflow Payment Service")
                .version("v1")
                .description("""
                        Simulated payments. CARD tokens ending in 0002 are declined.
                        WALLET charges above 500.00 (50000 minor units) are declined.
                        """));
    }
}
