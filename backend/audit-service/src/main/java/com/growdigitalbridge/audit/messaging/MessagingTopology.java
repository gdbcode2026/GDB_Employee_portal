package com.growdigitalbridge.audit.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MessagingTopology {

    private static final String QUEUE = "audit.events.v1";
    private static final String DLQ = "audit.events.v1.dlq";

    @Bean TopicExchange domainEventsExchange() { return ExchangeBuilder.topicExchange("gdb.domain.events").durable(true).build(); }

    @Bean Queue auditEventsQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange("gdb.domain.dlx").deadLetterRoutingKey(QUEUE).build();
    }

    @Bean Binding auditBinding(Queue auditEventsQueue, TopicExchange domainEventsExchange) { return BindingBuilder.bind(auditEventsQueue).to(domainEventsExchange).with("#"); }

    @Bean DirectExchange deadLetterExchange() { return ExchangeBuilder.directExchange("gdb.domain.dlx").durable(true).build(); }

    @Bean Queue auditEventsDeadLetterQueue() { return QueueBuilder.durable(DLQ).build(); }

    @Bean Binding auditDeadLetterBinding(Queue auditEventsDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(auditEventsDeadLetterQueue).to(deadLetterExchange).with(QUEUE);
    }

    /** Trusts the platform's own event-envelope package so {@code DomainEvent} round-trips as JSON. */
    @Bean MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.growdigitalbridge.platform.common.event");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
