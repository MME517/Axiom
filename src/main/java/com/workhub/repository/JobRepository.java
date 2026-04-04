package com.workhub.repository;

import com.workhub.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, String> {
    List<Job> findAllByProjectIdAndTenantId(String projectId, String tenantId);
}
