package com.workhub.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Returned by POST /projects/{id}/generate-report (202 Accepted)
 * and GET /projects/{id}/report-status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportJobResponse {

    private String jobId;
    private String projectId;
    private String tenantId;

    /** Overall job status: PENDING | COMPLETED | FAILED */
    private String status;

    /** Report-specific status: PENDING | IN_PROGRESS | DONE | FAILED */
    private String reportStatus;

    /** Propagated X-Correlation-ID for tracing */
    private String correlationId;

    private Instant createdAt;
    private Instant updatedAt;
}
