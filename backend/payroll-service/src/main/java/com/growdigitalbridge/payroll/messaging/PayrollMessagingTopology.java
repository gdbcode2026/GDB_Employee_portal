package com.growdigitalbridge.payroll.messaging;

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
 * Declares the shared domain-events exchange so this producer works standalone; declaring the
 * same exchange from multiple services is idempotent in RabbitMQ. Phase 2 (Calculation Core)
 * additionally consumes {@code attendance.finalized.v1} and {@code leave.approved.v1} (Section I,
 * decision 6) - each gets its own durable queue/DLX/DLQ, mirroring the exact shape Workflow
 * Service already uses for {@code employee.deactivated.v1}.
 */
@Configuration
public class PayrollMessagingTopology {

    private static final String EXCHANGE = "gdb.domain.events";
    private static final String DLX = "gdb.domain.dlx";
    private static final String ATTENDANCE_QUEUE = "payroll.attendance-finalized.v1";
    private static final String ATTENDANCE_DLQ = "payroll.attendance-finalized.v1.dlq";
    private static final String LEAVE_QUEUE = "payroll.leave-approved.v1";
    private static final String LEAVE_DLQ = "payroll.leave-approved.v1.dlq";

    @Bean
    TopicExchange domainEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DLX).durable(true).build();
    }

    @Bean
    Queue attendanceFinalizedQueue() {
        return QueueBuilder.durable(ATTENDANCE_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(ATTENDANCE_QUEUE)
                .build();
    }

    @Bean
    Queue attendanceFinalizedDeadLetterQueue() {
        return QueueBuilder.durable(ATTENDANCE_DLQ).build();
    }

    @Bean
    Binding attendanceFinalizedBinding(Queue attendanceFinalizedQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(attendanceFinalizedQueue).to(domainEventsExchange).with("attendance.finalized.v1");
    }

    @Bean
    Binding attendanceFinalizedDeadLetterBinding(Queue attendanceFinalizedDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(attendanceFinalizedDeadLetterQueue).to(deadLetterExchange).with(ATTENDANCE_QUEUE);
    }

    @Bean
    Queue leaveApprovedQueue() {
        return QueueBuilder.durable(LEAVE_QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(LEAVE_QUEUE)
                .build();
    }

    @Bean
    Queue leaveApprovedDeadLetterQueue() {
        return QueueBuilder.durable(LEAVE_DLQ).build();
    }

    @Bean
    Binding leaveApprovedBinding(Queue leaveApprovedQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(leaveApprovedQueue).to(domainEventsExchange).with("leave.approved.v1");
    }

    @Bean
    Binding leaveApprovedDeadLetterBinding(Queue leaveApprovedDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(leaveApprovedDeadLetterQueue).to(deadLetterExchange).with(LEAVE_QUEUE);
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
