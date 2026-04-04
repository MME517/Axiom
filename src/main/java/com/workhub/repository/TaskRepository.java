package com.workhub.repository;

import com.workhub.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {

    // Get all tasks for a project within a tenant
    List<Task> findAllByProjectIdAndTenantId(String projectId, String tenantId);

    // Get a task only if it belongs to the tenant
    Optional<Task> findByTaskIdAndTenantId(String taskId, String tenantId);
}