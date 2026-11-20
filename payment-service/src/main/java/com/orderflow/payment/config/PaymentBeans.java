package com.orderflow.payment.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentBeans {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
