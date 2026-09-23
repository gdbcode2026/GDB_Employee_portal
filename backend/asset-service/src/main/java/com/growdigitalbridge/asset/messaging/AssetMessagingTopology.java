package com.growdigitalbridge.asset.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes {@code asset.assigned.v1} and consumes exactly the two events
 * docs/architecture/COMMUNICATION.md and MICROSERVICES.md document for Asset:
 * {@code employee.deactivated.v1} ("consumes lifecycle") and {@code workflow.completed.v1}
 * ("consumes workflow completion"). Both are bound to a single queue since one listener
 * dispatches on event type - no other event type is bound here.
 */
@Configuration
public class AssetMessagingTopology {

    private static final String EXCHANGE = "gdb.domain.events";
    private static final String DLX = "gdb.domain.dlx";
    private static final String QUEUE = "asset.domain-events.v1";
    private static final String DLQ = "asset.domain-events.v1.dlq";

    @Bean
    TopicExchange domainEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DLX).durable(true).build();
    }

    @Bean
    Queue domainEventsQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(QUEUE)
                .build();
    }

    @Bean
    Queue domainEventsDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding employeeDeactivatedBinding(Queue domainEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(domainEventsQueue).to(domainEventsExchange).with("employee.deactivated.v1");
    }

    @Bean
    Binding workflowCompletedBinding(Queue domainEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(domainEventsQueue).to(domainEventsExchange).with("workflow.completed.v1");
    }

    @Bean
    Binding domainEventsDeadLetterBinding(Queue domainEventsDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(domainEventsDeadLetterQueue).to(deadLetterExchange).with(QUEUE);
    }

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.growdigitalbridge.platform.common.event");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
