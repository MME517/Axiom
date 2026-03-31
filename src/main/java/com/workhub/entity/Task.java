package com.workhub.entity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tasks")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String taskId;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String status; // TODO, IN_PROGRESS, DONE

    @Version                // for optimistic locking (Youssef will use this)
    private Long version;
}
