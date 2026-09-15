package com.growdigitalbridge.audit.messaging;
import org.springframework.amqp.core.*; import org.springframework.context.annotation.Bean; import org.springframework.context.annotation.Configuration;
@Configuration class MessagingTopology {
 @Bean TopicExchange domainEventsExchange() { return ExchangeBuilder.topicExchange("gdb.domain.events").durable(true).build(); }
 @Bean Queue auditEventsQueue() { return QueueBuilder.durable("audit.events.v1").deadLetterExchange("gdb.domain.dlx").build(); }
 @Bean Binding auditBinding(Queue auditEventsQueue, TopicExchange domainEventsExchange) { return BindingBuilder.bind(auditEventsQueue).to(domainEventsExchange).with("#"); }
 @Bean DirectExchange deadLetterExchange() { return ExchangeBuilder.directExchange("gdb.domain.dlx").durable(true).build(); }
}
