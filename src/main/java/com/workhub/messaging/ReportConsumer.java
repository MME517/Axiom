package com.workhub.messaging;

import com.workhub.entity.Job;
import com.workhub.entity.ProcessedMessage;
import com.workhub.repository.JobRepository;
import com.workhub.repository.ProcessedMessageRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Background worker that consumes {@link ReportJobMessage} payloads from the
 * {@code report.generate} queue, simulates report generation, and updates the
 * {@link Job} record's {@code reportStatus} in the database.
 *
 * <h3>Idempotency</h3>
 * Before doing any work, the consumer checks whether the AMQP
 * {@code messageId} has already been stored in {@code processed_messages}.
 * If so it acknowledges without re-processing (safe for at-least-once delivery).
 *
 * <h3>Retry / DLQ</h3>
 * Spring AMQP's StatefulRetryOperationsInterceptor retries up to max-attempts
 * (configured in application.yml). After exhaustion the message is NACK-ed
 * without requeue and RabbitMQ routes it to the DLX → DLQ automatically.
 *
 * <h3>Correlation ID</h3>
 * The correlationId carried in the payload is placed in the MDC for the
 * duration of message processing so every log line is traceable.
 */
@Slf4j
@Component
public class ReportConsumer {

    private final JobRepository jobRepository;
    private final ProcessedMessageRepository processedMessageRepository;

    private final Counter processedCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;

    public ReportConsumer(
            JobRepository jobRepository,
            ProcessedMessageRepository processedMessageRepository,
            MeterRegistry meterRegistry) {

        this.jobRepository = jobRepository;
        this.processedMessageRepository = processedMessageRepository;

        this.processedCounter = Counter.builder("workhub.report.messages.processed")
                .description("Report-generation messages processed successfully")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("workhub.report.messages.duplicate")
                .description("Duplicate messages skipped by idempotency guard")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("workhub.report.messages.failed")
                .description("Report-generation messages that failed processing")
                .register(meterRegistry);
    }

    @RabbitListener(queues = "${workhub.rabbitmq.report-queue}")
    @Transactional
    public void handleReportJob(ReportJobMessage payload, Message amqpMessage) {

        String messageId = amqpMessage.getMessageProperties().getMessageId();
        String correlationId = payload.getCorrelationId();

        // ── Correlation-ID MDC injection ───────────────────────────────────
        MDC.put("correlationId", correlationId != null ? correlationId : "n/a");

        try {
            log.info("[CONSUMER] Received report job | messageId={} jobId={} projectId={} tenantId={}",
                    messageId, payload.getJobId(), payload.getProjectId(), payload.getTenantId());

            // ── Idempotency guard ──────────────────────────────────────────
            if (messageId != null && processedMessageRepository.existsByMessageId(messageId)) {
                log.warn("[CONSUMER] Duplicate message detected – skipping | messageId={}", messageId);
                duplicateCounter.increment();
                return;
            }

            // ── Business logic: simulate report generation (2-3 s) ─────────
            Optional<Job> optJob = jobRepository.findById(payload.getJobId());
            if (optJob.isEmpty()) {
                log.error("[CONSUMER] Job not found in DB | jobId={}", payload.getJobId());
                failedCounter.increment();
                throw new IllegalStateException("Job " + payload.getJobId() + " not found");
            }

            Job job = optJob.get();
            job.setReportStatus("IN_PROGRESS");
            jobRepository.save(job);

            // Simulate the actual report-generation work
            simulateReportGeneration();

            job.setReportStatus("DONE");
            job.setStatus("COMPLETED");
            jobRepository.save(job);

            // ── Mark message as processed (idempotency record) ────────────
            if (messageId != null) {
                try {
                    processedMessageRepository.save(
                            ProcessedMessage.builder().messageId(messageId).build());
                } catch (DataIntegrityViolationException ex) {
                    // Race-condition: another instance already stored it – safe to ignore
                    log.warn("[CONSUMER] Concurrent idempotency insert for messageId={}, ignoring", messageId);
                }
            }

            processedCounter.increment();
            log.info("[CONSUMER] Report generated successfully | jobId={} messageId={}",
                    payload.getJobId(), messageId);

        } catch (Exception ex) {
            log.error("[CONSUMER] Processing failed | messageId={} jobId={} error={}",
                    messageId, payload.getJobId(), ex.getMessage(), ex);

            // Update job to FAILED so the API can surface the status
            jobRepository.findById(payload.getJobId()).ifPresent(j -> {
                j.setReportStatus("FAILED");
                j.setStatus("FAILED");
                jobRepository.save(j);
            });

            failedCounter.increment();
            // Re-throw so Spring AMQP retry interceptor can apply the retry strategy
            throw new RuntimeException("Report processing failed for job " + payload.getJobId(), ex);

        } finally {
            MDC.remove("correlationId");
        }
    }

    /**
     * Simulates 2-second report generation work.
     * Replace with real PDF/data-export logic in production.
     */
    private void simulateReportGeneration() {
        try {
            Thread.sleep(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
