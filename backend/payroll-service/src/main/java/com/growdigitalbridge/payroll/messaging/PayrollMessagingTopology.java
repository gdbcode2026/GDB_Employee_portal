package com.growdigitalbridge.payroll.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the shared domain-events exchange so this producer works standalone; declaring the
 * same exchange from multiple services is idempotent in RabbitMQ. No queue is declared here:
 * this Phase 1 foundation registers no {@code @RabbitListener} - consuming {@code
 * attendance.finalized.v1}/{@code leave.approved.v1} is calculation-pipeline work (Phase 2/5 per
 * PAYROLL_REQUIREMENTS.md Section Y), explicitly out of this task's scope.
 */
@Configuration
public class PayrollMessagingTopology {

    @Bean
    TopicExchange domainEventsExchange() {
        return ExchangeBuilder.topicExchange("gdb.domain.events").durable(true).build();
    }

    /** Trusts the platform's own packages so {@code DomainEvent} round-trips as JSON, not a Java-serialized blob. */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.growdigitalbridge.platform.common.event");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
