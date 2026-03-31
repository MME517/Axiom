package com.workhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateTaskRequest {

    @NotBlank(message = "Status is required")
    private String status;
}