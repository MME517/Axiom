package com.workhub.repository;

import com.workhub.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {

    // List tasks — scoped by BOTH projectId AND tenantId
    List<Task> findAllByProjectIdAndTenantId(String projectId, String tenantId);

    // Get single task — scoped by tenantId
    Optional<Task> findByTaskIdAndTenantId(String taskId, String tenantId);

    // List ALL tasks for a tenant (for cross-tenant list test)
    List<Task> findAllByTenantId(String tenantId);
}