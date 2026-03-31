package com.workhub.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskResponse {
    private String taskId;
    private String projectId;
    private String tenantId;
    private String status;
    private Long version;
}