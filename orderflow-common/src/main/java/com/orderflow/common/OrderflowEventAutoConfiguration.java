package com.orderflow.common;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class OrderflowEventAutoConfiguration {

    @Bean
    public EventSerde eventSerde() {
        return new EventSerde();
    }
}
