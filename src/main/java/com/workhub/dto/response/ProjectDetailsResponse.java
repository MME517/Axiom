package com.workhub.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProjectDetailsResponse {
    private String projectId;
    private String name;
    private String tenantId;
    private String createdBy;
    private List<TaskResponse> tasks;
}
