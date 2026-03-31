package com.workhub.entity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "jobs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String jobId;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String status; // PENDING, COMPLETED, FAILED
}