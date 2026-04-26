package com.workhub.repository;

import com.workhub.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<Job, String> {
    List<Job> findAllByProjectIdAndTenantId(String projectId, String tenantId);

    /** Returns the most recent job for a project – used for report status polling. */
    Optional<Job> findTopByProjectIdAndTenantIdOrderByCreatedAtDesc(String projectId, String tenantId);
}

