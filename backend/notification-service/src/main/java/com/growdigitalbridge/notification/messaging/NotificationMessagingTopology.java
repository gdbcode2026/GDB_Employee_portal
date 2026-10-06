package com.growdigitalbridge.notification.messaging;

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
 * Consumes exactly the events inspected and confirmed to carry an unambiguous employee recipient
 * in their current payload (see {@link DomainEventListener}'s Javadoc for the full per-event
 * reasoning, including why {@code workflow.completed.v1} and any document/policy event are
 * deliberately NOT bound here). All six are bound to a single queue since one listener dispatches
 * on event type - mirrors Organization/Asset Service's exact topology shape.
 */
@Configuration
public class NotificationMessagingTopology {

    private static final String EXCHANGE = "gdb.domain.events";
    private static final String DLX = "gdb.domain.dlx";
    private static final String QUEUE = "notification.domain-events.v1";
    private static final String DLQ = "notification.domain-events.v1.dlq";

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
    Binding leaveRequestedBinding(Queue domainEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(domainEventsQueue).to(domainEventsExchange).with("leave.requested.v1");
    }

    @Bean
    Binding leaveApprovedBinding(Queue domainEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(domainEventsQueue).to(domainEventsExchange).with("leave.approved.v1");
    }

    @Bean
    Binding leaveRejectedBinding(Queue domainEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(domainEventsQueue).to(domainEventsExchange).with("leave.rejected.v1");
    }

    @Bean
    Binding expenseSubmittedBinding(Queue domainEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(domainEventsQueue).to(domainEventsExchange).with("expense.submitted.v1");
    }

    @Bean
    Binding expenseApprovedBinding(Queue domainEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(domainEventsQueue).to(domainEventsExchange).with("expense.approved.v1");
    }

    @Bean
    Binding attendanceRegularizationApprovedBinding(Queue domainEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(domainEventsQueue).to(domainEventsExchange).with("attendance.regularization-approved.v1");
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
