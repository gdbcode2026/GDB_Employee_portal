package com.growdigitalbridge.expense.messaging;

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
 * Publishes {@code expense.submitted.v1}/{@code expense.approved.v1} and consumes exactly the
 * one event docs/architecture/COMMUNICATION.md documents Expense as consuming:
 * {@code workflow.completed.v1}. No other event type is bound here - EMPLOYEE_DEACTIVATED is
 * not in Expense's documented consumer list, unlike Document/Asset/Workflow.
 */
@Configuration
public class ExpenseMessagingTopology {

    private static final String EXCHANGE = "gdb.domain.events";
    private static final String DLX = "gdb.domain.dlx";
    private static final String QUEUE = "expense.workflow-events.v1";
    private static final String DLQ = "expense.workflow-events.v1.dlq";

    @Bean
    TopicExchange domainEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DLX).durable(true).build();
    }

    @Bean
    Queue workflowEventsQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(QUEUE)
                .build();
    }

    @Bean
    Queue workflowEventsDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding workflowCompletedBinding(Queue workflowEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(workflowEventsQueue).to(domainEventsExchange).with("workflow.completed.v1");
    }

    @Bean
    Binding workflowEventsDeadLetterBinding(Queue workflowEventsDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(workflowEventsDeadLetterQueue).to(deadLetterExchange).with(QUEUE);
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
