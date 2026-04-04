package com.workhub.repository;

import com.workhub.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {

    // Get all projects for a tenant
    List<Project> findAllByTenantId(String tenantId);

    // Get a project only if it belongs to the tenant
    Optional<Project> findByProjectIdAndTenantId(String projectId, String tenantId);
}