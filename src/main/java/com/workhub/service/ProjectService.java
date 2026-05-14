package com.workhub.service;

import com.workhub.dto.request.CreateProjectRequest;
import com.workhub.dto.request.CreateTaskRequest;
import com.workhub.dto.request.UpdateTaskRequest;
import com.workhub.dto.response.ProjectDetailsResponse;
import com.workhub.dto.response.ProjectResponse;
import com.workhub.dto.response.ReportJobResponse;
import com.workhub.dto.response.TaskResponse;
import com.workhub.entity.Job;
import com.workhub.entity.Project;
import com.workhub.entity.Task;
import com.workhub.exception.ResourceNotFoundException;
import com.workhub.messaging.ReportJobMessage;
import com.workhub.messaging.ReportProducer;
import com.workhub.entity.OutboxEvent;
import com.workhub.repository.JobRepository;
import com.workhub.repository.OutboxEventRepository;
import com.workhub.repository.ProjectRepository;
import com.workhub.repository.TaskRepository;
import com.workhub.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final JobRepository jobRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;


    public ProjectResponse createProject(CreateProjectRequest request,
                                         String userId) {
        String tenantId = TenantContext.getTenantId();

        Project project = Project.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .createdBy(userId)
                .build();

        Project saved = projectRepository.save(project);
        return mapToProjectResponse(saved);
    }

    public List<ProjectResponse> getAllProjects() {
        String tenantId = TenantContext.getTenantId();
        return projectRepository.findAllByTenantId(tenantId)
                .stream()
                .map(this::mapToProjectResponse)
                .toList();
    }

    public ProjectDetailsResponse getProjectById(String projectId) {
        String tenantId = TenantContext.getTenantId();
        Project project = projectRepository
                .findByProjectIdAndTenantId(projectId, tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Project not found"));

        List<TaskResponse> tasks = taskRepository
                .findAllByProjectIdAndTenantId(projectId, tenantId)
                .stream()
                .map(this::mapToTaskResponse)
                .toList();

        return mapToProjectDetailsResponse(project, tasks);
    }

    @Transactional
    public TaskResponse createTask(String projectId,
                                   CreateTaskRequest request,
                                   boolean simulateFailure) {
        String tenantId = TenantContext.getTenantId();

        // Verify project belongs to tenant first
        projectRepository.findByProjectIdAndTenantId(projectId, tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Project not found"));

        Task task = Task.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .status(request.getStatus())
                .build();

        Task saved = taskRepository.save(task);

        Job job = Job.builder()
                .tenantId(tenantId)
                .projectId(projectId)
                .status("PENDING")
                .build();
        jobRepository.save(job);

        if (simulateFailure) {
            throw new IllegalStateException("Simulated transactional failure");
        }

        return mapToTaskResponse(saved);
    }

    public TaskResponse updateTask(String taskId,
                                   UpdateTaskRequest request) {
        String tenantId = TenantContext.getTenantId();

        Task task = taskRepository
                .findByTaskIdAndTenantId(taskId, tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Task not found"));

        task.setStatus(request.getStatus());
        Task saved = taskRepository.save(task);
        return mapToTaskResponse(saved);
    }

    // ── Async Report Generation ────────────────────────────────────────────

    /**
     * Step 1 (HTTP thread): creates a PENDING Job in the DB, publishes a
     * RabbitMQ message, and returns immediately with 202 Accepted.
     *
     * @param projectId       target project
     * @param userId          JWT subject (for audit)
     * @param correlationId   end-to-end trace ID
     */
    @Transactional
    public ReportJobResponse initiateReportGeneration(String projectId,
                                                      String userId,
                                                      String correlationId) {
        MDC.put("correlationId", correlationId);
        try {
            String tenantId = TenantContext.getTenantId();

            // Verify project ownership
            projectRepository.findByProjectIdAndTenantId(projectId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

            // Create job record
            Job job = Job.builder()
                    .tenantId(tenantId)
                    .projectId(projectId)
                    .status("PENDING")
                    .reportStatus("PENDING")
                    .correlationId(correlationId)
                    .build();
            Job saved = jobRepository.save(job);

            // Create Outbox Event instead of direct publishing
            ReportJobMessage message = ReportJobMessage.builder()
                    .jobId(saved.getJobId())
                    .projectId(projectId)
                    .tenantId(tenantId)
                    .correlationId(correlationId)
                    .build();

            try {
                String payload = objectMapper.writeValueAsString(message);
                OutboxEvent outboxEvent = OutboxEvent.builder()
                        .aggregateId(saved.getJobId())
                        .aggregateType("JOB")
                        .eventType("REPORT_GENERATION_REQUESTED")
                        .payload(payload)
                        .status("PENDING")
                        .correlationId(correlationId)
                        .build();
                outboxEventRepository.save(outboxEvent);
            } catch (Exception e) {
                log.error("[SERVICE] Failed to serialize report job message", e);
                throw new RuntimeException("Failed to initiate report generation due to serialization error");
            }

            log.info("[SERVICE] Report job initiated and outboxed | jobId={} projectId={} userId={} correlationId={}",
                    saved.getJobId(), projectId, userId, correlationId);

            return mapToReportJobResponse(saved);

        } finally {
            MDC.remove("correlationId");
        }
    }

    /**
     * Polls the latest report job for a given project (within tenant scope).
     */
    public ReportJobResponse getReportStatus(String projectId) {
        String tenantId = TenantContext.getTenantId();

        // Verify project existence first
        projectRepository.findByProjectIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        return jobRepository.findTopByProjectIdAndTenantIdOrderByCreatedAtDesc(projectId, tenantId)
                .map(this::mapToReportJobResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No report job found for project " + projectId));
    }

    // ── Mappers ────────────────────────────────────────────────────────────

    private ProjectResponse mapToProjectResponse(Project project) {
        return ProjectResponse.builder()
                .projectId(project.getProjectId())
                .name(project.getName())
                .tenantId(project.getTenantId())
                .createdBy(project.getCreatedBy())
                .build();
    }

    private ProjectDetailsResponse mapToProjectDetailsResponse(Project project,
                                                               List<TaskResponse> tasks) {
        return ProjectDetailsResponse.builder()
                .projectId(project.getProjectId())
                .name(project.getName())
                .tenantId(project.getTenantId())
                .createdBy(project.getCreatedBy())
                .tasks(tasks)
                .build();
    }

    private TaskResponse mapToTaskResponse(Task task) {
        return TaskResponse.builder()
                .taskId(task.getTaskId())
                .projectId(task.getProjectId())
                .tenantId(task.getTenantId())
                .status(task.getStatus())
                .version(task.getVersion())
                .build();
    }

    private ReportJobResponse mapToReportJobResponse(Job job) {
        return ReportJobResponse.builder()
                .jobId(job.getJobId())
                .projectId(job.getProjectId())
                .tenantId(job.getTenantId())
                .status(job.getStatus())
                .reportStatus(job.getReportStatus())
                .correlationId(job.getCorrelationId())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
