package com.workhub.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Idempotency guard: once a RabbitMQ message is successfully processed,
 * its messageId is stored here.  On redelivery the consumer checks this
 * table first and skips re-processing if the record already exists.
 */
@Entity
@Table(name = "processed_messages",
        uniqueConstraints = @UniqueConstraint(columnNames = "message_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** The AMQP messageId header – must be set by the producer */
    @Column(name = "message_id", nullable = false, unique = true)
    private String messageId;

    @Column(nullable = false)
    private Instant processedAt;

    @PrePersist
    protected void onCreate() {
        processedAt = Instant.now();
    }
}
