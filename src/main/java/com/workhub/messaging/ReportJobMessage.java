package com.workhub.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Payload published to the report.generate queue.
 * <p>
 * Serialised as JSON via Jackson MessageConverter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportJobMessage implements Serializable {

    /** DB id of the Job record created by the HTTP handler */
    private String jobId;

    /** Project to generate a report for */
    private String projectId;

    /** Tenant scoping */
    private String tenantId;

    /** Propagated from the X-Correlation-ID HTTP request header */
    private String correlationId;
}
