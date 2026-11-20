package com.orderflow.order.config;

import com.orderflow.common.PermanentMessageException;
import com.orderflow.order.domain.validation.CurrencyValidator;
import com.orderflow.order.domain.validation.CustomerValidator;
import com.orderflow.order.domain.validation.LineItemsValidator;
import com.orderflow.order.domain.validation.PaymentValidator;
import com.orderflow.order.domain.validation.ValidationHandler;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class OrderBeans {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Supplier<UUID> orderIds() {
        return UUID::randomUUID;
    }

    @Bean
    ValidationHandler orderValidationChain() {
        ValidationHandler customer = new CustomerValidator();
        customer.linkWith(new CurrencyValidator())
                .linkWith(new LineItemsValidator())
                .linkWith(new PaymentValidator());
        return customer;
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                new org.springframework.kafka.listener.DeadLetterPublishingRecoverer(template),
                new FixedBackOff(200L, 3)
        );
        handler.addNotRetryableExceptions(PermanentMessageException.class);
        return handler;
    }
}
