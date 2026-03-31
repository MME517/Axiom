package com.workhub.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProjectResponse {
    private String projectId;
    private String name;
    private String tenantId;
    private String createdBy;
}