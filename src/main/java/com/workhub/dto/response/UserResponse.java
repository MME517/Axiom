package com.workhub.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.Set;

@Data
@Builder
public class UserResponse {
    private String userId;
    private String email;
    private String tenantId;
    private String tenantName;
    private String tenantPlan;
    private Set<String> roles;
}