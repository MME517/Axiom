package com.workhub.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workhub.entity.OutboxEvent;
import com.workhub.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final ReportProducer reportProducer;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${workhub.outbox.processor-delay:5000}")
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("[OUTBOX] Found {} pending events to process", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            processEvent(event);
        }
    }

    @Transactional
    public void processEvent(OutboxEvent event) {
        MDC.put("correlationId", event.getCorrelationId());
        try {
            log.info("[OUTBOX] Processing event | eventId={} type={}", event.getId(), event.getEventType());

            if ("REPORT_GENERATION_REQUESTED".equals(event.getEventType())) {
                ReportJobMessage message = objectMapper.readValue(event.getPayload(), ReportJobMessage.class);
                reportProducer.publish(message);
            }

            event.setStatus("PROCESSED");
            event.setProcessedAt(Instant.now());
            outboxEventRepository.save(event);

            log.info("[OUTBOX] Event processed successfully | eventId={}", event.getId());
        } catch (Exception e) {
            log.error("[OUTBOX] Failed to process event | eventId={}", event.getId(), e);
            event.setStatus("FAILED");
            outboxEventRepository.save(event);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
