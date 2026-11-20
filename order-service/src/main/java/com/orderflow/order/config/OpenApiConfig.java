package com.orderflow.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI orderOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Orderflow Order Service")
                .version("v1")
                .description("""
                        Write-side order API. A 202 response means the order was accepted.
                        Payment completion is asynchronous: poll GET /api/orders/{id}
                        until the status is CONFIRMED or CANCELLED.
                        """));
    }
}
