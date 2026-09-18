package com.growdigitalbridge.project.messaging;

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
 * docs/architecture/MICROSERVICES.md lists Project's own events as "Deferred project events"
 * (no approved consumer yet), so no queue/listener is declared here - Project only produces.
 * The events below (project.created.v1, project.updated.v1, project.member-added.v1,
 * project.member-removed.v1, task.created.v1, task.assigned.v1, task.status-changed.v1) use
 * the platform's established envelope/exchange/routing-key conventions so they are ready for
 * an approved consumer, and are already captured by Audit Service's catch-all ("#") binding
 * today, fulfilling DATABASE.md's "membership/assignment audited" requirement without
 * inventing new cross-service business logic.
 */
@Configuration
public class ProjectMessagingTopology {

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
