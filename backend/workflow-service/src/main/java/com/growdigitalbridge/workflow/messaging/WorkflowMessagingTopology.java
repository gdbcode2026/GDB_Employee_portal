package com.growdigitalbridge.workflow.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.ExchangeBuilder;
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
 * No queue/listener is declared here: COMMUNICATION.md names Workflow as a consumer of
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
