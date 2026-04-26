package com.workhub.controller;

import com.workhub.dto.request.CreateProjectRequest;
import com.workhub.dto.request.CreateTaskRequest;
import com.workhub.dto.request.UpdateTaskRequest;
import com.workhub.dto.response.ProjectDetailsResponse;
import com.workhub.dto.response.ProjectResponse;
import com.workhub.dto.response.ReportJobResponse;
import com.workhub.dto.response.TaskResponse;
import com.workhub.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping("/projects")
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.createProject(request, userId));
    }

    @GetMapping("/projects")
    public ResponseEntity<List<ProjectResponse>> getAllProjects() {
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    @GetMapping("/projects/{id}")
    public ResponseEntity<ProjectDetailsResponse> getProjectById(
            @PathVariable String id) {
        return ResponseEntity.ok(projectService.getProjectById(id));
    }

    @PostMapping("/projects/{id}/tasks")
    public ResponseEntity<TaskResponse> createTask(
            @PathVariable String id,
            @Valid @RequestBody CreateTaskRequest request,
            @RequestParam(name = "simulateFailure", defaultValue = "false")
            boolean simulateFailure) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.createTask(id, request, simulateFailure));
    }

    @PatchMapping("/tasks/{id}")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable String id,
            @Valid @RequestBody UpdateTaskRequest request) {
        return ResponseEntity.ok(projectService.updateTask(id, request));
    }

    /**
     * Triggers asynchronous report generation for a project.
     * <p>
     * The request is accepted immediately (202 Accepted), a Job record is created
     * with reportStatus=PENDING, and a message is published to the
     * {@code report.generate} RabbitMQ queue.  The caller can poll
     * {@code GET /projects/{id}/report-status} to check completion.
     *
     * @param id              project UUID
     * @param correlationId   optional X-Correlation-ID header; auto-generated if absent
     * @param authentication  current JWT principal
     */
    @PostMapping("/projects/{id}/generate-report")
    public ResponseEntity<ReportJobResponse> generateReport(
            @PathVariable String id,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            Authentication authentication) {

        // Propagate or mint a correlation ID for end-to-end tracing
        String effectiveCorrelationId =
                (correlationId != null && !correlationId.isBlank())
                        ? correlationId
                        : UUID.randomUUID().toString();

        String userId = (String) authentication.getPrincipal();
        ReportJobResponse response =
                projectService.initiateReportGeneration(id, userId, effectiveCorrelationId);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("X-Correlation-ID", effectiveCorrelationId)
                .body(response);
    }

    /**
     * Polls the current report-generation status for a project's latest job.
     */
    @GetMapping("/projects/{id}/report-status")
    public ResponseEntity<ReportJobResponse> getReportStatus(@PathVariable String id) {
        return ResponseEntity.ok(projectService.getReportStatus(id));
    }
}

