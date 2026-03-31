package com.workhub.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.Set;

@Data
@Builder
public class LoginResponse {
    private String token;
    private String userId;
    private String tenantId;
    private Set<String> roles;
}