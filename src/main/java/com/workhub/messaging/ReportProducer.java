package com.workhub.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publishes {@link ReportJobMessage} payloads to the main report exchange.
 * <p>
 * Each message is stamped with a unique {@code messageId} (AMQP property)
 * so the consumer can enforce exactly-once processing via the
 * {@code processed_messages} table.
 */
@Slf4j
@Component
public class ReportProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${workhub.rabbitmq.report-exchange}")
    private String reportExchange;

    @Value("${workhub.rabbitmq.report-routing-key}")
    private String reportRoutingKey;

    private final Counter publishedCounter;

    public ReportProducer(RabbitTemplate rabbitTemplate, MeterRegistry meterRegistry) {
        this.rabbitTemplate = rabbitTemplate;
        this.publishedCounter = Counter.builder("workhub.report.messages.published")
                .description("Total report-generation messages published to RabbitMQ")
                .register(meterRegistry);
    }

    /**
     * Enqueues a report-generation job.
     *
     * @param message      fully populated job payload (jobId, projectId, tenantId, correlationId)
     */
    public void publish(ReportJobMessage message) {
        String messageId = UUID.randomUUID().toString();

        rabbitTemplate.convertAndSend(
                reportExchange,
                reportRoutingKey,
                message,
                msg -> {
                    MessageProperties props = msg.getMessageProperties();
                    props.setMessageId(messageId);
                    props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    props.setCorrelationId(message.getCorrelationId());
                    return msg;
                });

        publishedCounter.increment();

        log.info("[PRODUCER] Enqueued report job | jobId={} projectId={} tenantId={} " +
                        "messageId={} correlationId={}",
                message.getJobId(), message.getProjectId(), message.getTenantId(),
                messageId, message.getCorrelationId());
    }
}
