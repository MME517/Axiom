package com.workhub.controller;

import com.workhub.dto.request.CreateProjectRequest;
import com.workhub.dto.request.CreateTaskRequest;
import com.workhub.dto.request.UpdateTaskRequest;
import com.workhub.dto.response.ProjectDetailsResponse;
import com.workhub.dto.response.ProjectResponse;
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
}
