package com.growdigitalbridge.organization.messaging;

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
 * Consumes EMPLOYEE_CREATED/EMPLOYEE_UPDATED per the documented consumer table
 * (docs/architecture/COMMUNICATION.md); no other event type is bound here. Reuses the exact
 * exchange/DLX names already established by audit-service's topology.
 */
@Configuration
public class OrganizationMessagingTopology {

    private static final String EXCHANGE = "gdb.domain.events";
    private static final String DLX = "gdb.domain.dlx";
    private static final String QUEUE = "organization.employee-events.v1";
    private static final String DLQ = "organization.employee-events.v1.dlq";

    @Bean
    TopicExchange domainEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DLX).durable(true).build();
    }

    @Bean
    Queue employeeEventsQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(QUEUE)
                .build();
    }

    @Bean
    Queue employeeEventsDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding employeeCreatedBinding(Queue employeeEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(employeeEventsQueue).to(domainEventsExchange).with("employee.created.v1");
    }

    @Bean
    Binding employeeUpdatedBinding(Queue employeeEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(employeeEventsQueue).to(domainEventsExchange).with("employee.updated.v1");
    }

    @Bean
    Binding employeeEventsDeadLetterBinding(Queue employeeEventsDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(employeeEventsDeadLetterQueue).to(deadLetterExchange).with(QUEUE);
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
