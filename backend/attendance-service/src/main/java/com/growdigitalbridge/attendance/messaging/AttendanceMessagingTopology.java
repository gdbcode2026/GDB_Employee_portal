package com.growdigitalbridge.attendance.messaging;

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
 * Publishes attendance events and consumes {@code LEAVE_APPROVED} per the documented consumer
 * table (docs/architecture/COMMUNICATION.md) - the one event Attendance is a real, non-deferred
 * consumer of. Reuses the exact exchange/DLX names already established by audit-service's topology.
 */
@Configuration
public class AttendanceMessagingTopology {

    private static final String EXCHANGE = "gdb.domain.events";
    private static final String DLX = "gdb.domain.dlx";
    private static final String QUEUE = "attendance.leave-events.v1";
    private static final String DLQ = "attendance.leave-events.v1.dlq";

    @Bean
    TopicExchange domainEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DLX).durable(true).build();
    }

    @Bean
    Queue leaveEventsQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(QUEUE)
                .build();
    }

    @Bean
    Queue leaveEventsDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding leaveApprovedBinding(Queue leaveEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(leaveEventsQueue).to(domainEventsExchange).with("leave.approved.v1");
    }

    @Bean
    Binding leaveEventsDeadLetterBinding(Queue leaveEventsDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(leaveEventsDeadLetterQueue).to(deadLetterExchange).with(QUEUE);
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
