package com.workhub.service;

import com.workhub.dto.request.CreateProjectRequest;
import com.workhub.dto.request.CreateTaskRequest;
import com.workhub.dto.request.UpdateTaskRequest;
import com.workhub.dto.response.ProjectDetailsResponse;
import com.workhub.dto.response.ProjectResponse;
import com.workhub.dto.response.TaskResponse;
import com.workhub.entity.Job;
import com.workhub.entity.Project;
import com.workhub.entity.Task;
import com.workhub.exception.ResourceNotFoundException;
import com.workhub.repository.JobRepository;
import com.workhub.repository.ProjectRepository;
import com.workhub.repository.TaskRepository;
import com.workhub.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final JobRepository jobRepository;


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
}
