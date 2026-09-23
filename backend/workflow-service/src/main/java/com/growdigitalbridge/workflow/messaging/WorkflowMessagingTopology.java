package com.growdigitalbridge.workflow.messaging;

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
 * Declares the shared domain-events exchange so this producer works standalone; declaring
 * the same exchange from multiple services is idempotent in RabbitMQ.
 *
 * Also consumes {@code employee.deactivated.v1} per docs/architecture/COMMUNICATION.md's event
 * contract table, which names Workflow as a consumer of `EMPLOYEE_DEACTIVATED` - mirroring the
 * exact queue/DLX/DLQ shape already used by Document and Asset Service for the same event.
 *
 * No other queue/listener is declared here: COMMUNICATION.md names Workflow as a consumer of
 * request events only through each owning domain's own boundary description (e.g. "consumes
 * Organization scope and workflow completion" for Leave), but the event table itself lists
 * Workflow as a named consumer of only LEAVE_REQUESTED and EXPENSE_SUBMITTED - and the
 * already-shipped Leave/Expense services do not publish those with Workflow integration in
 * mind, nor call Workflow at all (they own direct in-domain decision endpoints per their own
 * API.md rows). Wiring a consumer here would mean inventing an integration nothing currently
 * produces for. MICROSERVICES.md's "consumes cancellation" for Workflow also names no
 * concrete event in COMMUNICATION.md's table, so no listener is added for it either.
 */
@Configuration
public class WorkflowMessagingTopology {

    private static final String EXCHANGE = "gdb.domain.events";
    private static final String DLX = "gdb.domain.dlx";
    private static final String QUEUE = "workflow.employee-events.v1";
    private static final String DLQ = "workflow.employee-events.v1.dlq";

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
    Binding employeeDeactivatedBinding(Queue employeeEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(employeeEventsQueue).to(domainEventsExchange).with("employee.deactivated.v1");
    }

    @Bean
    Binding employeeEventsDeadLetterBinding(Queue employeeEventsDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(employeeEventsDeadLetterQueue).to(deadLetterExchange).with(QUEUE);
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
