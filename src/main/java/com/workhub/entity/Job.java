package com.workhub.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "jobs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String jobId;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String projectId;

    /** Overall job status: PENDING, COMPLETED, FAILED */
    @Column(nullable = false)
    private String status;

    /** Report generation status: PENDING, IN_PROGRESS, DONE, FAILED */
    @Column(nullable = false)
    @Builder.Default
    private String reportStatus = "PENDING";

    /** Trace ID propagated from the HTTP request via X-Correlation-ID header */
    @Column
    private String correlationId;

    @Column
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}